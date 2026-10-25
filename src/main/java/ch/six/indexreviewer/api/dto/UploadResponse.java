package ch.six.indexreviewer.api.dto;

import ch.six.indexreviewer.domain.DataType;

/**
 * Acknowledgement of a CSV upload.
 *
 * @param dataType     type of input file that was uploaded
 * @param fileName     original name of the uploaded file
 * @param importedRows number of rows persisted
 * @param skippedRows  number of redundant rows dropped during import
 */
public record UploadResponse(DataType dataType, String fileName, long importedRows, long skippedRows) {
}
