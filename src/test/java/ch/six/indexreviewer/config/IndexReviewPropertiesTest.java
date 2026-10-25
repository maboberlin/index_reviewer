package ch.six.indexreviewer.config;

import ch.six.indexreviewer.config.IndexReviewProperties.IndexParameters;
import ch.six.indexreviewer.domain.IndexType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binds the real {@code application.properties}, so a typo in the shipped
 * configuration fails here rather than during a review.
 */
@SpringBootTest
class IndexReviewPropertiesTest {

    @Autowired
    private IndexReviewProperties properties;
    @Autowired
    private IndexParametersRegistry registry;

    @Test
    void bindsTheQ3ReviewParameters() {
        IndexParameters smi = registry.forType(IndexType.SMI_INDEX_REVIEW_Q3_2026);

        assertThat(smi.cutOffDate()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(smi.reviewDate()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(smi.targetSize()).isEqualTo(20);
        assertThat(smi.maxWeight()).isEqualByComparingTo(new BigDecimal("0.18"));
        assertThat(smi.selectionBuffer()).isNotNegative();
    }

    @Test
    void configuresEveryKnownIndexType() {
        assertThat(properties.definitions()).containsOnlyKeys(IndexType.values());
    }

    @Test
    void theCutOffPrecedesTheReviewDate() {
        IndexParameters smi = registry.forType(IndexType.SMI_INDEX_REVIEW_Q3_2026);

        assertThat(smi.cutOffDate()).isBefore(smi.reviewDate());
    }
}
