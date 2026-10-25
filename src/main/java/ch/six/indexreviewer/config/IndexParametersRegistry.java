package ch.six.indexreviewer.config;

import ch.six.indexreviewer.config.IndexReviewProperties.IndexParameters;
import ch.six.indexreviewer.domain.IndexType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Looks up the review parameters of an index.
 * <p>
 * Checks at construction that every {@link IndexType} is configured, so adding
 * a constant to the enum without adding its parameters fails the context at
 * start up rather than the first review that needs them. Same reasoning as
 * {@link ch.six.indexreviewer.ingestion.DataProcessorRegistry}.
 * <p>
 * Callers depend on this rather than on {@link IndexReviewProperties}, so they
 * get a guaranteed value instead of an {@code Optional} they would have to
 * handle at every use.
 */
@Component
public class IndexParametersRegistry {

    private final Map<IndexType, IndexParameters> parametersByType;

    public IndexParametersRegistry(IndexReviewProperties properties) {
        Map<IndexType, IndexParameters> configured = properties.definitions() == null
                ? Map.of()
                : properties.definitions();

        Set<IndexType> missing = EnumSet.allOf(IndexType.class);
        missing.removeAll(configured.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "No review parameters configured for " + missing
                            + "; expected index-review.definitions.<INDEX_TYPE>.* entries");
        }

        this.parametersByType = new EnumMap<>(configured);
    }

    public IndexParameters forType(IndexType indexType) {
        IndexParameters parameters = parametersByType.get(indexType);
        if (parameters == null) {
            throw new IllegalArgumentException("No review parameters configured for " + indexType);
        }
        return parameters;
    }
}
