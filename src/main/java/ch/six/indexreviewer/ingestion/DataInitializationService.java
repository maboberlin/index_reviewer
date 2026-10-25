package ch.six.indexreviewer.ingestion;

import ch.six.indexreviewer.concurrency.ProcessLock;
import ch.six.indexreviewer.domain.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * Entry point for loading input data: serialises imports, then delegates to the
 * processor responsible for the data type.
 * <p>
 * Two uploads of the <em>same</em> data type cannot overlap - the second is
 * refused rather than queued, because the caller asked to import a file, not to
 * wait behind someone else's. Different data types write to different tables
 * and are therefore allowed to run at the same time.
 * <p>
 * This class is deliberately <strong>not</strong> {@code @Transactional}. The
 * lock is taken around the call to the processor, whose {@code process} method
 * carries the transaction, so the lock is still held when the transaction
 * commits. Were the lock taken inside the transactional method instead, it
 * would be released before the commit and a second import could read
 * uncommitted state. For the same reason the processor must be a separate bean:
 * calling it through {@code this} would bypass the proxy and lose the
 * transaction altogether.
 */
@Service
public class DataInitializationService {

    private static final Logger log = LoggerFactory.getLogger(DataInitializationService.class);

    private final DataProcessorRegistry registry;
    private final ProcessLock processLock;

    public DataInitializationService(DataProcessorRegistry registry, ProcessLock processLock) {
        this.registry = registry;
        this.processLock = processLock;
    }

    /**
     * Validates and stores one input file.
     *
     * @throws ImportInProgressException if an import of the same data type is running
     * @throws CsvValidationException    if the file is rejected
     */
    public ImportResult initialize(DataType dataType, InputStream csv) {
        try (ProcessLock.LockHandle handle = processLock.tryAcquire(lockKey(dataType))
                .orElseThrow(() -> new ImportInProgressException(dataType))) {
            log.info("Starting import of {}", dataType);
            ImportResult result = registry.forType(dataType).process(csv);
            log.info("Imported {} rows for {}", result.importedRows(), dataType);
            return result;
        }
    }

    private static String lockKey(DataType dataType) {
        return "import:" + dataType.name();
    }
}
