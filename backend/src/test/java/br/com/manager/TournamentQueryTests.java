package br.com.manager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.manager.models.dto.TournamentDtos.Edition;
import br.com.manager.repository.TournamentRepository;
import br.com.manager.service.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class TournamentQueryTests {

    @Test
    void existingCupListAuthenticatesWithoutTakingTheWorldSimulationLock() {
        var saves = mock(SaveService.class);
        var repository = mock(TournamentRepository.class);
        var world = mock(WorldCompetitionService.class);
        var editions = List.of(mock(Edition.class));
        when(repository.editions("league")).thenReturn(editions);
        assertThat(new TournamentQueryService(saves, repository, world).list("league", "Bearer test")).isSameAs(
            editions
        );
        verify(saves).summary("league", "Bearer test");
        verifyNoInteractions(world);
    }

    @Test
    void missingEditionsAreInitializedBeforeReturningTheList() {
        var saves = mock(SaveService.class);
        var repository = mock(TournamentRepository.class);
        var world = mock(WorldCompetitionService.class);
        var editions = List.of(mock(Edition.class));
        when(repository.editions("league")).thenReturn(List.of(), editions);
        assertThat(new TournamentQueryService(saves, repository, world).list("league", "Bearer test")).isSameAs(
            editions
        );
        verify(world).synchronize("league");
    }
}
