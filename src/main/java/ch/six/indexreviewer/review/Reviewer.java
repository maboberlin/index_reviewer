package ch.six.indexreviewer.review;

import ch.six.indexreviewer.domain.IndexType;

/**
 * Runs the review of one index: rank, select, weight, persist.
 * <p>
 * One implementation per {@link IndexType}, picked up by
 * {@link ReviewerRegistry} through component scanning, so supporting a further
 * index means adding a class. Implementations carry the transaction; the
 * caller holds the process lock around them.
 */
public interface Reviewer {

    /**
     * The index this reviewer handles. Must be unique across all reviewers.
     */
    IndexType supportedType();

    /**
     * Recomputes the index and stores the outcome, replacing whatever a
     * previous run of the same index produced. Also advances the definition to
     * {@code COMPLETED} within the same transaction, so status and results
     * become visible together.
     * <p>
     * Takes the index type rather than the loaded {@code IndexDefinition} on
     * purpose. The implementation loads it inside its own transaction, where
     * it is managed and therefore dirty-checked; an entity loaded by the
     * caller would arrive detached and the status change would be lost
     * silently, while the rows still saved correctly because they only need
     * its id.
     */
    void review(IndexType indexType);
}
