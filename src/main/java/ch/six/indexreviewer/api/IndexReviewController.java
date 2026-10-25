package ch.six.indexreviewer.api;

import ch.six.indexreviewer.api.dto.IndexReviewRequest;
import ch.six.indexreviewer.api.dto.IndexReviewResponse;
import ch.six.indexreviewer.domain.IndexType;
import ch.six.indexreviewer.domain.ReviewStatus;
import ch.six.indexreviewer.review.IndexReviewService;
import ch.six.indexreviewer.review.result.ReviewResultAssemblerRegistry;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Execution and status enquiry of an index review.
 */
@RestController
@RequestMapping("/api/v1/index-reviews")
public class IndexReviewController {

    private final IndexReviewService indexReviewService;
    private final ReviewResultAssemblerRegistry assemblerRegistry;

    public IndexReviewController(IndexReviewService indexReviewService,
                                 ReviewResultAssemblerRegistry assemblerRegistry) {
        this.indexReviewService = indexReviewService;
        this.assemblerRegistry = assemblerRegistry;
    }

    /**
     * Runs the index review for the requested index type and returns the review report.
     * <p>
     * Running and reporting are two steps: the review leaves its outcome in the
     * database, then the report is read back from that committed state. The
     * status endpoint will reuse the second step on its own.
     */
    @PostMapping("/run")
    public IndexReviewResponse run(@RequestBody IndexReviewRequest request) {
        IndexType indexType = request.indexType();
        indexReviewService.runReview(indexType);
        return assemblerRegistry.forType(indexType).assemble(indexType);
    }

    /**
     * Returns the report of the last review run for the requested index type.
     */
    @PostMapping("/status")
    public IndexReviewResponse status(@RequestBody IndexReviewRequest request) {
        IndexType indexType = request.indexType();
        return assemblerRegistry.forType(indexType).assemble(indexType);
    }
}
