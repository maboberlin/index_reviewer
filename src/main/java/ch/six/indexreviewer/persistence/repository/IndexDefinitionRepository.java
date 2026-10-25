package ch.six.indexreviewer.persistence.repository;

import ch.six.indexreviewer.domain.IndexType;
import ch.six.indexreviewer.persistence.entity.IndexDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IndexDefinitionRepository extends JpaRepository<IndexDefinition, Long> {

    Optional<IndexDefinition> findByName(IndexType name);

    boolean existsByName(IndexType name);
}
