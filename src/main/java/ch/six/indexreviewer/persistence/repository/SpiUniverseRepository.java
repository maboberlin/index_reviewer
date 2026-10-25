package ch.six.indexreviewer.persistence.repository;

import ch.six.indexreviewer.persistence.entity.SpiUniverse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SpiUniverseRepository extends JpaRepository<SpiUniverse, Long> {

    List<SpiUniverse> findByDate(LocalDate date);
}
