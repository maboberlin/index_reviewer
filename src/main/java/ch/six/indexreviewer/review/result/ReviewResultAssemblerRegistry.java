package ch.six.indexreviewer.review.result;

import ch.six.indexreviewer.domain.IndexType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picks the {@link ReviewResultAssembler} for a given {@link IndexType},
 * failing at startup on a missing or duplicated index in the same way as
 * {@link ch.six.indexreviewer.review.ReviewerRegistry}.
 */
@Component
public class ReviewResultAssemblerRegistry {

    private final Map<IndexType, ReviewResultAssembler> assemblersByType;

    public ReviewResultAssemblerRegistry(List<ReviewResultAssembler> assemblers) {
        Map<IndexType, ReviewResultAssembler> byType = new EnumMap<>(IndexType.class);
        for (ReviewResultAssembler assembler : assemblers) {
            ReviewResultAssembler previous = byType.put(assembler.supportedType(), assembler);
            if (previous != null) {
                throw new IllegalStateException("Two result assemblers declare " + assembler.supportedType()
                        + ": " + previous.getClass().getName() + " and " + assembler.getClass().getName());
            }
        }
        Set<IndexType> unhandled = EnumSet.allOf(IndexType.class);
        unhandled.removeAll(byType.keySet());
        if (!unhandled.isEmpty()) {
            throw new IllegalStateException("No ReviewResultAssembler registered for " + unhandled);
        }
        this.assemblersByType = Map.copyOf(byType);
    }

    public ReviewResultAssembler forType(IndexType indexType) {
        ReviewResultAssembler assembler = assemblersByType.get(indexType);
        if (assembler == null) {
            throw new IllegalArgumentException("No ReviewResultAssembler registered for " + indexType);
        }
        return assembler;
    }
}
