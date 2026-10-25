package ch.six.indexreviewer.concurrency;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-JVM {@link ProcessLock}, sufficient for a single application instance.
 * <p>
 * Uses {@link ReentrantLock#tryLock()} rather than {@code synchronized} for two
 * reasons: {@code synchronized} has no non-blocking form, so a second caller
 * would wait and then run anyway instead of being refused; and it pins the
 * carrier thread once virtual threads are enabled.
 * <p>
 * Keys come from a small fixed set (the data types), so the map cannot grow
 * without bound and entries are intentionally never evicted.
 */
@Component
public class InMemoryProcessLock implements ProcessLock {

    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public Optional<LockHandle> tryAcquire(String key) {
        ReentrantLock lock = locks.computeIfAbsent(key, unused -> new ReentrantLock());
        if (!lock.tryLock()) {
            return Optional.empty();
        }
        return Optional.of(lock::unlock);
    }
}
