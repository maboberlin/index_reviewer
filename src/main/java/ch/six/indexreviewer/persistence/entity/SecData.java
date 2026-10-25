package ch.six.indexreviewer.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Security reference data of a company on a given date.
 * <p>
 * Imported from {@code sec_data.csv}. Price is read on the cut-off date, shares
 * and free float on the review date; both dates are stored as separate rows.
 * Rows are immutable: a correction is a new import, never an update.
 */
@Entity
@Table(name = "sec_data")
public class SecData extends AuditedEntity {

    /**
     * Decimal columns are sized explicitly: left to itself Hibernate maps a
     * {@link BigDecimal} to {@code numeric(38,2)}, which would round a free
     * float of 0.6766 to 0.68.
     */
    public static final int PRICE_PRECISION = 19;
    public static final int PRICE_SCALE = 6;

    /** A fraction in (0, 1], so one integer digit is enough. */
    public static final int FREE_FLOAT_PRECISION = 9;
    public static final int FREE_FLOAT_SCALE = 8;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "date", nullable = false, updatable = false)
    private LocalDate date;

    @Column(name = "price", updatable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal price;

    @Column(name = "free_float", nullable = false, updatable = false,
            precision = FREE_FLOAT_PRECISION, scale = FREE_FLOAT_SCALE)
    private BigDecimal freeFloat;

    @Column(name = "shares", nullable = false, updatable = false)
    private Long shares;

    protected SecData() {
        // for JPA
    }

    public SecData(Long companyId, LocalDate date, BigDecimal price, BigDecimal freeFloat, Long shares) {
        this.companyId = companyId;
        this.date = date;
        this.price = price;
        this.freeFloat = freeFloat;
        this.shares = shares;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getFreeFloat() {
        return freeFloat;
    }

    public Long getShares() {
        return shares;
    }
}
