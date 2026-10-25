package ch.six.indexreviewer.persistence.repository;

import ch.six.indexreviewer.domain.IndexType;
import ch.six.indexreviewer.persistence.entity.Ffmcap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FfmcapRepository extends JpaRepository<Ffmcap, Long> {

    List<Ffmcap> findByIndexDefinitionName(IndexType name);

    void deleteByIndexDefinitionName(IndexType name);
}
