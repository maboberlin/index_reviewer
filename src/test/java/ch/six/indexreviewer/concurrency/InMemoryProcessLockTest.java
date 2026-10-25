package ch.six.indexreviewer.concurrency;

import ch.six.indexreviewer.concurrency.ProcessLock.LockHandle;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryProcessLockTest {

    private final InMemoryProcessLock lock = new InMemoryProcessLock();

    @Test
    void grantsAFreeLock() {
        assertThat(lock.tryAcquire("a")).isPresent();
    }

    @Test
    void refusesALockHeldByAnotherThread() throws Exception {
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = pool.submit(() -> {
                try (LockHandle handle = lock.tryAcquire("a").orElseThrow()) {
                    acquired.countDown();
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });

            assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(lock.tryAcquire("a")).isEmpty();

            release.countDown();
            holder.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(lock.tryAcquire("a")).isPresent();
    }

    @Test
    void keysAreIndependent() {
        Optional<LockHandle> first = lock.tryAcquire("a");
        assertThat(first).isPresent();
        assertThat(lock.tryAcquire("b")).isPresent();
    }

    @Test
    void releasesOnClose() {
        lock.tryAcquire("a").orElseThrow().close();
        assertThat(lock.tryAcquire("a")).isPresent();
    }
}
