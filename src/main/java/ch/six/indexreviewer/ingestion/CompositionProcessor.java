package ch.six.indexreviewer.ingestion;

import ch.six.indexreviewer.domain.DataType;
import ch.six.indexreviewer.persistence.entity.Composition;
import ch.six.indexreviewer.service.CompositionService;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Imports {@code composition.csv} - the companies currently in the index, used
 * as the baseline for determining joiners and leavers.
 */
@Component
public class CompositionProcessor extends AbstractCsvDataProcessor<Composition> {

    private static final String ID = "id";

    private final CompositionService service;

    public CompositionProcessor(CompositionService service) {
        this.service = service;
    }

    @Override
    public DataType supportedType() {
        return DataType.COMPOSITION;
    }

    @Override
    protected List<String> expectedHeader() {
        return List.of(ID);
    }

    @Override
    protected Composition mapRow(CSVRecord csvRecord, long line, RowErrors errors) {
        Long companyId = requiredPositiveLong(csvRecord, ID, line, errors);
        return companyId == null ? null : new Composition(companyId);
    }

    @Override
    protected void validateAll(List<ParsedRow<Composition>> rows, RowErrors errors) {
        // A company cannot be in the index twice.
        rejectDuplicates(rows, Composition::getCompanyId, ID, errors);
    }

    @Override
    protected void persist(List<Composition> rows) {
        service.replaceAll(rows);
    }
}
