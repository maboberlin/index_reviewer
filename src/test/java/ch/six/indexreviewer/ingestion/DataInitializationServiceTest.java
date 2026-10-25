package ch.six.indexreviewer.ingestion;

import ch.six.indexreviewer.concurrency.InMemoryProcessLock;
import ch.six.indexreviewer.domain.DataType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the concurrency contract with stub processors. The real processors are
 * far too fast to overlap two imports reliably, so the blocking point is faked
 * with a latch.
 */
class DataInitializationServiceTest {

    @Test
    void refusesASecondImportOfTheSameTypeWhileTheFirstRuns() throws Exception {
        BlockingProcessor blocking = new BlockingProcessor(DataType.SPI_UNIVERSE);
        DataInitializationService service = serviceWith(blocking);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ImportResult> first = pool.submit(
                    () -> service.initialize(DataType.SPI_UNIVERSE, reader()));

            assertThat(blocking.started.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.initialize(DataType.SPI_UNIVERSE, reader()))
                    .isInstanceOf(ImportInProgressException.class)
                    .hasMessageContaining("SPI_UNIVERSE");

            blocking.release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).importedRows()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        // The lock is free again once the first import finished.
        assertThat(service.initialize(DataType.SPI_UNIVERSE, reader()).importedRows()).isEqualTo(1);
    }

    @Test
    void differentDataTypesDoNotBlockEachOther() throws Exception {
        BlockingProcessor blocking = new BlockingProcessor(DataType.SPI_UNIVERSE);
        DataInitializationService service = serviceWith(blocking);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ImportResult> first = pool.submit(
                    () -> service.initialize(DataType.SPI_UNIVERSE, reader()));
            assertThat(blocking.started.await(5, TimeUnit.SECONDS)).isTrue();

            // A different file writes to a different table, so it may proceed.
            assertThat(service.initialize(DataType.SEC_DATA, reader()).importedRows()).isEqualTo(1);

            blocking.release.countDown();
            first.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void releasesTheLockWhenTheImportFails() {
        DataProcessor failing = new DataProcessor() {
            @Override
            public DataType supportedType() {
                return DataType.COMPOSITION;
            }

            @Override
            public ImportResult process(InputStream csv) {
                throw new IllegalStateException("boom");
            }
        };
        DataInitializationService service = serviceWith(failing);

        assertThatThrownBy(() -> service.initialize(DataType.COMPOSITION, reader()))
                .isInstanceOf(IllegalStateException.class);

        // A failed import must not leave the data type permanently locked.
        assertThatThrownBy(() -> service.initialize(DataType.COMPOSITION, reader()))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Builds a service whose registry uses {@code override} for its type and no-ops elsewhere. */
    private static DataInitializationService serviceWith(DataProcessor override) {
        List<DataProcessor> processors = java.util.Arrays.stream(DataType.values())
                .map(type -> type == override.supportedType() ? override : (DataProcessor) new NoopProcessor(type))
                .toList();
        return new DataInitializationService(new DataProcessorRegistry(processors), new InMemoryProcessLock());
    }

    private static InputStream reader() {
        return new ByteArrayInputStream("id\n1\n".getBytes(StandardCharsets.UTF_8));
    }

    private record NoopProcessor(DataType type) implements DataProcessor {

        @Override
        public DataType supportedType() {
            return type;
        }

        @Override
        public ImportResult process(InputStream csv) {
            return new ImportResult(type, 1, 0);
        }
    }

    /** Holds the lock until {@link #release} is counted down. */
    private static final class BlockingProcessor implements DataProcessor {

        private final DataType type;
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingProcessor(DataType type) {
            this.type = type;
        }

        @Override
        public DataType supportedType() {
            return type;
        }

        @Override
        public ImportResult process(InputStream csv) {
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("never released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return new ImportResult(type, 1, 0);
        }
    }
}
