package ch.six.indexreviewer.api.dto;

import ch.six.indexreviewer.domain.IndexType;

/**
 * Request body of the run and status endpoints: {@code {"index_type": "..."}}.
 */
public record IndexReviewRequest(IndexType indexType) {
}
