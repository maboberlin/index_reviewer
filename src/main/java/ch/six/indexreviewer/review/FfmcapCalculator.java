package ch.six.indexreviewer.review;

import ch.six.indexreviewer.persistence.entity.SecData;
import ch.six.indexreviewer.persistence.entity.SpiUniverse;
import ch.six.indexreviewer.service.SecDataService;
import ch.six.indexreviewer.service.SpiUniverseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Computes the free float market capitalization of every eligible company.
 * <p>
 * {@code FFMCAP = Price(cut-off) × Shares(review) × FreeFloat(review)} - the
 * price is taken on the cut-off date, everything else on the review date.
 */
@Component
public class FfmcapCalculator {

    private static final Logger log = LoggerFactory.getLogger(FfmcapCalculator.class);

    private static final MathContext MC = MathContext.DECIMAL128;

    private final SpiUniverseService spiUniverseService;
    private final SecDataService secDataService;

    public FfmcapCalculator(SpiUniverseService spiUniverseService, SecDataService secDataService) {
        this.spiUniverseService = spiUniverseService;
        this.secDataService = secDataService;
    }

    /**
     * @return market capitalization per eligible company, and the companies
     *         that had to be left out for want of data
     */
    public EligibilityResult calculate(LocalDate cutOffDate, LocalDate reviewDate) {

        List<SecData> byCutOffDate = secDataService.findByDate(cutOffDate);
        List<SecData> byReviewDate = secDataService.findByDate(reviewDate);
        Map<Long, SecData> pricing = byCompany(byCutOffDate);
        Map<Long, SecData> fundamentals = byCompany(byReviewDate);
        List<SpiUniverse> universe = spiUniverseService.findByDate(reviewDate);

        if (universe.isEmpty()) {
            throw new IllegalStateException(
                    "No SPI universe data for review date " + reviewDate + "; import spi_universe.csv first");
        }

        Map<Long, BigDecimal> eligible = new LinkedHashMap<>();
        List<Long> ineligible = new ArrayList<>();

        for (SpiUniverse member : universe) {
            Long companyId = member.getCompanyId();
            SecData price = pricing.get(companyId);
            SecData fundamental = fundamentals.get(companyId);

            // A company whose data is incomplete is simply not eligible. The
            // supplied files do contain such a case, so treating it as a hard
            // error would make every review fail.
            if (price == null || price.getPrice() == null) {
                log.warn("Company {} has no price on the cut-off date {}; excluded from the review",
                        companyId, cutOffDate);
                ineligible.add(companyId);
                continue;
            }
            if (fundamental == null) {
                log.warn("Company {} has no security data on the review date {}; excluded from the review",
                        companyId, reviewDate);
                ineligible.add(companyId);
                continue;
            }

            // actual calculation
            BigDecimal ffmcap = price.getPrice()
                    .multiply(BigDecimal.valueOf(fundamental.getShares()), MC)
                    .multiply(fundamental.getFreeFloat(), MC);

            eligible.put(companyId, ffmcap);
        }

        log.info("{} of {} universe companies are eligible for the review ({} excluded for missing data)",
                eligible.size(), universe.size(), ineligible.size());
        return new EligibilityResult(eligible, List.copyOf(ineligible));
    }

    private static Map<Long, SecData> byCompany(List<SecData> rows) {
        return rows.stream().collect(Collectors.toMap(
                SecData::getCompanyId,
                Function.identity(),
                // The import rejects duplicate (company, date) pairs, so this
                // cannot normally fire; keeping the first keeps it deterministic.
                (first, duplicate) -> first));
    }

    /**
     * @param ffmcapByCompany  market capitalization of every eligible company
     * @param ineligible       companies left out because data was missing
     */
    public record EligibilityResult(Map<Long, BigDecimal> ffmcapByCompany, List<Long> ineligible) {
    }
}
