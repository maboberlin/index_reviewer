package ch.six.indexreviewer.service;

import ch.six.indexreviewer.persistence.entity.SecData;
import ch.six.indexreviewer.persistence.repository.SecDataRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Access to the imported security reference data.
 */
@Service
public class SecDataService {

    private final SecDataRepository repository;

    public SecDataService(SecDataRepository repository) {
        this.repository = repository;
    }

    /**
     * Makes {@code rows} the current security data, discarding the previous import.
     */
    public void replaceAll(List<SecData> rows) {
        repository.deleteAllInBatch();
        repository.saveAll(rows);
    }

    public List<SecData> findAll() {
        return repository.findAll();
    }

    public List<SecData> findByDate(LocalDate date) {
        return repository.findByDate(date);
    }

    public long count() {
        return repository.count();
    }
}
