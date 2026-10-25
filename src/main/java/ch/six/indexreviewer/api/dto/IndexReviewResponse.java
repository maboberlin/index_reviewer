package ch.six.indexreviewer.api.dto;

import ch.six.indexreviewer.domain.ReviewStatus;

import java.util.List;

/**
 * Result of an index review: the review report returned by both the run and the
 * status endpoint.
 *
 * @param constituents final constituent list with their weighting factors
 * @param joiners      company ids entering the index
 * @param leavers      company ids leaving the index
 * @param reviewStatus state of the review run
 */
public record IndexReviewResponse(
        List<ConstituentDto> constituents,
        List<String> joiners,
        List<String> leavers,
        ReviewStatus reviewStatus) {

    public static IndexReviewResponse of(ReviewStatus status) {
        return new IndexReviewResponse(List.of(), List.of(), List.of(), status);
    }
}
