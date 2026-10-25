package ch.six.indexreviewer.ingestion;

import ch.six.indexreviewer.domain.DataType;
import ch.six.indexreviewer.persistence.entity.SecData;
import ch.six.indexreviewer.service.CompositionService;
import ch.six.indexreviewer.service.SecDataService;
import ch.six.indexreviewer.service.SpiUniverseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises the processors against the real supplied files and against
 * deliberately broken ones.
 */
@SpringBootTest
class CsvImportIntegrationTest {

    private static final Path TASK_DIR = Path.of("task");
    private static final LocalDate REVIEW_DATE = LocalDate.of(2026, 9, 21);
    private static final LocalDate CUT_OFF_DATE = LocalDate.of(2026, 9, 10);

    @Autowired
    private DataInitializationService service;
    @Autowired
    private SpiUniverseService spiUniverseService;
    @Autowired
    private SecDataService secDataService;
    @Autowired
    private CompositionService compositionService;

    /**
     * The supplied file lists 409 rows but only 205 distinct companies, all on
     * the review date. Membership is a set, so the repeats are collapsed and
     * reported rather than treated as an error.
     */
    @Test
    void importsTheSuppliedSpiUniverseCollapsingItsRepeatedRows() throws IOException {
        ImportResult result = importTaskFile(DataType.SPI_UNIVERSE, "spi_universe.csv");

        assertThat(result.importedRows()).isEqualTo(205);
        assertThat(result.skippedRows()).isEqualTo(204);
        assertThat(spiUniverseService.count()).isEqualTo(205);
        assertThat(spiUniverseService.findByDate(REVIEW_DATE)).hasSize(205);
    }

    @Test
    void keepsTheSameCompanyOnDifferentUniverseDates() {
        String csv = """
                date;id
                2026-09-21;1
                2026-09-21;1
                2026-09-10;1
                """;

        ImportResult result = service.initialize(DataType.SPI_UNIVERSE, reader(csv));

        // Only the repeat within the same date is redundant.
        assertThat(result.importedRows()).isEqualTo(2);
        assertThat(result.skippedRows()).isEqualTo(1);
    }

    /**
     * Guards the decimal mapping. Hibernate's default for a {@code BigDecimal}
     * column is {@code numeric(38,2)}, which would turn a free float of 0.6766
     * into 0.68 and quietly misweight the index.
     */
    @Test
    void storesDecimalsExactlyRatherThanRoundingThem() {
        String csv = """
                id;date;price;free_float;shares
                1;2026-09-10;2080.123456;0.6766;309375
                """;

        service.initialize(DataType.SEC_DATA, reader(csv));

        SecData stored = secDataService.findByDate(CUT_OFF_DATE).getFirst();
        assertThat(stored.getPrice()).isEqualByComparingTo(new BigDecimal("2080.123456"));
        assertThat(stored.getFreeFloat()).isEqualByComparingTo(new BigDecimal("0.6766"));
    }

    @Test
    void stillRejectsDuplicateSecDataBecauseItCouldConflict() {
        String csv = """
                id;date;price;free_float;shares
                1;2026-09-10;10.0;0.5;100
                1;2026-09-10;99.0;0.5;100
                """;

        assertThatThrownBy(() -> service.initialize(DataType.SEC_DATA, reader(csv)))
                .isInstanceOf(CsvValidationException.class)
                .satisfies(thrown -> assertThat(errorsOf(thrown))
                        .anySatisfy(error -> assertThat(error.message()).contains("duplicate entry")));
    }

    @Test
    void importsTheSuppliedComposition() throws IOException {
        ImportResult result = importTaskFile(DataType.COMPOSITION, "composition.csv");

        assertThat(result.importedRows()).isEqualTo(20);
        assertThat(compositionService.count()).isEqualTo(20);
    }

    @Test
    void importsTheSuppliedSecDataKeepingTheMissingPricesNull() throws IOException {
        ImportResult result = importTaskFile(DataType.SEC_DATA, "sec_data.csv");

        assertThat(result.importedRows()).isEqualTo(409);

        // Price is only quoted on the cut-off date. The review-date rows must keep
        // a null price - a 0.0 would silently zero out the market capitalization.
        assertThat(secDataService.findByDate(REVIEW_DATE))
                .isNotEmpty()
                .allSatisfy(row -> assertThat(row.getPrice()).isNull());
        assertThat(secDataService.findByDate(CUT_OFF_DATE))
                .isNotEmpty()
                .allSatisfy(row -> assertThat(row.getPrice()).isNotNull());
    }

    @Test
    void reimportingReplacesThepreviousDataInsteadOfAppending() throws IOException {
        importTaskFile(DataType.COMPOSITION, "composition.csv");
        long afterFirst = compositionService.count();

        importTaskFile(DataType.COMPOSITION, "composition.csv");

        assertThat(compositionService.count()).isEqualTo(afterFirst);
    }

    /**
     * The supplied files are written with a byte order mark. Left in the stream
     * it becomes part of the first header name and every lookup of that column
     * fails, so it has to be consumed before the CSV parser sees it.
     */
    @Test
    void acceptsAFileStartingWithAByteOrderMark() {
        InputStream withBom = new ByteArrayInputStream(
                ("﻿" + "id\n7\n").getBytes(StandardCharsets.UTF_8));

        ImportResult result = service.initialize(DataType.COMPOSITION, withBom);

        assertThat(result.importedRows()).isEqualTo(1);
    }

    @Test
    void rejectsAnUnexpectedHeader() {
        assertThatThrownBy(() -> service.initialize(DataType.COMPOSITION, reader("company\n1\n")))
                .isInstanceOf(CsvValidationException.class)
                .satisfies(thrown -> assertThat(errorsOf(thrown))
                        .anySatisfy(error -> assertThat(error.message()).contains("expected header")));
    }

    @Test
    void rejectsAnUnparseableNumberAndNamesTheLine() {
        assertThatThrownBy(() -> service.initialize(DataType.COMPOSITION, reader("id\n1\nabc\n")))
                .isInstanceOf(CsvValidationException.class)
                .satisfies(thrown -> assertThat(errorsOf(thrown))
                        .singleElement()
                        .satisfies(error -> {
                            assertThat(error.line()).isEqualTo(3);
                            assertThat(error.field()).isEqualTo("id");
                            assertThat(error.message()).contains("not a whole number");
                        }));
    }

    @Test
    void rejectsADuplicateComposition() {
        assertThatThrownBy(() -> service.initialize(DataType.COMPOSITION, reader("id\n7\n7\n")))
                .isInstanceOf(CsvValidationException.class)
                .satisfies(thrown -> assertThat(errorsOf(thrown))
                        .anySatisfy(error -> assertThat(error.message()).contains("duplicate entry 7")));
    }

    @Test
    void rejectsAFreeFloatOutsideItsRange() {
        String csv = """
                id;date;price;free_float;shares
                1;2026-09-21;10.0;1.5;100
                """;

        assertThatThrownBy(() -> service.initialize(DataType.SEC_DATA, reader(csv)))
                .isInstanceOf(CsvValidationException.class)
                .satisfies(thrown -> assertThat(errorsOf(thrown))
                        .anySatisfy(error -> {
                            assertThat(error.field()).isEqualTo("free_float");
                            assertThat(error.message()).contains("(0, 1]");
                        }));
    }

    @Test
    void reportsEveryBadRowNotJustTheFirst() {
        assertThatThrownBy(() -> service.initialize(DataType.COMPOSITION, reader("id\nx\ny\nz\n")))
                .isInstanceOf(CsvValidationException.class)
                .satisfies(thrown -> assertThat(errorsOf(thrown)).hasSize(3));
    }

    @Test
    void aBadRowRollsBackTheWholeFileAndLeavesTheEarlierImportIntact() throws IOException {
        importTaskFile(DataType.COMPOSITION, "composition.csv");
        long before = compositionService.count();
        assertThat(before).isEqualTo(20);

        // Valid rows first, then a broken one: the delete and the inserts already
        // issued must roll back together.
        assertThatThrownBy(() -> service.initialize(DataType.COMPOSITION, reader("id\n1\n2\nbroken\n")))
                .isInstanceOf(CsvValidationException.class);

        assertThat(compositionService.count()).isEqualTo(before);
    }

    @Test
    void rejectsAFileWithoutDataRows() {
        assertThatThrownBy(() -> service.initialize(DataType.COMPOSITION, reader("id\n")))
                .isInstanceOf(CsvValidationException.class)
                .satisfies(thrown -> assertThat(errorsOf(thrown))
                        .anySatisfy(error -> assertThat(error.message()).contains("no data rows")));
    }

    private ImportResult importTaskFile(DataType dataType, String fileName) throws IOException {
        Path file = TASK_DIR.resolve(fileName);
        assumeTrue(Files.exists(file), () -> "input file not available: " + file.toAbsolutePath());
        try (InputStream csv = Files.newInputStream(file)) {
            return service.initialize(dataType, csv);
        }
    }

    private static InputStream reader(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }

    private static List<RowError> errorsOf(Throwable thrown) {
        return ((CsvValidationException) thrown).getErrors();
    }
}
