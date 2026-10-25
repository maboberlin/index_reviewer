package ch.six.indexreviewer.persistence.repository;

import ch.six.indexreviewer.domain.IndexType;
import ch.six.indexreviewer.persistence.entity.SmiIndex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SmiIndexRepository extends JpaRepository<SmiIndex, Long> {

    List<SmiIndex> findByIndexDefinitionName(IndexType name);

    void deleteByIndexDefinitionName(IndexType name);
}
