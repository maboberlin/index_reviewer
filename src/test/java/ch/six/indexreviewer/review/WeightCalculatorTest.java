package ch.six.indexreviewer.review;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class WeightCalculatorTest {

    private final WeightCalculator calculator = new WeightCalculator();

    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal CAP_18 = new BigDecimal("0.18");

    /**
     * The worked example from the methodology rulebook: A/B/C with market
     * capitalizations 600/300/100 and a 50% cap must give 50% / 37.5% / 12.5%.
     * <p>
     * This is the test that distinguishes redistributing the <em>excess</em>
     * from redistributing the whole weight - the latter would yield 50/75/25
     * and total 150%.
     */
    @Test
    void reproducesTheRulebookExample() {
        Map<Long, BigDecimal> ffmcap = ffmcap(1L, "600", 2L, "300", 3L, "100");

        Map<Long, ConstituentWeights> weights = calculator.calculate(ffmcap, HALF);

        assertThat(weights.get(1L).raw()).isEqualByComparingTo("0.60");
        assertThat(weights.get(2L).raw()).isEqualByComparingTo("0.30");
        assertThat(weights.get(3L).raw()).isEqualByComparingTo("0.10");

        assertThat(weights.get(1L).capped()).isEqualByComparingTo("0.50");
        assertThat(weights.get(2L).capped()).isEqualByComparingTo("0.30");
        assertThat(weights.get(3L).capped()).isEqualByComparingTo("0.10");

        assertThat(weights.get(1L).result()).isEqualByComparingTo("0.50");
        assertThat(weights.get(2L).result()).isEqualByComparingTo("0.375");
        assertThat(weights.get(3L).result()).isEqualByComparingTo("0.125");

        assertThat(sumOfResults(weights)).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void leavesWeightsAloneWhenNoneExceedTheCap() {
        Map<Long, BigDecimal> ffmcap = ffmcap(1L, "500", 2L, "300", 3L, "200");

        Map<Long, ConstituentWeights> weights = calculator.calculate(ffmcap, HALF);

        // Nothing was capped, so the published weight is the raw one.
        assertThat(weights.get(1L).result()).isEqualByComparingTo("0.50");
        assertThat(weights.get(2L).result()).isEqualByComparingTo("0.30");
        assertThat(weights.get(3L).result()).isEqualByComparingTo("0.20");
        assertThat(sumOfResults(weights)).isEqualByComparingTo(BigDecimal.ONE);
    }

    /**
     * With a non-terminating share the published weight is the raw one rounded
     * to the stored scale, give or take the residual that keeps the total at 1.
     */
    @Test
    void publishesTheRawWeightRoundedWhenNothingIsCapped() {
        Map<Long, BigDecimal> ffmcap = ffmcap(1L, "100", 2L, "100", 3L, "100");

        Map<Long, ConstituentWeights> weights = calculator.calculate(ffmcap, HALF);

        BigDecimal tolerance = new BigDecimal("0.000000000001");
        assertThat(weights.values()).allSatisfy(weight ->
                assertThat(weight.result()).isCloseTo(weight.raw(), within(tolerance)));
        assertThat(sumOfResults(weights)).isEqualByComparingTo(BigDecimal.ONE);
    }

    /**
     * One pass is not enough here: capping the 70% constituent frees 52%, and
     * spreading that over the rest lifts the 20% constituent well past 18%.
     * A single-pass implementation leaves a constituent above the cap.
     */
    @Test
    void keepsRedistributingUntilNobodyExceedsTheCap() {
        Map<Long, BigDecimal> ffmcap = ffmcap(
                1L, "700", 2L, "200", 3L, "40", 4L, "30", 5L, "20", 6L, "10");

        Map<Long, ConstituentWeights> weights = calculator.calculate(ffmcap, CAP_18);

        assertThat(weights.values())
                .allSatisfy(weight -> assertThat(weight.result()).isLessThanOrEqualTo(CAP_18));
        assertThat(sumOfResults(weights)).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void producesWeightsSummingToExactlyOneDespiteRounding() {
        // Three equal constituents force a non-terminating 1/3 and so exercise
        // both the MathContext on the division and the residual adjustment.
        Map<Long, BigDecimal> ffmcap = ffmcap(1L, "1", 2L, "1", 3L, "1");

        Map<Long, ConstituentWeights> weights = calculator.calculate(ffmcap, HALF);

        assertThat(sumOfResults(weights)).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void spreadsEvenlyWhenEveryConstituentIsAtTheCap() {
        // Five constituents at a 20% cap: the only solution is 20% each.
        Map<Long, BigDecimal> ffmcap = ffmcap(1L, "1000", 2L, "1", 3L, "1", 4L, "1", 5L, "1");

        Map<Long, ConstituentWeights> weights = calculator.calculate(ffmcap, new BigDecimal("0.20"));

        assertThat(weights.values())
                .allSatisfy(weight -> assertThat(weight.result()).isEqualByComparingTo("0.20"));
    }

    /**
     * The residual that makes the weights total exactly 1 must not be dumped on
     * a constituent already sitting on the cap, or the published weight ends up
     * a rounding unit above the limit.
     */
    @Test
    void neverPushesAConstituentOverTheCapWhileFixingUpTheTotal() {
        // Two dominant names are both pinned to the cap and the remainder does
        // not divide evenly, so a residual has to be placed somewhere.
        Map<Long, BigDecimal> ffmcap = ffmcap(
                1L, "5000", 2L, "5000", 3L, "1", 4L, "1", 5L, "1",
                6L, "1", 7L, "1", 8L, "1", 9L, "1");

        Map<Long, ConstituentWeights> weights = calculator.calculate(ffmcap, CAP_18);

        assertThat(weights.values())
                .allSatisfy(weight -> assertThat(weight.result()).isLessThanOrEqualTo(CAP_18));
        assertThat(sumOfResults(weights)).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void rejectsACapTooSmallToReachFullWeight() {
        // Three constituents can never fill 100% under a 10% cap.
        Map<Long, BigDecimal> ffmcap = ffmcap(1L, "100", 2L, "100", 3L, "100");

        assertThatThrownBy(() -> calculator.calculate(ffmcap, new BigDecimal("0.10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot reach a total weight of 1");
    }

    /**
     * Redistribution is proportional, so a zero-weight constituent would never
     * take a share of the excess and the weights could stop summing to 1
     * without anything complaining. It has to be refused rather than tolerated.
     */
    @Test
    void rejectsAConstituentWithoutMarketCapitalisation() {
        Map<Long, BigDecimal> ffmcap = ffmcap(1L, "100", 2L, "0", 3L, "100");

        assertThatThrownBy(() -> calculator.calculate(ffmcap, HALF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive for every constituent")
                // Names the offender, so a bad input is traceable to a company.
                .hasMessageContaining("2");
    }

    @Test
    void rejectsANegativeMarketCapitalisation() {
        Map<Long, BigDecimal> ffmcap = ffmcap(1L, "100", 2L, "-5", 3L, "100");

        assertThatThrownBy(() -> calculator.calculate(ffmcap, HALF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive for every constituent");
    }

    @Test
    void returnsNothingForAnEmptyIndex() {
        assertThat(calculator.calculate(Map.of(), CAP_18)).isEmpty();
    }

    private static BigDecimal sumOfResults(Map<Long, ConstituentWeights> weights) {
        return weights.values().stream()
                .map(ConstituentWeights::result)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Map<Long, BigDecimal> ffmcap(Object... idsAndValues) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        for (int i = 0; i < idsAndValues.length; i += 2) {
            map.put((Long) idsAndValues[i], new BigDecimal((String) idsAndValues[i + 1]));
        }
        return map;
    }
}
