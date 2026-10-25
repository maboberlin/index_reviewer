package ch.six.indexreviewer.review;

import java.math.BigDecimal;

/**
 * An eligible company and the market capitalization it is ranked by.
 *
 * @param rank 1-based position by descending free float market capitalization
 */
public record RankedCompany(int rank, Long companyId, BigDecimal ffmcap) {
}
