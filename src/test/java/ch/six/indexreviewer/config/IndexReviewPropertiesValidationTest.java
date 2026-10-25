package ch.six.indexreviewer.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts that bad configuration stops the application rather than being
 * discovered halfway through a review.
 */
class IndexReviewPropertiesValidationTest {

    private static final String PREFIX = "index-review.definitions.SMI_INDEX_REVIEW_Q3_2026.";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EnableProperties.class);

    /** Positive control: without it a failing assertion below could pass for the wrong reason. */
    @Test
    void acceptsACompleteConfiguration() {
        runner.withPropertyValues(
                        PREFIX + "cut-off-date=2026-09-10",
                        PREFIX + "review-date=2026-09-21",
                        PREFIX + "target-size=20",
                        PREFIX + "max-weight=0.18",
                        PREFIX + "selection-buffer=2")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void rejectsACapAboveOneHundredPercent() {
        runner.withPropertyValues(
                        PREFIX + "cut-off-date=2026-09-10",
                        PREFIX + "review-date=2026-09-21",
                        PREFIX + "target-size=20",
                        PREFIX + "max-weight=1.5",
                        PREFIX + "selection-buffer=2")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsAMissingReviewDate() {
        runner.withPropertyValues(
                        PREFIX + "cut-off-date=2026-09-10",
                        PREFIX + "target-size=20",
                        PREFIX + "max-weight=0.18",
                        PREFIX + "selection-buffer=2")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * A cap finer than the 12 decimals the weights are stored at could not be
     * compared against a published weight without being rounded first, so it
     * would be honoured at a value other than the one configured.
     */
    @Test
    void rejectsACapFinerThanTheStoredWeightScale() {
        runner.withPropertyValues(
                        PREFIX + "cut-off-date=2026-09-10",
                        PREFIX + "review-date=2026-09-21",
                        PREFIX + "target-size=20",
                        PREFIX + "max-weight=0.1800000000005",
                        PREFIX + "selection-buffer=2")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsANonPositiveTargetSize() {
        runner.withPropertyValues(
                        PREFIX + "cut-off-date=2026-09-10",
                        PREFIX + "review-date=2026-09-21",
                        PREFIX + "target-size=0",
                        PREFIX + "max-weight=0.18",
                        PREFIX + "selection-buffer=2")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IndexReviewProperties.class)
    static class EnableProperties {
    }
}
