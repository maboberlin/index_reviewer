package ch.six.indexreviewer.service;

import ch.six.indexreviewer.domain.IndexType;
import ch.six.indexreviewer.domain.ReviewStatus;
import ch.six.indexreviewer.persistence.entity.IndexDefinition;
import ch.six.indexreviewer.persistence.repository.IndexDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Access to the reviewable indices and their review state.
 */
@Service
public class IndexDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(IndexDefinitionService.class);

    private final IndexDefinitionRepository repository;

    public IndexDefinitionService(IndexDefinitionRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates the row for any {@link IndexType} that does not have one yet.
     * <p>
     * Idempotent, so it can run on every start up. The schema is recreated each
     * time the application boots, so without this the review endpoints would
     * have no index to refer to.
     */
    @Transactional
    public void createMissingDefinitions() {
        for (IndexType type : IndexType.values()) {
            if (!repository.existsByName(type)) {
                repository.save(new IndexDefinition(type));
                log.info("Created index definition for {}", type);
            }
        }
    }

    public Optional<IndexDefinition> findByName(IndexType name) {
        return repository.findByName(name);
    }

    /**
     * @throws IllegalStateException if the definition is missing, which would
     *                               mean the start up seeding did not run
     */
    public IndexDefinition getByName(IndexType name) {
        return repository.findByName(name)
                .orElseThrow(() -> new IllegalStateException("No index definition for " + name));
    }

    @Transactional
    public IndexDefinition updateStatus(IndexType name, ReviewStatus status, OffsetDateTime lastProcessed) {
        IndexDefinition definition = getByName(name);
        definition.setStatus(status);
        definition.setLastProcessed(lastProcessed);
        return definition;
    }

    /**
     * Publishes that a review has started.
     * <p>
     * {@code REQUIRES_NEW} on purpose: the point of the flag is to be visible
     * to other readers <em>while</em> the review runs, and a status written in
     * the review's own transaction stays invisible until that transaction
     * commits - by which time the review is already finished.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(IndexType name) {
        IndexDefinition definition = getByName(name);
        definition.setStatus(ReviewStatus.RUNNING);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(IndexType name) {
        IndexDefinition definition = getByName(name);
        definition.setStatus(ReviewStatus.COMPLETED);
    }

    /**
     * Records that a review failed.
     * <p>
     * {@code REQUIRES_NEW} on purpose: this runs after the review's transaction
     * has rolled back, so writing it in that transaction would roll it back too
     * and leave the status stuck at {@code RUNNING}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(IndexType name) {
        IndexDefinition definition = getByName(name);
        definition.setStatus(ReviewStatus.FAILED);
    }

    public List<IndexDefinition> findAll() {
        return repository.findAll();
    }

    public long count() {
        return repository.count();
    }
}
