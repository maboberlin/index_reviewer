package ch.six.indexreviewer.domain;

/**
 * Lifecycle state of an index review run.
 */
public enum ReviewStatus {

    NOT_STARTED,
    RUNNING,
    COMPLETED,
    FAILED
}
