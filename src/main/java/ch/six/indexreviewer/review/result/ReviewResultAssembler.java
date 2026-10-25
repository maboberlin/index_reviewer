package ch.six.indexreviewer.review.result;

import ch.six.indexreviewer.api.dto.IndexReviewResponse;
import ch.six.indexreviewer.domain.IndexType;

/**
 * Builds the review report from what a review stored.
 * <p>
 * Deliberately separate from the review itself and driven only by persisted
 * state, so the status endpoint can return the same report later without
 * re-running anything.
 */
public interface ReviewResultAssembler {

    /**
     * The index this assembler handles. Must be unique across all assemblers.
     */
    IndexType supportedType();

    /**
     * Reads the stored result of the last review and turns it into the report.
     */
    IndexReviewResponse assemble(IndexType indexType);
}
