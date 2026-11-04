package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.repository.ClubPaletteRepository;
import org.junit.jupiter.api.Test;

@org.springframework.boot.test.context.SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
class ClubPaletteRepositoryTests {

    @org.springframework.beans.factory.annotation.Autowired
    ClubPaletteRepository repository;

    @Test
    void palmeirasKeepsItsExactBrandColourAndItsSeparateAwayKit() throws Exception {
        var palette = repository.forTeam(98L);
        assertThat(palette.primary()).isEqualTo("#006437");
        assertThat(palette.homeAccent()).isEqualTo("#ffffff");
        assertThat(palette.homeColors()).containsExactly("#006437", "#ffffff");
        assertThat(palette.homeMethod()).isEqualTo("official");
        assertThat(palette.homeSource()).contains("palmeiras.com.br");
        assertThat(palette.secondary()).isEqualTo("#ffffff");
        assertThat(palette.awayAccent()).isNotEqualTo(palette.homeAccent());
    }

    @Test
    void unknownClubsReceiveAnExplicitlyNeutralPalette() throws Exception {
        var palette = repository.forTeam(Long.MAX_VALUE);
        assertThat(palette.homeMethod()).isEqualTo("neutral");
        assertThat(palette.homeSource()).isNull();
        assertThat(palette.homeColors()).contains(palette.primary());
    }
}
