package ch.six.indexreviewer.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Free float market capitalization computed for one company in one review run.
 * <p>
 * This table is the ranking input: every eligible company of the universe gets
 * a row, whether or not it ends up in the index. What happens to the ones that
 * do - selection and weighting - lives in {@link SmiIndex}, so this entity
 * stays a record of the measurement rather than of the decision.
 */
@Entity
@Table(name = "ffmcap")
public class Ffmcap extends AuditedEntity {

    /**
     * Market capitalizations reach the order of 10^11, so the default scale of
     * a {@link BigDecimal} column is nowhere near enough - it has to be stated.
     */
    public static final int FFMCAP_PRECISION = 38;
    public static final int FFMCAP_SCALE = 6;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "ffmcap", precision = FFMCAP_PRECISION, scale = FFMCAP_SCALE)
    private BigDecimal ffmcap;

    /**
     * Lazy on purpose: a review loads hundreds of these rows at once and the
     * eager default would fetch the same parent over and over.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "index_definition_id", nullable = false, updatable = false)
    private IndexDefinition indexDefinition;

    protected Ffmcap() {
        // for JPA
    }

    public Ffmcap(IndexDefinition indexDefinition, Long companyId, BigDecimal ffmcap) {
        this.indexDefinition = indexDefinition;
        this.companyId = companyId;
        this.ffmcap = ffmcap;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public IndexDefinition getIndexDefinition() {
        return indexDefinition;
    }

    public BigDecimal getFfmcap() {
        return ffmcap;
    }

    public void setFfmcap(BigDecimal ffmcap) {
        this.ffmcap = ffmcap;
    }
}
