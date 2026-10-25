package ch.six.indexreviewer.service;

import ch.six.indexreviewer.persistence.entity.Composition;
import ch.six.indexreviewer.persistence.repository.CompositionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Access to the current (pre-review) index composition.
 */
@Service
public class CompositionService {

    private final CompositionRepository repository;

    public CompositionService(CompositionRepository repository) {
        this.repository = repository;
    }

    /**
     * Makes {@code rows} the current composition, discarding the previous import.
     */
    public void replaceAll(List<Composition> rows) {
        repository.deleteAllInBatch();
        repository.saveAll(rows);
    }

    public List<Composition> findAll() {
        return repository.findAll();
    }

    public long count() {
        return repository.count();
    }
}
