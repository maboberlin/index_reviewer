package ch.six.indexreviewer.persistence.repository;

import ch.six.indexreviewer.persistence.entity.SecData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SecDataRepository extends JpaRepository<SecData, Long> {

    List<SecData> findByDate(LocalDate date);
}
