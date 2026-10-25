package ch.six.indexreviewer.service;

import ch.six.indexreviewer.domain.IndexType;
import ch.six.indexreviewer.persistence.entity.Ffmcap;
import ch.six.indexreviewer.persistence.repository.FfmcapRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Access to the free float market capitalizations computed by a review.
 * <p>
 * Unlike the three import services this one is not fed by an upload; it is
 * filled by the review itself, which is implemented in the next step.
 */
@Service
public class FfmcapService {

    private final FfmcapRepository repository;

    public FfmcapService(FfmcapRepository repository) {
        this.repository = repository;
    }

    /**
     * Makes {@code rows} the ranking input for the given index, discarding
     * whatever a previous run of that same index produced. Other indices are
     * untouched.
     */
    @Transactional
    public void replaceForIndex(IndexType name, List<Ffmcap> rows) {
        repository.deleteByIndexDefinitionName(name);
        repository.saveAll(rows);
    }

    public List<Ffmcap> findByIndex(IndexType name) {
        return repository.findByIndexDefinitionName(name);
    }

    public List<Ffmcap> saveAll(List<Ffmcap> rows) {
        return repository.saveAll(rows);
    }

    public List<Ffmcap> findAll() {
        return repository.findAll();
    }

    public long count() {
        return repository.count();
    }
}
