package br.com.manager.service;

import br.com.manager.models.dto.*;
import br.com.manager.repository.*;
import br.com.manager.utils.PlayerNameGenerator;
import br.com.manager.utils.YouthGeneration;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JuniorPlayersService {

    private final SaveService saves;
    private final SaveRepository leagues;
    private final ManagementRepository management;
    private final JuniorPlayersRepository juniors;

    public JuniorPlayersService(
        SaveService saves,
        SaveRepository leagues,
        ManagementRepository management,
        JuniorPlayersRepository juniors
    ) {
        this.saves = saves;
        this.leagues = leagues;
        this.management = management;
        this.juniors = juniors;
    }

    @Transactional
    public List<ManagedPlayerDto> players(String id, String token) {
        saves.authorizedRoom(id, token);
        leagues.lock(id);
        var member = saves.realtimeMember(id, token.substring(7));
        var existing = juniors.players(id, member.teamId());
        if (!existing.isEmpty()) return existing;
        var main = management.players(id, member.teamId());
        int lowest = main.stream().mapToInt(ManagedPlayerDto::ability).min().orElse(60);
        double average = main.stream().mapToInt(ManagedPlayerDto::ability).average().orElse(60);
        var random = new Random();
        var talents = YouthGeneration.talents(random);
        var usedNames = new HashSet<String>();
        main.forEach(player -> usedNames.add(player.name()));
        String country = juniors.country(id, member.teamId());
        var roles = List.of(
            "GOL",
            "GOL",
            "DEF",
            "DEF",
            "DEF",
            "DEF",
            "DEF",
            "DEF",
            "MEI",
            "MEI",
            "MEI",
            "MEI",
            "MEI",
            "MEI",
            "ATA",
            "ATA",
            "ATA",
            "ATA"
        );
        var generated = new ArrayList<JuniorPlayerSeedDto>();
        for (int i = 0; i < roles.size(); i++) {
            int age = 15 + random.nextInt(4);
            var position = roles.get(i);
            var profile = YouthGeneration.profile(talents.get(i), position, age, average, lowest, random);
            generated.add(
                new JuniorPlayerSeedDto(
                    i + 1,
                    PlayerNameGenerator.next(country, random, usedNames),
                    position,
                    profile.ability(),
                    age,
                    profile.technical(),
                    talents.get(i)
                )
            );
        }
        juniors.create(id, member.teamId(), generated);
        return juniors.players(id, member.teamId());
    }
}
