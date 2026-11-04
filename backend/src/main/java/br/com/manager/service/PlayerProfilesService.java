package br.com.manager.service;

import br.com.manager.exception.DefaultErrorException;
import br.com.manager.repository.CatalogMigrationRepository;
import br.com.manager.repository.PlayerProfilesRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerProfilesService {

    private final PlayerProfilesRepository repository;
    private final CatalogMigrationRepository migration;

    public PlayerProfilesService(PlayerProfilesRepository repository, CatalogMigrationRepository migration) {
        this.repository = repository;
        this.migration = migration;
    }

    @Transactional
    public void initialize() {
        if (repository.missingProfiles()) {
            migration.executeSeed("player-profiles-estimated.sql");
            migration.executeSeed("player-profiles-source.sql");
        }
    }

    public void requireAvailableStarters(String saveId, String memberId) {
        if (!repository.unavailableStarters(saveId, memberId).isEmpty()) throw new DefaultErrorException(
            HttpStatus.CONFLICT,
            "Há titulares lesionados ou sem condição de jogo. Ajuste a escalação antes de confirmar."
        );
    }
}
