package ch.six.indexreviewer.config;

import ch.six.indexreviewer.domain.IndexType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * The parameters that define a review, one entry per index.
 * <p>
 * These belong to the <em>definition</em> of an index review - fixed, published
 * in advance, not editable while the application runs - which is why they live
 * in configuration rather than in the database. The database holds the review
 * <em>state</em> instead: {@code IndexDefinition.status}, {@code lastProcessed}
 * and the computed results.
 * <p>
 * Everything is validated while the context starts, so a missing or nonsensical
 * value stops the application with a message naming the property instead of
 * surfacing as a failure in the middle of a review.
 *
 * @param definitions parameters per index; {@link IndexParametersRegistry}
 *                    additionally checks that no index is missing
 */
@ConfigurationProperties("index-review")
@Validated
public record IndexReviewProperties(

        // The @Valid belongs inside the generic parameter: as a container element
        // annotation it cascades into the map values. On the field it would not,
        // and the constraints below would never run.
        Map<IndexType, @Valid IndexParameters> definitions) {

    /**
     * @param cutOffDate      date whose prices are used for the weighting
     * @param reviewDate      date whose shares, free float and universe are used
     * @param targetSize      number of constituents the index is built from
     * @param maxWeight       cap no constituent may exceed, as a fraction (0.18 = 18%)
     * @param selectionBuffer rank tolerance applied around {@code targetSize},
     *                        see methodology rulebook 5.12.3.2
     */
    public record IndexParameters(

            @NotNull LocalDate cutOffDate,

            @NotNull LocalDate reviewDate,

            @Positive int targetSize,

            @NotNull
            @DecimalMin(value = "0", inclusive = false)
            @DecimalMax("1.0")
            // No finer than the scale the weights are stored at: a cap the
            // published figures cannot express would be silently honoured at a
            // rounded value instead of the one configured.
            @Digits(integer = 1, fraction = 12)
            BigDecimal maxWeight,

            @PositiveOrZero int selectionBuffer) {
    }
}
