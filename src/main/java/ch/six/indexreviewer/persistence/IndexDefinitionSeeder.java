package ch.six.indexreviewer.persistence;

import ch.six.indexreviewer.service.IndexDefinitionService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Makes sure every {@link ch.six.indexreviewer.domain.IndexType} has a row in
 * {@code index_definition} once the application is up.
 * <p>
 * The schema is recreated on every start, so the seeding has to happen on every
 * start too. Keeping it here rather than in a {@code data.sql} means adding an
 * index is a change to the enum alone.
 */
@Component
public class IndexDefinitionSeeder implements ApplicationRunner {

    private final IndexDefinitionService indexDefinitionService;

    public IndexDefinitionSeeder(IndexDefinitionService indexDefinitionService) {
        this.indexDefinitionService = indexDefinitionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        indexDefinitionService.createMissingDefinitions();
    }
}
