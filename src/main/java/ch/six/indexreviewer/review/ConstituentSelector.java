package ch.six.indexreviewer.review;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ranks the eligible companies and picks the index constituents, applying the
 * selection buffer from methodology rulebook 5.12.3.2.
 * <p>
 * Pure logic with no persistence so the buffer rules can be tested directly.
 */
@Component
public class ConstituentSelector {

    private static final Logger log = LoggerFactory.getLogger(ConstituentSelector.class);

    /**
     * Ranks by market capitalization, highest first.
     * <p>
     * Ties are broken by company id so that re-running a review over unchanged
     * data yields the same index - without it, two companies on an identical
     * capitalization could swap places between runs and silently change the
     * published composition.
     */
    public List<RankedCompany> rank(Map<Long, BigDecimal> ffmcapByCompany) {
        List<Map.Entry<Long, BigDecimal>> ordered = new ArrayList<>(ffmcapByCompany.entrySet());
        ordered.sort(Map.Entry.<Long, BigDecimal>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()));

        List<RankedCompany> ranked = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            ranked.add(new RankedCompany(i + 1, ordered.get(i).getKey(), ordered.get(i).getValue()));
        }
        return ranked;
    }

    /**
     * Picks {@code targetSize} constituents from the ranking.
     * <p>
     * Companies ranked above the buffer zone are in regardless. The buffer zone
     * - the {@code 2 × buffer} places straddling {@code targetSize} - competes
     * for the places that are left, and a company already in the index wins
     * them ahead of a newcomer on a similar capitalization. That is what stops
     * the composition churning over small rank movements between reviews.
     *
     * @param ranked             ranking produced by {@link #rank(Map)}
     * @param currentComposition companies in the index before this review
     * @param targetSize         number of constituents wanted
     * @param buffer             places either side of {@code targetSize} that compete
     * @return the selected company ids, in rank order
     */
    public List<Long> select(List<RankedCompany> ranked,
                             Set<Long> currentComposition,
                             int targetSize,
                             int buffer) {
        if (ranked.size() < targetSize) {
            throw new IllegalArgumentException(
                    "Only " + ranked.size() + " eligible companies for an index of " + targetSize);
        }

        int guaranteed = Math.max(0, targetSize - buffer);
        int candidateLimit = Math.min(ranked.size(), targetSize + buffer);

        List<Long> selected = new ArrayList<>(targetSize);
        ranked.subList(0, guaranteed).forEach(company -> selected.add(company.companyId()));

        List<RankedCompany> contenders = ranked.subList(guaranteed, candidateLimit);
        int openPlaces = targetSize - guaranteed;

        // Incumbents first, in rank order, then the best of the rest.
        for (RankedCompany contender : contenders) {
            if (selected.size() == targetSize) {
                break;
            }
            if (currentComposition.contains(contender.companyId())) {
                selected.add(contender.companyId());
            }
        }
        for (RankedCompany contender : contenders) {
            if (selected.size() == targetSize) {
                break;
            }
            if (!selected.contains(contender.companyId())) {
                selected.add(contender.companyId());
            }
        }

        log.info("Selected {} constituents: ranks 1-{} outright, {} place(s) decided among ranks {}-{}",
                selected.size(), guaranteed, openPlaces, guaranteed + 1, candidateLimit);

        // Restore rank order; the incumbent-first passes above ignore it.
        List<Long> inRankOrder = ranked.stream()
                .map(RankedCompany::companyId)
                .filter(selected::contains)
                .toList();
        return inRankOrder;
    }
}
