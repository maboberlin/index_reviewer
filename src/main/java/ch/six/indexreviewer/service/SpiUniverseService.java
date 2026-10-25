package ch.six.indexreviewer.service;

import ch.six.indexreviewer.persistence.entity.SpiUniverse;
import ch.six.indexreviewer.persistence.repository.SpiUniverseRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Access to the imported SPI universe.
 */
@Service
public class SpiUniverseService {

    private final SpiUniverseRepository repository;

    public SpiUniverseService(SpiUniverseRepository repository) {
        this.repository = repository;
    }

    /**
     * Makes {@code rows} the current universe, discarding the previous import.
     * <p>
     * Runs in the caller's transaction, so a later failure rolls the delete back
     * together with the insert.
     */
    public void replaceAll(List<SpiUniverse> rows) {
        repository.deleteAllInBatch();
        repository.saveAll(rows);
    }

    public List<SpiUniverse> findAll() {
        return repository.findAll();
    }

    public List<SpiUniverse> findByDate(LocalDate date) {
        return repository.findByDate(date);
    }

    public long count() {
        return repository.count();
    }
}
