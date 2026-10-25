package ch.six.indexreviewer.review;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConstituentSelectorTest {

    private final ConstituentSelector selector = new ConstituentSelector();

    /** Companies 1..n with descending market capitalization, so rank == id. */
    private static Map<Long, BigDecimal> descendingUniverse(int size) {
        Map<Long, BigDecimal> ffmcap = new LinkedHashMap<>();
        for (int i = 1; i <= size; i++) {
            ffmcap.put((long) i, BigDecimal.valueOf(1000L - i));
        }
        return ffmcap;
    }

    @Test
    void ranksByMarketCapitalizationDescending() {
        Map<Long, BigDecimal> ffmcap = new LinkedHashMap<>();
        ffmcap.put(7L, new BigDecimal("10"));
        ffmcap.put(8L, new BigDecimal("30"));
        ffmcap.put(9L, new BigDecimal("20"));

        List<RankedCompany> ranked = selector.rank(ffmcap);

        assertThat(ranked).extracting(RankedCompany::companyId).containsExactly(8L, 9L, 7L);
        assertThat(ranked).extracting(RankedCompany::rank).containsExactly(1, 2, 3);
    }

    /**
     * Without a tie-break two companies on the same capitalization could swap
     * between runs and silently change the published index.
     */
    @Test
    void breaksTiesByCompanyIdSoRerunsAreIdentical() {
        Map<Long, BigDecimal> ffmcap = new LinkedHashMap<>();
        ffmcap.put(9L, new BigDecimal("50"));
        ffmcap.put(3L, new BigDecimal("50"));
        ffmcap.put(6L, new BigDecimal("50"));

        assertThat(selector.rank(ffmcap)).extracting(RankedCompany::companyId)
                .containsExactly(3L, 6L, 9L);
    }

    @Test
    void takesTheTopRanksOutrightWhenNoIncumbentIsInTheBufferZone() {
        List<RankedCompany> ranked = selector.rank(descendingUniverse(30));

        // Buffer zone is ranks 19-22; no incumbents there, so 19 and 20 win on rank.
        List<Long> selected = selector.select(ranked, Set.of(), 20, 2);

        assertThat(selected).hasSize(20);
        assertThat(selected).containsExactlyElementsOf(idsOneTo(20));
    }

    @Test
    void prefersIncumbentsInsideTheBufferZone() {
        List<RankedCompany> ranked = selector.rank(descendingUniverse(30));

        // 21 and 22 are already in the index; 19 and 20 are not.
        List<Long> selected = selector.select(ranked, Set.of(21L, 22L), 20, 2);

        assertThat(selected).hasSize(20);
        assertThat(selected).containsAll(idsOneTo(18));
        assertThat(selected).contains(21L, 22L);
        assertThat(selected).doesNotContain(19L, 20L);
    }

    @Test
    void fillsRemainingPlacesByRankWhenOnlyOneIncumbentCompetes() {
        List<RankedCompany> ranked = selector.rank(descendingUniverse(30));

        List<Long> selected = selector.select(ranked, Set.of(22L), 20, 2);

        assertThat(selected).hasSize(20);
        // 22 takes one place as incumbent, 19 the other as the best of the rest.
        assertThat(selected).contains(22L, 19L);
        assertThat(selected).doesNotContain(20L, 21L);
    }

    @Test
    void keepsTheHighestRankedIncumbentsWhenMoreCompeteThanThereArePlaces() {
        List<RankedCompany> ranked = selector.rank(descendingUniverse(30));

        // All four contenders are incumbents but only two places are open.
        List<Long> selected = selector.select(ranked, Set.of(19L, 20L, 21L, 22L), 20, 2);

        assertThat(selected).hasSize(20);
        assertThat(selected).contains(19L, 20L);
        assertThat(selected).doesNotContain(21L, 22L);
    }

    @Test
    void returnsConstituentsInRankOrder() {
        List<RankedCompany> ranked = selector.rank(descendingUniverse(30));

        List<Long> selected = selector.select(ranked, Set.of(21L, 22L), 20, 2);

        assertThat(selected).isSorted();
    }

    @Test
    void selectsPurelyByRankWhenTheBufferIsZero() {
        List<RankedCompany> ranked = selector.rank(descendingUniverse(30));

        assertThat(selector.select(ranked, Set.of(25L), 20, 0))
                .containsExactlyElementsOf(idsOneTo(20));
    }

    @Test
    void copesWithFewerCandidatesThanTheBufferZoneAllowsFor() {
        List<RankedCompany> ranked = selector.rank(descendingUniverse(21));

        List<Long> selected = selector.select(ranked, Set.of(21L), 20, 2);

        assertThat(selected).hasSize(20);
        assertThat(selected).contains(21L);
    }

    @Test
    void refusesToBuildAnIndexFromTooFewEligibleCompanies() {
        List<RankedCompany> ranked = selector.rank(descendingUniverse(19));

        assertThatThrownBy(() -> selector.select(ranked, Set.of(), 20, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only 19 eligible companies");
    }

    private static List<Long> idsOneTo(int last) {
        return java.util.stream.LongStream.rangeClosed(1, last).boxed().toList();
    }
}
