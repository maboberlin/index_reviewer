package ch.six.indexreviewer.ingestion;

import ch.six.indexreviewer.domain.DataType;
import ch.six.indexreviewer.persistence.entity.SecData;
import ch.six.indexreviewer.service.SecDataService;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Imports {@code sec_data.csv} - price, free float and shares per company and date.
 * <p>
 * Price is optional on purpose. The review reads the price on the cut-off date
 * and shares and free float on the review date, so the supplied file carries no
 * price on review-date rows. Whether a price is actually present where a given
 * review needs one is checked by that review, not here: this processor stays
 * independent of any particular index or review date.
 */
@Component
public class SecDataProcessor extends AbstractCsvDataProcessor<SecData> {

    private static final String ID = "id";
    private static final String DATE = "date";
    private static final String PRICE = "price";
    private static final String FREE_FLOAT = "free_float";
    private static final String SHARES = "shares";

    private final SecDataService service;

    public SecDataProcessor(SecDataService service) {
        this.service = service;
    }

    @Override
    public DataType supportedType() {
        return DataType.SEC_DATA;
    }

    @Override
    protected List<String> expectedHeader() {
        return List.of(ID, DATE, PRICE, FREE_FLOAT, SHARES);
    }

    @Override
    protected SecData mapRow(CSVRecord csvRecord, long line, RowErrors errors) {
        Long companyId = requiredPositiveLong(csvRecord, ID, line, errors);
        LocalDate date = requiredDate(csvRecord, DATE, line, errors);
        BigDecimal price = optionalDecimal(csvRecord, PRICE, line, errors);
        BigDecimal freeFloat = requiredDecimal(csvRecord, FREE_FLOAT, line, errors);
        Long shares = requiredPositiveLong(csvRecord, SHARES, line, errors);

        if (price != null && price.signum() <= 0) {
            errors.add(line, PRICE, "must be greater than 0 but was " + price.toPlainString());
            price = null;
        }
        if (freeFloat != null && (freeFloat.signum() <= 0 || freeFloat.compareTo(BigDecimal.ONE) > 0)) {
            errors.add(line, FREE_FLOAT, "must be a fraction in (0, 1] but was " + freeFloat.toPlainString());
            freeFloat = null;
        }

        if (companyId == null || date == null || freeFloat == null || shares == null) {
            return null;
        }
        return new SecData(companyId, date, price, freeFloat, shares);
    }

    @Override
    protected void validateAll(List<ParsedRow<SecData>> rows, RowErrors errors) {
        rejectDuplicates(rows, row -> List.of(row.getCompanyId(), row.getDate()), ID, errors);
    }

    @Override
    protected void persist(List<SecData> rows) {
        service.replaceAll(rows);
    }
}
