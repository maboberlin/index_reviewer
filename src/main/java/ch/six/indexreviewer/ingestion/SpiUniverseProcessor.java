package ch.six.indexreviewer.ingestion;

import ch.six.indexreviewer.domain.DataType;
import ch.six.indexreviewer.persistence.entity.SpiUniverse;
import ch.six.indexreviewer.service.SpiUniverseService;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Imports {@code spi_universe.csv} - which company is in the SPI universe on
 * which date.
 */
@Component
public class SpiUniverseProcessor extends AbstractCsvDataProcessor<SpiUniverse> {

    private static final Logger log = LoggerFactory.getLogger(SpiUniverseProcessor.class);

    private static final String DATE = "date";
    private static final String ID = "id";

    private final SpiUniverseService service;

    public SpiUniverseProcessor(SpiUniverseService service) {
        this.service = service;
    }

    @Override
    public DataType supportedType() {
        return DataType.SPI_UNIVERSE;
    }

    @Override
    protected List<String> expectedHeader() {
        return List.of(DATE, ID);
    }

    @Override
    protected SpiUniverse mapRow(CSVRecord csvRecord, long line, RowErrors errors) {
        LocalDate date = requiredDate(csvRecord, DATE, line, errors);
        Long companyId = requiredPositiveLong(csvRecord, ID, line, errors);
        if (date == null || companyId == null) {
            return null;
        }
        return new SpiUniverse(companyId, date);
    }

    /**
     * Membership in the universe is a set: a row carries nothing beyond the
     * company and the date, so a repeated row restates the same fact and cannot
     * contradict the first one. The supplied file does repeat most of its rows,
     * so these are collapsed rather than rejected - and counted, so that the
     * caller sees it happened.
     * <p>
     * This is deliberately not done for the other inputs: a repeated
     * {@code sec_data} row could carry a different price, which is a genuine
     * conflict and stays an error.
     */
    @Override
    protected List<ParsedRow<SpiUniverse>> discardRedundantRows(List<ParsedRow<SpiUniverse>> rows) {
        List<ParsedRow<SpiUniverse>> distinct =
                distinctBy(rows, row -> List.of(row.getCompanyId(), row.getDate()));
        if (distinct.size() < rows.size()) {
            log.info("Collapsed {} repeated universe rows, keeping {} distinct entries",
                    rows.size() - distinct.size(), distinct.size());
        }
        return distinct;
    }

    @Override
    protected void validateAll(List<ParsedRow<SpiUniverse>> rows, RowErrors errors) {
        // Duplicates are already collapsed; nothing else spans the whole file.
    }

    @Override
    protected void persist(List<SpiUniverse> rows) {
        service.replaceAll(rows);
    }
}
