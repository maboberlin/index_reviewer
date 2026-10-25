package ch.six.indexreviewer.api;

import ch.six.indexreviewer.api.dto.ApiError;
import ch.six.indexreviewer.api.dto.ValidationErrorResponse;
import ch.six.indexreviewer.ingestion.CsvValidationException;
import ch.six.indexreviewer.ingestion.ImportInProgressException;
import ch.six.indexreviewer.ingestion.RowError;
import ch.six.indexreviewer.review.ReviewFailedException;
import ch.six.indexreviewer.review.ReviewInProgressException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Translates the ingestion failures into HTTP responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * A broken file usually breaks the same way on every row; reporting all of
     * them would bury the useful information.
     */
    private static final int MAX_REPORTED_ERRORS = 20;

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CsvValidationException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(CsvValidationException ex) {
        List<RowError> all = ex.getErrors();
        List<RowError> reported = all.size() > MAX_REPORTED_ERRORS
                ? all.subList(0, MAX_REPORTED_ERRORS)
                : all;
        log.warn("Rejected {} upload: {} error(s)", ex.getDataType(), all.size());
        return ResponseEntity.badRequest()
                .body(new ValidationErrorResponse(ex.getDataType(), ex.getMessage(), all.size(), reported));
    }

    @ExceptionHandler(ImportInProgressException.class)
    public ResponseEntity<ApiError> handleImportInProgress(ImportInProgressException ex) {
        log.warn("Refused concurrent import of {}", ex.getDataType());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(ReviewInProgressException.class)
    public ResponseEntity<ApiError> handleReviewInProgress(ReviewInProgressException ex) {
        log.warn("Refused concurrent review of {}", ex.getIndexType());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(ex.getMessage()));
    }

    /**
     * The request itself was well formed; the data it operates on was not
     * sufficient to produce an index, which is 422 rather than 400 or 500.
     */
    @ExceptionHandler(ReviewFailedException.class)
    public ResponseEntity<ApiError> handleReviewFailed(ReviewFailedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ApiError(ex.getMessage()));
    }
}
