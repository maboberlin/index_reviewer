package ch.six.indexreviewer.ingestion;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Shared CSV handling for all input files: parse, check the header, map and
 * validate every row, then hand the result to the subclass for persisting.
 * <p>
 * Subclasses contribute only what actually differs per file - the expected
 * header, how a row becomes an entity, any file-wide rules, and where to store
 * the result.
 *
 * @param <T> entity produced from one row
 */
public abstract class AbstractCsvDataProcessor<T> implements DataProcessor {

    /** The input files are semicolon separated. */
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setDelimiter(';')
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .setTrim(true)
            .get();

    /** Input files are read as UTF-8, whatever byte order mark they carry. */
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    /** Columns the file must have, in order. */
    protected abstract List<String> expectedHeader();

    /**
     * Maps one record. Returns {@code null} when the row is invalid, after
     * having recorded why in {@code errors}.
     */
    protected abstract T mapRow(CSVRecord csvRecord, long line, RowErrors errors);

    /** Checks rules spanning the whole file, typically duplicate keys. */
    protected abstract void validateAll(List<ParsedRow<T>> rows, RowErrors errors);

    /**
     * Last chance to drop rows before they are validated and stored. The
     * default keeps every row; a processor overrides this when a repeated row
     * is redundant rather than wrong.
     */
    protected List<ParsedRow<T>> discardRedundantRows(List<ParsedRow<T>> rows) {
        return rows;
    }

    /** Stores the file, replacing whatever was imported before. */
    protected abstract void persist(List<T> rows);

    /**
     * Runs inside a transaction so that a row rejected late in the file rolls
     * back the rows already inserted. A half-imported universe would silently
     * produce a wrong index, so the import is all-or-nothing.
     * <p>
     * The caller takes the process lock <em>around</em> this call, never inside
     * it, which keeps the lock held until after the commit.
     */
    @Override
    @Transactional
    public ImportResult process(InputStream csv) {
        RowErrors errors = new RowErrors();

        List<ParsedRow<T>> rows = new ArrayList<>();
        parseRows(csv, errors, rows);

        List<ParsedRow<T>> retained = discardRedundantRows(rows);
        validateAll(retained, errors);

        // Only genuinely empty if nothing was rejected on the way here - otherwise
        // the rows did exist and the errors already explain why none survived.
        if (retained.isEmpty() && errors.isEmpty()) {
            errors.add(1, "*", "file contains a header but no data rows");
        }
        if (!errors.isEmpty()) {
            throw new CsvValidationException(supportedType(), errors.asList());
        }

        List<T> inputData = retained.stream().map(ParsedRow::value).toList();
        persist(inputData);

        return new ImportResult(supportedType(), retained.size(), rows.size() - (long) retained.size());
    }

    private void parseRows(InputStream csv, RowErrors errors, List<ParsedRow<T>> rows) {
        try (Reader reader = new InputStreamReader(BOMInputStream.builder().setInputStream(csv).get(), CHARSET);
             CSVParser parser = FORMAT.parse(reader)
        ) {
            requireExpectedHeader(parser, errors);
            if (!errors.isEmpty()) {
                // Without a usable header every row would fail for the same reason.
                throw new CsvValidationException(supportedType(), errors.asList());
            }
            for (CSVRecord csvRecord : parser) {
                long line = csvRecord.getRecordNumber() + 1; // + the header line
                if (!csvRecord.isConsistent()) {
                    errors.add(line, "*", "expected " + expectedHeader().size()
                            + " fields but found " + csvRecord.size());
                    continue;
                }
                T mapped = mapRow(csvRecord, line, errors);
                if (mapped != null) {
                    rows.add(new ParsedRow<>(line, mapped));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded " + supportedType() + " file", e);
        }
    }

    /**
     * Keeps the first row per key, preserving file order.
     */
    protected static <T, K> List<ParsedRow<T>> distinctBy(List<ParsedRow<T>> rows, Function<T, K> keyExtractor) {
        Set<K> seen = new HashSet<>();
        return rows.stream()
                .filter(row -> seen.add(keyExtractor.apply(row.value())))
                .toList();
    }

    private void requireExpectedHeader(CSVParser parser, RowErrors errors) {
        List<String> actual = parser.getHeaderNames();
        if (!actual.equals(expectedHeader())) {
            errors.add(1, "*", "expected header " + expectedHeader() + " but found " + actual);
        }
    }

    // --- helpers for subclasses ------------------------------------------------

    /**
     * Raw value of a column, with blank treated as absent. Distinguishing an
     * empty cell from a zero matters: {@code sec_data.csv} leaves the price
     * empty on the review date, and reading that as 0.0 would zero out the
     * market capitalization instead of marking it unknown.
     */
    protected static String rawValue(CSVRecord csvRecord, String field) {
        String value = csvRecord.get(field);
        return (value == null || value.isBlank()) ? null : value;
    }

    protected static Long requiredPositiveLong(CSVRecord csvRecord, String field, long line, RowErrors errors) {
        String raw = rawValue(csvRecord, field);
        if (raw == null) {
            errors.add(line, field, "must not be empty");
            return null;
        }
        try {
            long parsed = Long.parseLong(raw);
            if (parsed <= 0) {
                errors.add(line, field, "must be greater than 0 but was " + parsed);
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            errors.add(line, field, quote(raw) + " is not a whole number");
            return null;
        }
    }

    /**
     * Parses a decimal that may legitimately be absent.
     * <p>
     * {@link BigDecimal} rather than {@code double}: prices, free floats and
     * the weights derived from them are exact decimal quantities, and binary
     * floating point cannot represent them exactly. The scale written in the
     * file is preserved, so what was uploaded is what is stored.
     *
     * @return the value, or {@code null} when the cell is empty or unparseable
     */
    protected static BigDecimal optionalDecimal(CSVRecord csvRecord, String field, long line, RowErrors errors) {
        String raw = rawValue(csvRecord, field);
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            errors.add(line, field, quote(raw) + " is not a decimal number");
            return null;
        }
    }

    protected static BigDecimal requiredDecimal(CSVRecord csvRecord, String field, long line, RowErrors errors) {
        if (rawValue(csvRecord, field) == null) {
            errors.add(line, field, "must not be empty");
            return null;
        }
        return optionalDecimal(csvRecord, field, line, errors);
    }

    protected static LocalDate requiredDate(CSVRecord csvRecord, String field, long line, RowErrors errors) {
        String raw = rawValue(csvRecord, field);
        if (raw == null) {
            errors.add(line, field, "must not be empty");
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            errors.add(line, field, quote(raw) + " is not an ISO date (yyyy-MM-dd)");
            return null;
        }
    }

    /**
     * Records an error for every row whose key was already seen.
     */
    protected <K> void rejectDuplicates(List<ParsedRow<T>> rows,
                                        Function<T, K> keyExtractor,
                                        String field,
                                        RowErrors errors) {
        Map<K, Long> firstSeen = new HashMap<>();
        for (ParsedRow<T> row : rows) {
            K key = keyExtractor.apply(row.value());
            Long previous = firstSeen.putIfAbsent(key, row.line());
            if (previous != null) {
                errors.add(row.line(), field, "duplicate entry " + key + ", already present on line " + previous);
            }
        }
    }

    private static String quote(String value) {
        return "'" + value + "'";
    }
}
