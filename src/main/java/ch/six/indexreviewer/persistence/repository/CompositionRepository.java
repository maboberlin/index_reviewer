package ch.six.indexreviewer.persistence.repository;

import ch.six.indexreviewer.persistence.entity.Composition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompositionRepository extends JpaRepository<Composition, Long> {
}
