package ch.six.indexreviewer.api;

import ch.six.indexreviewer.domain.DataType;
import ch.six.indexreviewer.ingestion.DataInitializationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the endpoint itself, which composes two steps: run the review, then
 * read the report back out of the committed state.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IndexReviewControllerTest {

    private static final Path TASK_DIR = Path.of("task");
    private static final String RUN = "/api/v1/index-reviews/run";
    private static final String SMI_REQUEST = """
            {"index_type":"SMI_INDEX_REVIEW_Q3_2026"}""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DataInitializationService dataInitializationService;

    @BeforeEach
    void importInputFiles() throws IOException {
        importTaskFile(DataType.SPI_UNIVERSE, "spi_universe.csv");
        importTaskFile(DataType.SEC_DATA, "sec_data.csv");
        importTaskFile(DataType.COMPOSITION, "composition.csv");
    }

    @Test
    void runsTheReviewAndReturnsTheReport() throws Exception {
        mockMvc.perform(post(RUN).contentType(MediaType.APPLICATION_JSON).content(SMI_REQUEST))
                .andExpect(status().isOk())
                // Both steps ran: the review produced constituents and the status
                // was read back from what it committed.
                .andExpect(jsonPath("$.constituents.length()").value(20))
                .andExpect(jsonPath("$.review_status").value("COMPLETED"))
                .andExpect(jsonPath("$.constituents[0].id").exists())
                .andExpect(jsonPath("$.constituents[0].weight").exists())
                .andExpect(jsonPath("$.joiners").isArray())
                .andExpect(jsonPath("$.leavers").isArray());
    }

    @Test
    void rejectsAnUnknownIndexType() throws Exception {
        mockMvc.perform(post(RUN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"index_type":"NOT_AN_INDEX"}"""))
                .andExpect(status().isBadRequest());
    }

    private void importTaskFile(DataType dataType, String fileName) throws IOException {
        Path file = TASK_DIR.resolve(fileName);
        assumeTrue(Files.exists(file), () -> "input file not available: " + file.toAbsolutePath());
        try (InputStream csv = Files.newInputStream(file)) {
            dataInitializationService.initialize(dataType, csv);
        }
    }
}
