package ch.six.indexreviewer.api.dto;

import ch.six.indexreviewer.domain.DataType;
import ch.six.indexreviewer.ingestion.RowError;

import java.util.List;

/**
 * Error payload for a rejected upload: what was wrong, and where.
 *
 * @param dataType      type of file that was rejected
 * @param message       summary
 * @param errorCount    total number of failures found
 * @param errors        the first {@code errorCount} failures, truncated for readability
 */
public record ValidationErrorResponse(
        DataType dataType,
        String message,
        int errorCount,
        List<RowError> errors) {
}
