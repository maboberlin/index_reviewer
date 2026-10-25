package ch.six.indexreviewer.review;

import java.math.BigDecimal;

/**
 * The three weighting stages kept for one constituent, so the capping remains
 * auditable rather than only its outcome being visible.
 *
 * @param raw    weight purely by market capitalization share
 * @param capped {@code raw} after a single application of the cap
 * @param result weight after the excess has been redistributed to exhaustion
 */
public record ConstituentWeights(BigDecimal raw, BigDecimal capped, BigDecimal result) {
}
