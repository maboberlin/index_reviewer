package ch.six.indexreviewer.review;

import ch.six.indexreviewer.domain.IndexType;

/**
 * Thrown when a review could not be completed, typically because the input
 * data is missing or insufficient. Surfaces as HTTP 422.
 */
public class ReviewFailedException extends RuntimeException {

    private final transient IndexType indexType;

    public ReviewFailedException(IndexType indexType, Throwable cause) {
        super("Review of index " + indexType + " failed: " + cause.getMessage(), cause);
        this.indexType = indexType;
    }

    public IndexType getIndexType() {
        return indexType;
    }
}
