package ch.six.indexreviewer.review;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns market capitalizations into index weights, capped and redistributed.
 * <p>
 * Pure arithmetic with no persistence, so the rulebook example can be asserted
 * against it directly.
 */
@Component
public class WeightCalculator {

    /**
     * Intermediate precision. Every division must be given a context: the
     * two-argument {@link BigDecimal#divide(BigDecimal)} throws
     * {@link ArithmeticException} as soon as a quotient does not terminate,
     * which a third of anything does.
     */
    private static final MathContext MC = MathContext.DECIMAL128;

    /** Scale the published weights are rounded to; matches the stored column. */
    private static final int RESULT_SCALE =
            ch.six.indexreviewer.persistence.entity.SmiIndex.WEIGHT_SCALE;

    /**
     * Safety net only. The capped set grows every pass, so the loop cannot run
     * more times than there are constituents; this stops a pathological input
     * from spinning rather than failing.
     */
    private static final int MAX_ITERATIONS = 1_000;

    /**
     * @param marketCapByCompany free float market capitalization per constituent
     * @param maxWeight          cap as a fraction, e.g. 0.18
     * @return the raw, capped and final weight of every constituent; the final
     *         weights are each at most {@code maxWeight} and sum to exactly 1
     */
    public Map<Long, ConstituentWeights> calculate(Map<Long, BigDecimal> marketCapByCompany,
                                                   BigDecimal maxWeight) {
        if (marketCapByCompany.isEmpty()) {
            return Map.of();
        }
        requirePositiveMarketCaps(marketCapByCompany);
        requireCapIsReachable(marketCapByCompany.size(), maxWeight);

        // Positive everywhere, so the total is necessarily positive too.
        BigDecimal total = marketCapByCompany.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, BigDecimal> raw = new LinkedHashMap<>();
        marketCapByCompany.forEach((companyId, marketCap) -> raw.put(companyId, marketCap.divide(total, MC)));

        Map<Long, BigDecimal> capped = new LinkedHashMap<>();
        raw.forEach((companyId, weight) -> capped.put(companyId, weight.min(maxWeight)));

        Map<Long, BigDecimal> result = redistribute(raw, maxWeight);
        roundAndAbsorbResidual(result, maxWeight);

        Map<Long, ConstituentWeights> weights = new LinkedHashMap<>();
        raw.forEach((companyId, rawWeight) -> weights.put(companyId,
                new ConstituentWeights(rawWeight, capped.get(companyId), result.get(companyId))));
        return weights;
    }

    /**
     * Caps the constituents over the limit and shares what was taken off them
     * among those still below it, in proportion to their current weight.
     * <p>
     * One pass is not enough: taking 22% off a 40% constituent and spreading it
     * over the rest can lift the next largest above the cap in turn. Repeating
     * terminates because a constituent pushed to the cap stays there, so the
     * capped set only ever grows.
     */
    private Map<Long, BigDecimal> redistribute(Map<Long, BigDecimal> raw, BigDecimal maxWeight) {
        Map<Long, BigDecimal> weights = new LinkedHashMap<>(raw);

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            boolean anyOverCap = weights.values().stream().anyMatch(w -> w.compareTo(maxWeight) > 0);
            if (!anyOverCap) {
                return weights;
            }
            weights.replaceAll((companyId, weight) -> weight.min(maxWeight));

            BigDecimal allocated = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal excess = BigDecimal.ONE.subtract(allocated);
            if (excess.signum() == 0) {
                // Capping landed on a full allocation: everything is at or below
                // the cap and the weights already sum to 1. Nothing to spread.
                return weights;
            }
            if (excess.signum() < 0) {
                throw new IllegalStateException(
                        "Allocated weight exceeded 1 after capping: " + allocated.toPlainString());
            }

            BigDecimal totalBelowCap = weights.values().stream()
                    .filter(weight -> weight.compareTo(maxWeight) < 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalBelowCap.signum() == 0) {
                throw new IllegalStateException(
                        "No constituent below the cap of " + maxWeight.toPlainString()
                                + " to absorb a remaining weight of " + excess.toPlainString());
            }

            BigDecimal share = excess.divide(totalBelowCap, MC);
            weights.replaceAll((companyId, weight) -> weight.compareTo(maxWeight) < 0
                    ? weight.add(weight.multiply(share, MC), MC)
                    : weight);
        }
        throw new IllegalStateException("Weight capping did not converge within " + MAX_ITERATIONS + " iterations");
    }

    /**
     * Rounds to the published scale and puts whatever rounding shaved off onto
     * one constituent, so the weights add up to exactly 1 rather than to
     * 0.999999999999.
     * <p>
     * The residual goes to the heaviest constituent that can still take it
     * <em>without passing the cap</em>. Filtering for headroom before picking
     * the heaviest is the point: picking the heaviest first would land on one
     * already sitting on the cap and push it a hair over - breaking, by a
     * rounding unit, the very limit the capping exists to enforce.
     * <p>
     * On return both published invariants hold: the weights sum to exactly 1,
     * and none exceeds the cap. Every operation here is at {@code RESULT_SCALE}
     * and therefore exact, so the sum is precisely 1 rather than nearly so.
     */
    private void roundAndAbsorbResidual(Map<Long, BigDecimal> weights, BigDecimal maxWeight) {
        weights.replaceAll((companyId, weight) -> weight.setScale(RESULT_SCALE, RoundingMode.HALF_UP));

        BigDecimal sum = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        // subtract already yields the larger of the two scales, i.e. RESULT_SCALE.
        BigDecimal residual = BigDecimal.ONE.subtract(sum);
        if (residual.signum() == 0) {
            return;
        }
        // DOWN, never HALF_UP: rounding a cap away from zero would permit a
        // weight fractionally above the limit that was actually configured.
        BigDecimal cap = maxWeight.setScale(RESULT_SCALE, RoundingMode.DOWN);

        Long target = weights.entrySet().stream()
                .filter(entry -> entry.getValue().add(residual).compareTo(cap) <= 0)
                .max(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                .map(Map.Entry::getKey)
                // Unreachable: no candidate needs every weight to sit within a
                // rounding unit of the cap, which would put the total at roughly
                // n x cap - and requireCapIsReachable has already established
                // that n x cap is at least 1, contradicting the shortfall that
                // produced a positive residual in the first place.
                .orElseThrow(() -> new IllegalStateException(
                        "No constituent can absorb a rounding residual of " + residual.toPlainString()
                                + " without exceeding the cap of " + cap.toPlainString()));

        weights.put(target, weights.get(target).add(residual));
    }

    /**
     * Every constituent must carry a positive market capitalization.
     * <p>
     * Redistribution is proportional, so a constituent of weight zero would
     * never receive any share of the excess. Once the others reached the cap
     * there would be nowhere left to place what remains, and the published
     * weights would quietly stop summing to 1 instead of failing. Rejecting it
     * at the door keeps that from becoming a silent arithmetic edge case.
     */
    private void requirePositiveMarketCaps(Map<Long, BigDecimal> marketCapByCompany) {
        List<Long> invalid = marketCapByCompany.entrySet().stream()
                .filter(entry -> entry.getValue() == null || entry.getValue().signum() <= 0)
                .map(Map.Entry::getKey)
                .toList();
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException(
                    "Market capitalization must be positive for every constituent but was not for " + invalid);
        }
    }

    /**
     * {@code size × cap} below 1 would leave weight that cannot be placed
     * anywhere, and the redistribution could never balance.
     */
    private void requireCapIsReachable(int constituentCount, BigDecimal maxWeight) {
        BigDecimal capacity = maxWeight.multiply(BigDecimal.valueOf(constituentCount));
        if (capacity.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException(
                    "A cap of " + maxWeight + " over " + constituentCount
                            + " constituents cannot reach a total weight of 1");
        }
    }
}
