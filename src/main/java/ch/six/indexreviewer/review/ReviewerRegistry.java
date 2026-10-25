package ch.six.indexreviewer.review;

import ch.six.indexreviewer.domain.IndexType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picks the {@link Reviewer} that handles a given {@link IndexType}.
 * <p>
 * Both consistency checks run at startup, as in
 * {@link ch.six.indexreviewer.ingestion.DataProcessorRegistry}: an index with
 * no reviewer, or two reviewers claiming the same index, fails the context
 * rather than the first request that needs it.
 */
@Component
public class ReviewerRegistry {

    private final Map<IndexType, Reviewer> reviewersByType;

    public ReviewerRegistry(List<Reviewer> reviewers) {
        Map<IndexType, Reviewer> byType = new EnumMap<>(IndexType.class);
        for (Reviewer reviewer : reviewers) {
            Reviewer previous = byType.put(reviewer.supportedType(), reviewer);
            if (previous != null) {
                throw new IllegalStateException("Two reviewers declare " + reviewer.supportedType()
                        + ": " + previous.getClass().getName() + " and " + reviewer.getClass().getName());
            }
        }
        Set<IndexType> unhandled = EnumSet.allOf(IndexType.class);
        unhandled.removeAll(byType.keySet());
        if (!unhandled.isEmpty()) {
            throw new IllegalStateException("No Reviewer registered for " + unhandled);
        }
        this.reviewersByType = Map.copyOf(byType);
    }

    public Reviewer forType(IndexType indexType) {
        Reviewer reviewer = reviewersByType.get(indexType);
        if (reviewer == null) {
            throw new IllegalArgumentException("No Reviewer registered for " + indexType);
        }
        return reviewer;
    }
}
