package ch.six.indexreviewer.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Membership of a company in the SPI universe on a given date.
 * <p>
 * Imported from {@code spi_universe.csv}. Rows are immutable: a correction is a
 * new import, never an update.
 */
@Entity
@Table(name = "spi_universe")
public class SpiUniverse extends AuditedEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "date", nullable = false, updatable = false)
    private LocalDate date;

    protected SpiUniverse() {
        // for JPA
    }

    public SpiUniverse(Long companyId, LocalDate date) {
        this.companyId = companyId;
        this.date = date;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public LocalDate getDate() {
        return date;
    }
}
