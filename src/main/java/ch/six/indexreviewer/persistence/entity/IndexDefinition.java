package ch.six.indexreviewer.persistence.entity;

import ch.six.indexreviewer.domain.IndexType;
import ch.six.indexreviewer.domain.ReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * One reviewable index and the state of its review.
 * <p>
 * Exactly one row exists per {@link IndexType}; the rows are created at start
 * up. Everything a review produces - the {@link Ffmcap} working records and the
 * resulting {@link SmiIndex} constituents - points back here, so a run is
 * always attributable to an index and a point in time.
 */
@Entity
@Table(name = "index_definition")
public class IndexDefinition extends AuditedEntity {

    /**
     * Stored by name, not by ordinal. The JPA default is {@code ORDINAL}, which
     * silently remaps every existing row the moment a constant is inserted or
     * reordered in the enum.
     * <p>
     * Written as {@code varchar} rather than the native {@code enum} column
     * Hibernate would otherwise emit: adding an index must be a change to the
     * enum alone, not a change to the column type as well.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "name", nullable = false, updatable = false, unique = true, length = 64)
    private IndexType name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 32)
    private ReviewStatus status;

    /** When the last review finished; null until one has run. */
    @Column(name = "last_processed")
    private OffsetDateTime lastProcessed;

    protected IndexDefinition() {
        // for JPA
    }

    public IndexDefinition(IndexType name) {
        this.name = name;
        this.status = ReviewStatus.NOT_STARTED;
    }

    public IndexType getName() {
        return name;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public OffsetDateTime getLastProcessed() {
        return lastProcessed;
    }

    public void setLastProcessed(OffsetDateTime lastProcessed) {
        this.lastProcessed = lastProcessed;
    }
}
