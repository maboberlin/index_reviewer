package ch.six.indexreviewer.ingestion;

import ch.six.indexreviewer.domain.DataType;

/**
 * Outcome of a successful import.
 *
 * @param dataType     type of file that was imported
 * @param importedRows rows stored
 * @param skippedRows  rows dropped as redundant, see {@link DataProcessor}
 */
public record ImportResult(DataType dataType, long importedRows, long skippedRows) {
}
