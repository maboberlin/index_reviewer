package ch.six.indexreviewer.config;

import ch.six.indexreviewer.config.IndexReviewProperties.IndexParameters;
import ch.six.indexreviewer.domain.IndexType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexParametersRegistryTest {

    private static final IndexParameters SMI = new IndexParameters(
            LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 21), 20, new BigDecimal("0.18"), 2);

    @Test
    void returnsTheParametersOfAConfiguredIndex() {
        IndexParametersRegistry registry = new IndexParametersRegistry(
                new IndexReviewProperties(Map.of(IndexType.SMI_INDEX_REVIEW_Q3_2026, SMI)));

        assertThat(registry.forType(IndexType.SMI_INDEX_REVIEW_Q3_2026)).isEqualTo(SMI);
    }

    @Test
    void refusesToStartWhenAnIndexTypeHasNoParameters() {
        assertThatThrownBy(() -> new IndexParametersRegistry(new IndexReviewProperties(Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMI_INDEX_REVIEW_Q3_2026");
    }

    @Test
    void treatsAbsentConfigurationAsNoneRatherThanFailingWithNull() {
        assertThatThrownBy(() -> new IndexParametersRegistry(new IndexReviewProperties(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMI_INDEX_REVIEW_Q3_2026");
    }
}
