package ch.six.indexreviewer.ingestion;

/**
 * A mapped row together with the line it came from, so that file-wide checks
 * such as duplicate detection can still report a location.
 */
public record ParsedRow<T>(long line, T value) {
}
