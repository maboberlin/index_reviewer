package ch.six.indexreviewer.review.result;

import ch.six.indexreviewer.api.dto.ConstituentDto;
import ch.six.indexreviewer.api.dto.IndexReviewResponse;
import ch.six.indexreviewer.domain.IndexType;
import ch.six.indexreviewer.domain.ReviewStatus;
import ch.six.indexreviewer.persistence.entity.Composition;
import ch.six.indexreviewer.persistence.entity.SmiIndex;
import ch.six.indexreviewer.service.CompositionService;
import ch.six.indexreviewer.service.IndexDefinitionService;
import ch.six.indexreviewer.service.SmiIndexService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the SMI review report: constituents with their weights, plus the
 * joiners and leavers against the composition the review started from.
 */
@Component
public class SmiReviewResultAssembler implements ReviewResultAssembler {

    private final SmiIndexService smiIndexService;
    private final CompositionService compositionService;
    private final IndexDefinitionService indexDefinitionService;

    public SmiReviewResultAssembler(SmiIndexService smiIndexService,
                                    CompositionService compositionService,
                                    IndexDefinitionService indexDefinitionService) {
        this.smiIndexService = smiIndexService;
        this.compositionService = compositionService;
        this.indexDefinitionService = indexDefinitionService;
    }

    @Override
    public IndexType supportedType() {
        return IndexType.SMI_INDEX_REVIEW_Q3_2026;
    }

    @Override
    @Transactional(readOnly = true)
    public IndexReviewResponse assemble(IndexType indexType) {
        ReviewStatus status = indexDefinitionService.getByName(indexType).getStatus();

        List<SmiIndex> constituents = smiIndexService.findByIndex(indexType).stream()
                .sorted(Comparator.comparing(SmiIndex::getFinalWeight,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        if (constituents.isEmpty()) {
            // Nothing has been computed yet, so there is nothing to compare against
            // either - reporting the whole composition as leavers would be wrong.
            return IndexReviewResponse.of(status);
        }

        List<ConstituentDto> constituentDtos = constituents.stream()
                .map(row -> new ConstituentDto(asId(row.getCompanyId()), weightOf(row)))
                .toList();

        Set<Long> newMembers = constituents.stream()
                .map(SmiIndex::getCompanyId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> previousMembers = compositionService.findAll().stream()
                .map(Composition::getCompanyId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> joiners = newMembers.stream()
                .filter(companyId -> !previousMembers.contains(companyId))
                .sorted()
                .map(SmiReviewResultAssembler::asId)
                .toList();

        List<String> leavers = previousMembers.stream()
                .filter(companyId -> !newMembers.contains(companyId))
                .sorted()
                .map(SmiReviewResultAssembler::asId)
                .toList();

        return new IndexReviewResponse(constituentDtos, joiners, leavers, status);
    }

    private static BigDecimal weightOf(SmiIndex row) {
        return row.getFinalWeight() == null ? BigDecimal.ZERO : row.getFinalWeight();
    }

    private static String asId(Long companyId) {
        return String.valueOf(companyId);
    }
}
