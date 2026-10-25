package ch.six.indexreviewer.ingestion;

import ch.six.indexreviewer.domain.DataType;

import java.io.InputStream;

/**
 * Imports one kind of CSV input file: validate, then insert.
 * <p>
 * One implementation per {@link DataType}. Implementations are picked up by
 * {@link DataProcessorRegistry} through component scanning, so supporting a
 * further input file means adding a class and nothing else.
 */
public interface DataProcessor {

    /**
     * The data type this processor handles. Must be unique across all processors.
     */
    DataType supportedType();

    /**
     * Validates and persists the whole file as one unit: either every row is
     * stored or, if any row is rejected, none is.
     *
     * @throws CsvValidationException if the header or any row is invalid
     */
    ImportResult process(InputStream csv);
}
