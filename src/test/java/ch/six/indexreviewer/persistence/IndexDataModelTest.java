package ch.six.indexreviewer.persistence;

import ch.six.indexreviewer.domain.IndexType;
import ch.six.indexreviewer.domain.ReviewStatus;
import ch.six.indexreviewer.persistence.entity.Ffmcap;
import ch.six.indexreviewer.persistence.entity.IndexDefinition;
import ch.six.indexreviewer.persistence.entity.SmiIndex;
import ch.six.indexreviewer.service.FfmcapService;
import ch.six.indexreviewer.service.IndexDefinitionService;
import ch.six.indexreviewer.service.SmiIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @Transactional} both rolls each test back and keeps a session open:
 * the associations to {@link IndexDefinition} are lazy and open-session-in-view
 * is disabled, so traversing one outside a transaction fails by design. The
 * review logic runs inside a transaction for the same reason.
 */
@SpringBootTest
@Transactional
class IndexDataModelTest {

    @Autowired
    private IndexDefinitionService indexDefinitionService;
    @Autowired
    private FfmcapService ffmcapService;
    @Autowired
    private SmiIndexService smiIndexService;

    @Test
    void seedsOneDefinitionPerIndexTypeAtStartUp() {
        assertThat(indexDefinitionService.count()).isEqualTo(IndexType.values().length);
        for (IndexType type : IndexType.values()) {
            assertThat(indexDefinitionService.findByName(type))
                    .hasValueSatisfying(definition -> {
                        assertThat(definition.getStatus()).isEqualTo(ReviewStatus.NOT_STARTED);
                        assertThat(definition.getLastProcessed()).isNull();
                        assertThat(definition.getCreatedAt()).isNotNull();
                    });
        }
    }

    @Test
    void seedingTwiceDoesNotCreateDuplicates() {
        long before = indexDefinitionService.count();

        indexDefinitionService.createMissingDefinitions();

        assertThat(indexDefinitionService.count()).isEqualTo(before);
    }

    @Test
    void persistsFfmcapAgainstItsIndexDefinition() {
        IndexDefinition definition = indexDefinitionService.getByName(IndexType.SMI_INDEX_REVIEW_Q3_2026);

        ffmcapService.replaceForIndex(IndexType.SMI_INDEX_REVIEW_Q3_2026,
                List.of(new Ffmcap(definition, 42L, new BigDecimal("123456789.123456"))));

        List<Ffmcap> stored = ffmcapService.findByIndex(IndexType.SMI_INDEX_REVIEW_Q3_2026);
        assertThat(stored).singleElement().satisfies(row -> {
            assertThat(row.getCompanyId()).isEqualTo(42L);
            assertThat(row.getFfmcap()).isEqualByComparingTo(new BigDecimal("123456789.123456"));
            assertThat(row.getIndexDefinition().getName()).isEqualTo(IndexType.SMI_INDEX_REVIEW_Q3_2026);
        });
    }

    @Test
    void persistsConstituentWeightsWithoutRounding() {
        IndexDefinition definition = indexDefinitionService.getByName(IndexType.SMI_INDEX_REVIEW_Q3_2026);
        SmiIndex constituent = new SmiIndex(definition, 7L);
        constituent.setWeight(new BigDecimal("0.201234567891"));
        constituent.setCappedWeight(new BigDecimal("0.180000000000"));
        constituent.setFinalWeight(new BigDecimal("0.185432109876"));

        smiIndexService.replaceForIndex(IndexType.SMI_INDEX_REVIEW_Q3_2026, List.of(constituent));

        assertThat(smiIndexService.findByIndex(IndexType.SMI_INDEX_REVIEW_Q3_2026))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getWeight()).isEqualByComparingTo(new BigDecimal("0.201234567891"));
                    assertThat(row.getCappedWeight()).isEqualByComparingTo(new BigDecimal("0.18"));
                    assertThat(row.getFinalWeight()).isEqualByComparingTo(new BigDecimal("0.185432109876"));
                });
    }

    @Test
    void replacingOneIndexResultDoesNotTouchAnother() {
        IndexDefinition definition = indexDefinitionService.getByName(IndexType.SMI_INDEX_REVIEW_Q3_2026);

        ffmcapService.replaceForIndex(IndexType.SMI_INDEX_REVIEW_Q3_2026,
                List.of(new Ffmcap(definition, 1L, BigDecimal.TEN)));
        ffmcapService.replaceForIndex(IndexType.SMI_INDEX_REVIEW_Q3_2026,
                List.of(new Ffmcap(definition, 2L, BigDecimal.ONE)));

        // The second run replaced the first rather than appending to it.
        assertThat(ffmcapService.findByIndex(IndexType.SMI_INDEX_REVIEW_Q3_2026))
                .singleElement()
                .satisfies(row -> assertThat(row.getCompanyId()).isEqualTo(2L));
    }

    @Test
    void tracksReviewStatusAndLastProcessed() {
        OffsetDateTime now = OffsetDateTime.now();

        indexDefinitionService.updateStatus(IndexType.SMI_INDEX_REVIEW_Q3_2026, ReviewStatus.COMPLETED, now);

        assertThat(indexDefinitionService.getByName(IndexType.SMI_INDEX_REVIEW_Q3_2026))
                .satisfies(definition -> {
                    assertThat(definition.getStatus()).isEqualTo(ReviewStatus.COMPLETED);
                    assertThat(definition.getLastProcessed()).isNotNull();
                });
    }
}
