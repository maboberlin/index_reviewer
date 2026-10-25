package ch.six.indexreviewer.service;

import ch.six.indexreviewer.domain.IndexType;
import ch.six.indexreviewer.persistence.entity.SmiIndex;
import ch.six.indexreviewer.persistence.repository.SmiIndexRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Access to the constituents produced by a review.
 */
@Service
public class SmiIndexService {

    private final SmiIndexRepository repository;

    public SmiIndexService(SmiIndexRepository repository) {
        this.repository = repository;
    }

    /**
     * Makes {@code rows} the result for the given index, discarding whatever a
     * previous run of that same index produced. Other indices are untouched.
     */
    @Transactional
    public void replaceForIndex(IndexType name, List<SmiIndex> rows) {
        repository.deleteByIndexDefinitionName(name);
        repository.saveAll(rows);
    }

    public List<SmiIndex> findByIndex(IndexType name) {
        return repository.findByIndexDefinitionName(name);
    }

    public List<SmiIndex> findAll() {
        return repository.findAll();
    }

    public long count() {
        return repository.count();
    }
}
