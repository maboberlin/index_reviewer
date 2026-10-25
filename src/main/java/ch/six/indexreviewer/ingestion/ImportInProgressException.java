package ch.six.indexreviewer.ingestion;

import ch.six.indexreviewer.domain.DataType;

/**
 * Thrown when an import is requested while one for the same data type is still
 * running. Surfaces as HTTP 409.
 */
public class ImportInProgressException extends RuntimeException {

    private final transient DataType dataType;

    public ImportInProgressException(DataType dataType) {
        super("An import for data type " + dataType + " is already running");
        this.dataType = dataType;
    }

    public DataType getDataType() {
        return dataType;
    }
}
