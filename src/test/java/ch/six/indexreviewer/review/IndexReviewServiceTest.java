package ch.six.indexreviewer.review;

import ch.six.indexreviewer.concurrency.InMemoryProcessLock;
import ch.six.indexreviewer.domain.IndexType;
import ch.six.indexreviewer.persistence.entity.IndexDefinition;
import ch.six.indexreviewer.service.IndexDefinitionService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the concurrency and status contract with a stub reviewer. A real
 * review finishes far too quickly to overlap two runs reliably, so the
 * blocking point is faked with a latch.
 */
class IndexReviewServiceTest {

    private static final IndexType SMI = IndexType.SMI_INDEX_REVIEW_Q3_2026;

    private final IndexDefinitionService definitionService = Mockito.mock(IndexDefinitionService.class);

    @Test
    void refusesASecondReviewWhileTheFirstRuns() throws Exception {
        BlockingReviewer blocking = new BlockingReviewer();
        IndexReviewService service = serviceWith(blocking);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = pool.submit(() -> service.runReview(SMI));
            assertThat(blocking.started.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.runReview(SMI))
                    .isInstanceOf(ReviewInProgressException.class)
                    .hasMessageContaining("SMI_INDEX_REVIEW_Q3_2026");

            blocking.release.countDown();
            first.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // The lock is free again once the first review finished.
        assertThatNoException().isThrownBy(() -> service.runReview(SMI));
    }

    @Test
    void publishesRunningBeforeTheReviewAndNotAfterwards() {
        IndexReviewService service = serviceWith(new BlockingReviewer(true));

        service.runReview(SMI);

        verify(definitionService).markRunning(SMI);
        verify(definitionService, never()).markFailed(SMI);
    }

    @Test
    void marksTheReviewFailedAndReleasesTheLockWhenItThrows() {
        IndexReviewService service = serviceWith(new FailingReviewer());

        assertThatThrownBy(() -> service.runReview(SMI))
                .isInstanceOf(ReviewFailedException.class)
                .hasMessageContaining("SMI_INDEX_REVIEW_Q3_2026");

        verify(definitionService).markFailed(SMI);

        // A failed review must not leave the index permanently locked.
        assertThatThrownBy(() -> service.runReview(SMI))
                .isInstanceOf(ReviewFailedException.class);
    }

    private IndexReviewService serviceWith(Reviewer reviewer) {
        when(definitionService.getByName(any())).thenReturn(new IndexDefinition(SMI));

        return new IndexReviewService(
                new ReviewerRegistry(List.of(reviewer)),
                definitionService,
                new InMemoryProcessLock());
    }

    /** Holds the review open until {@link #release} is counted down. */
    private static final class BlockingReviewer implements Reviewer {

        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingReviewer() {
        }

        private BlockingReviewer(boolean returnImmediately) {
            if (returnImmediately) {
                release.countDown();
            }
        }

        @Override
        public IndexType supportedType() {
            return SMI;
        }

        @Override
        public void review(IndexType indexType) {
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("never released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    private static final class FailingReviewer implements Reviewer {

        @Override
        public IndexType supportedType() {
            return SMI;
        }

        @Override
        public void review(IndexType indexType) {
            throw new IllegalStateException("no data");
        }
    }
}
