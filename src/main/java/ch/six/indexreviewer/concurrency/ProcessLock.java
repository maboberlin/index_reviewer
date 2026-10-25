package ch.six.indexreviewer.concurrency;

import java.util.Optional;

/**
 * Mutual exclusion for long running processes, keyed by an arbitrary string.
 * <p>
 * Acquisition never waits: either the caller gets the lock straight away or it
 * is told that someone else holds it, so a concurrent request can be refused
 * rather than silently queued.
 * <p>
 * The implementation is deliberately hidden behind this interface. The current
 * one is in-JVM, which is all a single instance needs; a clustered deployment
 * would swap in a database backed implementation without any caller changing.
 */
public interface ProcessLock {

    /**
     * Tries to take the lock for {@code key} without waiting.
     *
     * @return the handle to release it, or empty if it is already held
     */
    Optional<LockHandle> tryAcquire(String key);

    /**
     * Releases the lock when closed. Meant to be used in try-with-resources.
     */
    interface LockHandle extends AutoCloseable {

        @Override
        void close();
    }
}
