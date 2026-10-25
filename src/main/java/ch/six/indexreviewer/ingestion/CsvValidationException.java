package ch.six.indexreviewer.ingestion;

import ch.six.indexreviewer.domain.DataType;

import java.util.List;

/**
 * Thrown when an uploaded file is rejected. Surfaces as HTTP 400.
 * <p>
 * Carries every failure found, not just the first one.
 */
public class CsvValidationException extends RuntimeException {

    private final transient DataType dataType;
    private final transient List<RowError> errors;

    public CsvValidationException(DataType dataType, List<RowError> errors) {
        super(errors.size() + " validation error(s) in the uploaded " + dataType + " file");
        this.dataType = dataType;
        this.errors = List.copyOf(errors);
    }

    public DataType getDataType() {
        return dataType;
    }

    public List<RowError> getErrors() {
        return errors;
    }
}
