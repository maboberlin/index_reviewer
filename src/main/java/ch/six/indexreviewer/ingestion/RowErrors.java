package ch.six.indexreviewer.ingestion;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects validation failures while a file is parsed.
 * <p>
 * Errors are gathered rather than thrown one at a time, so a caller fixing a
 * broken file sees every bad row at once instead of rediscovering them upload
 * by upload.
 */
public final class RowErrors {

    private final List<RowError> errors = new ArrayList<>();

    public void add(long line, String field, String message) {
        errors.add(new RowError(line, field, message));
    }

    public boolean isEmpty() {
        return errors.isEmpty();
    }

    public List<RowError> asList() {
        return List.copyOf(errors);
    }
}
