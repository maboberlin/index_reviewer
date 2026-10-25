package ch.six.indexreviewer.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * A constituent of the reviewed index, with the weights computed for it.
 * <p>
 * Only companies that made the selection get a row here, which is why
 * {@link Ffmcap} no longer carries a {@code selected} flag: membership of this
 * table <em>is</em> the selection. The three weights are kept side by side so
 * the capping is auditable - raw weight, the weight after the 18% cap, and the
 * final weight once the excess has been redistributed.
 */
@Entity
@Table(name = "smi_index")
public class SmiIndex extends AuditedEntity {

    /** Weights are fractions of 1; capping and redistribution need headroom. */
    public static final int WEIGHT_PRECISION = 19;
    public static final int WEIGHT_SCALE = 12;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "weight", precision = WEIGHT_PRECISION, scale = WEIGHT_SCALE)
    private BigDecimal weight;

    @Column(name = "capped_weight", precision = WEIGHT_PRECISION, scale = WEIGHT_SCALE)
    private BigDecimal cappedWeight;

    @Column(name = "final_weight", precision = WEIGHT_PRECISION, scale = WEIGHT_SCALE)
    private BigDecimal finalWeight;

    /**
     * Lazy on purpose: a review loads hundreds of these rows at once and the
     * eager default would fetch the same parent over and over.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "index_definition_id", nullable = false, updatable = false)
    private IndexDefinition indexDefinition;

    protected SmiIndex() {
        // for JPA
    }

    public SmiIndex(IndexDefinition indexDefinition, Long companyId) {
        this.indexDefinition = indexDefinition;
        this.companyId = companyId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public IndexDefinition getIndexDefinition() {
        return indexDefinition;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public BigDecimal getCappedWeight() {
        return cappedWeight;
    }

    public void setCappedWeight(BigDecimal cappedWeight) {
        this.cappedWeight = cappedWeight;
    }

    public BigDecimal getFinalWeight() {
        return finalWeight;
    }

    public void setFinalWeight(BigDecimal finalWeight) {
        this.finalWeight = finalWeight;
    }
}
