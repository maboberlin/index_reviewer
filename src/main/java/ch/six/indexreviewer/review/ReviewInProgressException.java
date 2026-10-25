package ch.six.indexreviewer.review;

import ch.six.indexreviewer.domain.IndexType;

/**
 * Thrown when a review is requested while one for the same index is still
 * running. Surfaces as HTTP 409.
 */
public class ReviewInProgressException extends RuntimeException {

    private final transient IndexType indexType;

    public ReviewInProgressException(IndexType indexType) {
        super("A review of index " + indexType + " is already running");
        this.indexType = indexType;
    }

    public IndexType getIndexType() {
        return indexType;
    }
}
