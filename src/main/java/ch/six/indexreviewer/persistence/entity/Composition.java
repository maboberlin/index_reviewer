package ch.six.indexreviewer.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Member of the current (pre-review) SMI composition.
 * <p>
 * Imported from {@code composition.csv} and used as the baseline against which
 * joiners and leavers are determined. Rows are immutable.
 */
@Entity
@Table(name = "composition")
public class Composition extends AuditedEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    protected Composition() {
        // for JPA
    }

    public Composition(Long companyId) {
        this.companyId = companyId;
    }

    public Long getCompanyId() {
        return companyId;
    }
}
