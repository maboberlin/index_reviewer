package ch.six.indexreviewer.ingestion;

/**
 * A single validation failure, located in the uploaded file.
 *
 * @param line    1-based physical line in the CSV, header included
 * @param field   column the failure relates to
 * @param message what is wrong
 */
public record RowError(long line, String field, String message) {
}
