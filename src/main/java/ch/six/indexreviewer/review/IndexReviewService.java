package ch.six.indexreviewer.review;

import ch.six.indexreviewer.concurrency.ProcessLock;
import ch.six.indexreviewer.domain.IndexType;
import ch.six.indexreviewer.service.IndexDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs a review: serialises runs, drives the status, and delegates the work to
 * the reviewer registered for the index. Reporting on the outcome is a separate
 * concern and belongs to the caller.
 * <p>
 * Two reviews of the same index cannot overlap - the second is refused rather
 * than queued. Mutual exclusion is the {@link ProcessLock}, not the status
 * column: reading a status and then writing it is check-then-act and lets two
 * callers through, a status written inside the work transaction is invisible
 * until that transaction commits, and a status left at {@code RUNNING} by a
 * crashed run would block the index for good.
 * <p>
 * This class is deliberately <strong>not</strong> {@code @Transactional}; see
 * the propagation notes on each step below.
 */
@Service
public class IndexReviewService {

    private static final Logger log = LoggerFactory.getLogger(IndexReviewService.class);

    private final ReviewerRegistry reviewerRegistry;
    private final IndexDefinitionService indexDefinitionService;
    private final ProcessLock processLock;

    public IndexReviewService(ReviewerRegistry reviewerRegistry,
                              IndexDefinitionService indexDefinitionService,
                              ProcessLock processLock) {
        this.reviewerRegistry = reviewerRegistry;
        this.indexDefinitionService = indexDefinitionService;
        this.processLock = processLock;
    }

    /**
     * Runs the review, leaving the outcome in the database.
     * <p>
     * Returns nothing on purpose: running a review and reporting on one are
     * separate concerns, and the report is read back from committed state by a
     * {@link ch.six.indexreviewer.review.result.ReviewResultAssembler}. The
     * caller composes the two, which is what lets the status endpoint produce
     * the same report without running anything.
     *
     * @throws ReviewInProgressException if a review of the same index is running
     * @throws ReviewFailedException     if the review could not be completed
     */
    public void runReview(IndexType indexType) {
        try (ProcessLock.LockHandle handle = processLock.tryAcquire(lockKey(indexType))
                .orElseThrow(() -> new ReviewInProgressException(indexType))) {

            // Committed on its own so the status is actually observable while the
            // review runs; inside the work transaction it would never be seen.
            indexDefinitionService.markRunning(indexType);

            try {
                // Separate bean, so the transactional proxy applies. Loads the
                // definition and sets COMPLETED itself, within the same
                // transaction as the results it wrote.
                reviewerRegistry.forType(indexType).review(indexType);
                indexDefinitionService.markCompleted(indexType);
            } catch (RuntimeException e) {
                // In its own transaction: the work transaction has already rolled
                // back, so a status written in it would roll back with it.
                indexDefinitionService.markFailed(indexType);
                log.error("Review of {} failed", indexType, e);
                throw new ReviewFailedException(indexType, e);
            }
        }
    }

    private static String lockKey(IndexType indexType) {
        return "review:" + indexType.name();
    }
}
