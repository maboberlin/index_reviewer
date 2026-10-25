package ch.six.indexreviewer.review;

import ch.six.indexreviewer.config.IndexParametersRegistry;
import ch.six.indexreviewer.config.IndexReviewProperties.IndexParameters;
import ch.six.indexreviewer.domain.IndexType;
import ch.six.indexreviewer.persistence.entity.Composition;
import ch.six.indexreviewer.persistence.entity.Ffmcap;
import ch.six.indexreviewer.persistence.entity.IndexDefinition;
import ch.six.indexreviewer.persistence.entity.SmiIndex;
import ch.six.indexreviewer.service.CompositionService;
import ch.six.indexreviewer.service.FfmcapService;
import ch.six.indexreviewer.service.IndexDefinitionService;
import ch.six.indexreviewer.service.SmiIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Review of the SMI for Q3 2026.
 * <p>
 * Reads its parameters once, when the bean is created, so a run never depends
 * on configuration being re-resolved mid-flight.
 */
@Component
public class SmiIndexReviewer implements Reviewer {

    private static final Logger log = LoggerFactory.getLogger(SmiIndexReviewer.class);

    private final IndexParameters parameters;
    private final FfmcapCalculator ffmcapCalculator;
    private final ConstituentSelector constituentSelector;
    private final WeightCalculator weightCalculator;
    private final FfmcapService ffmcapService;
    private final SmiIndexService smiIndexService;
    private final CompositionService compositionService;
    private final IndexDefinitionService indexDefinitionService;

    public SmiIndexReviewer(IndexParametersRegistry parametersRegistry,
                            FfmcapCalculator ffmcapCalculator,
                            ConstituentSelector constituentSelector,
                            WeightCalculator weightCalculator,
                            FfmcapService ffmcapService,
                            SmiIndexService smiIndexService,
                            CompositionService compositionService,
                            IndexDefinitionService indexDefinitionService) {
        this.parameters = parametersRegistry.forType(supportedType());
        this.ffmcapCalculator = ffmcapCalculator;
        this.constituentSelector = constituentSelector;
        this.weightCalculator = weightCalculator;
        this.ffmcapService = ffmcapService;
        this.smiIndexService = smiIndexService;
        this.compositionService = compositionService;
        this.indexDefinitionService = indexDefinitionService;
    }

    @Override
    public IndexType supportedType() {
        return IndexType.SMI_INDEX_REVIEW_Q3_2026;
    }

    /**
     * One transaction for the whole review: a failure anywhere leaves the
     * previous result untouched rather than a half-rebuilt index. The caller
     * holds the process lock around this call, so the lock is still held when
     * the transaction commits.
     */
    @Override
    @Transactional
    public void review(IndexType indexType) {
        log.info("Reviewing {} with cut-off {} and review date {}",
                supportedType(), parameters.cutOffDate(), parameters.reviewDate());

        IndexDefinition definition = indexDefinitionService.getByName(indexType);

        Map<Long, BigDecimal> ffmcapByCompany = calculateFfmcapData();
        storeFfmcap(definition, ffmcapByCompany);

        List<Long> constituents = selectConstituents(ffmcapByCompany);
        Map<Long, ConstituentWeights> weights = calculateWeights(constituents, ffmcapByCompany);
        storeConstituents(definition, constituents, weights);

        log.info("Review of {} completed with {} constituents", supportedType(), constituents.size());
    }

    private Map<Long, BigDecimal> calculateFfmcapData() {
        FfmcapCalculator.EligibilityResult eligibility =
                ffmcapCalculator.calculate(parameters.cutOffDate(), parameters.reviewDate());
        return eligibility.ffmcapByCompany();
    }

    private List<Long> selectConstituents(Map<Long, BigDecimal> ffmcapByCompany) {
        List<RankedCompany> ranked = constituentSelector.rank(ffmcapByCompany);
        Set<Long> currentComposition = currentComposition();
        return constituentSelector.select(
                ranked, currentComposition, parameters.targetSize(), parameters.selectionBuffer());
    }

    private Map<Long, ConstituentWeights> calculateWeights(List<Long> constituents, Map<Long, BigDecimal> ffmcapByCompany) {
        Map<Long, BigDecimal> selectedFfmcap = new LinkedHashMap<>();
        constituents.forEach(companyId -> selectedFfmcap.put(companyId, ffmcapByCompany.get(companyId)));
        return weightCalculator.calculate(selectedFfmcap, parameters.maxWeight());
    }

    private void storeFfmcap(IndexDefinition definition, Map<Long, BigDecimal> ffmcapByCompany) {
        List<Ffmcap> rows = ffmcapByCompany.entrySet().stream()
                .map(entry -> new Ffmcap(definition, entry.getKey(), entry.getValue()))
                .toList();
        ffmcapService.replaceForIndex(supportedType(), rows);
    }

    private void storeConstituents(IndexDefinition definition,
                                   List<Long> constituents,
                                   Map<Long, ConstituentWeights> weights) {
        List<SmiIndex> rows = constituents.stream()
                .map(companyId -> {
                    ConstituentWeights weight = weights.get(companyId);
                    SmiIndex row = new SmiIndex(definition, companyId);
                    row.setWeight(weight.raw());
                    row.setCappedWeight(weight.capped());
                    row.setFinalWeight(weight.result());
                    return row;
                })
                .toList();
        smiIndexService.replaceForIndex(supportedType(), rows);
    }

    private Set<Long> currentComposition() {
        return compositionService.findAll().stream()
                .map(Composition::getCompanyId)
                .collect(Collectors.toSet());
    }
}
