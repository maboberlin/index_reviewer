package ch.six.indexreviewer.api;

import ch.six.indexreviewer.api.dto.UploadResponse;
import ch.six.indexreviewer.domain.DataType;
import ch.six.indexreviewer.ingestion.CsvValidationException;
import ch.six.indexreviewer.ingestion.DataInitializationService;
import ch.six.indexreviewer.ingestion.ImportResult;
import ch.six.indexreviewer.ingestion.RowError;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Ingestion of the CSV input files.
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final DataInitializationService dataInitializationService;

    public UploadController(DataInitializationService dataInitializationService) {
        this.dataInitializationService = dataInitializationService;
    }

    /**
     * Uploads one CSV input file, replacing the previously imported data of the
     * same type.
     *
     * @param dataType which of the input files is being uploaded
     * @param file     the CSV payload
     * @return how many rows were stored
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(@RequestParam("data_type") DataType dataType,
                                 @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new CsvValidationException(dataType,
                    List.of(new RowError(0, "file", "the uploaded file is empty")));
        }
        // The processor owns decoding, so the raw bytes are handed over as they are.
        try (InputStream csv = file.getInputStream()) {
            ImportResult result = dataInitializationService.initialize(dataType, csv);
            return new UploadResponse(dataType, file.getOriginalFilename(),
                    result.importedRows(), result.skippedRows());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }
}
