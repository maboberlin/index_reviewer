package ch.six.indexreviewer.ingestion;

import ch.six.indexreviewer.domain.DataType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picks the {@link DataProcessor} that handles a given {@link DataType}.
 * <p>
 * Spring injects every processor it finds, so adding support for a further
 * input file means adding one class - this registry does not change. Both
 * consistency checks run at startup: a missing or duplicated processor fails
 * the context rather than surfacing on the first upload that needs it.
 */
@Component
public class DataProcessorRegistry {

    private final Map<DataType, DataProcessor> processorsByType;

    public DataProcessorRegistry(List<DataProcessor> processors) {
        Map<DataType, DataProcessor> byType = new EnumMap<>(DataType.class);
        for (DataProcessor processor : processors) {
            DataProcessor previous = byType.put(processor.supportedType(), processor);
            if (previous != null) {
                throw new IllegalStateException("Two processors declare " + processor.supportedType()
                        + ": " + previous.getClass().getName() + " and " + processor.getClass().getName());
            }
        }
        Set<DataType> unhandled = EnumSet.allOf(DataType.class);
        unhandled.removeAll(byType.keySet());
        if (!unhandled.isEmpty()) {
            throw new IllegalStateException("No DataProcessor registered for " + unhandled);
        }
        this.processorsByType = Map.copyOf(byType);
    }

    public DataProcessor forType(DataType dataType) {
        DataProcessor processor = processorsByType.get(dataType);
        if (processor == null) {
            throw new IllegalArgumentException("No DataProcessor registered for " + dataType);
        }
        return processor;
    }
}
