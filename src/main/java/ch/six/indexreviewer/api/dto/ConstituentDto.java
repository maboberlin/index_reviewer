package ch.six.indexreviewer.api.dto;

import java.math.BigDecimal;

/**
 * A single index constituent and its final weight (as a fraction, e.g. 0.18 = 18%).
 * <p>
 * The weight is a {@link BigDecimal} so the figure published here is exactly
 * the one that was calculated and stored, with no binary rounding in between.
 */
public record ConstituentDto(String id, BigDecimal weight) {
}
