package ch.six.indexreviewer.review;

import ch.six.indexreviewer.api.dto.ConstituentDto;
import ch.six.indexreviewer.api.dto.IndexReviewResponse;
import ch.six.indexreviewer.domain.DataType;
import ch.six.indexreviewer.domain.IndexType;
import ch.six.indexreviewer.domain.ReviewStatus;
import ch.six.indexreviewer.ingestion.DataInitializationService;
import ch.six.indexreviewer.review.result.ReviewResultAssemblerRegistry;
import ch.six.indexreviewer.service.CompositionService;
import ch.six.indexreviewer.service.FfmcapService;
import ch.six.indexreviewer.service.IndexDefinitionService;
import ch.six.indexreviewer.service.SmiIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the real review over the supplied CSV files.
 */
@SpringBootTest
class IndexReviewIntegrationTest {

    private static final Path TASK_DIR = Path.of("task");
    private static final IndexType SMI = IndexType.SMI_INDEX_REVIEW_Q3_2026;
    private static final BigDecimal CAP = new BigDecimal("0.18");

    /** In the universe on the review date, but with no review-date sec_data. */
    private static final String COMPANY_WITHOUT_REVIEW_DATA = "166";

    @Autowired
    private DataInitializationService dataInitializationService;
    @Autowired
    private IndexReviewService indexReviewService;
    @Autowired
    private IndexDefinitionService indexDefinitionService;
    @Autowired
    private FfmcapService ffmcapService;
    @Autowired
    private SmiIndexService smiIndexService;
    @Autowired
    private CompositionService compositionService;
    @Autowired
    private ReviewResultAssemblerRegistry assemblerRegistry;

    @BeforeEach
    void importInputFiles() throws IOException {
        importTaskFile(DataType.SPI_UNIVERSE, "spi_universe.csv");
        importTaskFile(DataType.SEC_DATA, "sec_data.csv");
        importTaskFile(DataType.COMPOSITION, "composition.csv");
    }

    @Test
    void producesAnIndexOfTheTargetSize() {
        IndexReviewResponse response = runAndAssemble();

        assertThat(response.constituents()).hasSize(20);
        assertThat(response.reviewStatus()).isEqualTo(ReviewStatus.COMPLETED);
    }

    @Test
    void weightsSumToOneAndRespectTheCap() {
        IndexReviewResponse response = runAndAssemble();

        BigDecimal total = response.constituents().stream()
                .map(ConstituentDto::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(total).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(response.constituents())
                .allSatisfy(c -> assertThat(c.weight()).isLessThanOrEqualTo(CAP));
    }

    @Test
    void returnsConstituentsHeaviestFirst() {
        List<BigDecimal> weights = runAndAssemble().constituents().stream()
                .map(ConstituentDto::weight)
                .toList();

        assertThat(weights).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    /**
     * 205 universe companies but only 204 have review-date security data, so
     * exactly one is ineligible and must not reach the ranking table.
     */
    @Test
    void excludesTheCompanyWithoutReviewDateData() {
        IndexReviewResponse response = runAndAssemble();

        assertThat(ffmcapService.findByIndex(SMI)).hasSize(204);
        assertThat(response.constituents())
                .extracting(ConstituentDto::id)
                .doesNotContain(COMPANY_WITHOUT_REVIEW_DATA);
    }

    @Test
    void reportsJoinersAndLeaversAgainstTheCurrentComposition() {
        IndexReviewResponse response = runAndAssemble();

        List<String> constituentIds = response.constituents().stream().map(ConstituentDto::id).toList();

        assertThat(response.joiners()).doesNotHaveDuplicates().isSubsetOf(constituentIds);
        assertThat(response.leavers()).doesNotHaveDuplicates().doesNotContainAnyElementsOf(constituentIds);

        // Both the old and the new composition hold 20 names, so every company
        // that joins displaces exactly one that leaves.
        assertThat(compositionService.count()).isEqualTo(20);
        assertThat(response.joiners()).hasSameSizeAs(response.leavers());
    }

    @Test
    void rerunningReplacesTheResultRatherThanAppendingToIt() {
        IndexReviewResponse first = runAndAssemble();
        IndexReviewResponse second = runAndAssemble();

        assertThat(second.constituents()).isEqualTo(first.constituents());
        assertThat(second.joiners()).isEqualTo(first.joiners());
        assertThat(second.leavers()).isEqualTo(first.leavers());
        assertThat(smiIndexService.findByIndex(SMI)).hasSize(20);
        assertThat(ffmcapService.findByIndex(SMI)).hasSize(204);
    }

    /**
     * Runs the review and reads the report back, which is exactly the pair of
     * steps the controller performs.
     */
    private IndexReviewResponse runAndAssemble() {
        indexReviewService.runReview(SMI);
        return assemblerRegistry.forType(SMI).assemble(SMI);
    }

    private void importTaskFile(DataType dataType, String fileName) throws IOException {
        Path file = TASK_DIR.resolve(fileName);
        assumeTrue(Files.exists(file), () -> "input file not available: " + file.toAbsolutePath());
        try (InputStream csv = Files.newInputStream(file)) {
            dataInitializationService.initialize(dataType, csv);
        }
    }
}
