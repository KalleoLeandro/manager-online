package br.com.manager.service;

import br.com.manager.models.dto.RosterPolicyDto;
import br.com.manager.models.dto.CatalogMetadataDto;
import br.com.manager.repository.CatalogMigrationRepository;
import br.com.manager.repository.RosterPolicyRepository;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CatalogBootstrapService implements ApplicationRunner {

    private final CatalogMigrationRepository repository;
    private final RosterPolicyRepository policies;
    private final boolean dropLegacyColumn;
    private final PlayerProfilesService profiles;

    public CatalogBootstrapService(
        CatalogMigrationRepository repository,
        RosterPolicyRepository policies,
        PlayerProfilesService profiles,
        @Value("${app.catalog.drop-legacy-column:true}") boolean dropLegacyColumn
    ) {
        this.repository = repository;
        this.policies = policies;
        this.profiles = profiles;
        this.dropLegacyColumn = dropLegacyColumn;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        repository.requireNumericIds();
        var legacy = repository.legacyCatalog();
        if (!repository.initialized()) {
            if (repository.empty()) repository.executeSeed("catalog-seed.sql");
            repository.executeSeed("catalog-reference.sql");
            if (legacy.isPresent()) {
                policies.importCatalog(
                    Arrays.asList(
                        JsonMapper.builder().build().readValue(legacy.get().rosterPolicies(), RosterPolicyDto[].class)
                    )
                );
                repository.metadata(legacy.get().metadata());
            } else {
                repository.executeSeed("catalog-policies.sql");
                repository.executeSeed("catalog-metadata.sql");
                if (!repository.initialized()) repository.metadata(
                    new CatalogMetadataDto("local", "Catálogo local", "Definido pelo responsável pela instalação")
                );
            }
        }
        for (var save : repository.legacySavePolicies())
            policies.importSave(
                save.saveId(),
                Arrays.asList(JsonMapper.builder().build().readValue(save.rosterPolicies(), RosterPolicyDto[].class))
            );
        repository.removeLegacy(dropLegacyColumn);
        profiles.initialize();
    }
}
