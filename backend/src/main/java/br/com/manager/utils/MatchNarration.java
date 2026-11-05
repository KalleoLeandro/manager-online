package br.com.manager.utils;

import br.com.manager.models.dto.MatchStateDto;
import br.com.manager.models.dto.MatchStateDto.*;
import java.util.List;

/** Text and reading cadence are independent from the engine's outcome random stream. */
public final class MatchNarration {

    private MatchNarration() {}

    public static String exchange(MatchStateDto match, Player passer, Player receiver, Team team) {
        if (passer.id == receiver.id) return (
            passer.name +
            " participa da troca de passes de " +
            team.name +
            ", recebe a devolução e procura espaço para dar sequência ao ataque."
        );
        return switch (Math.floorMod(match.seed + match.minute + match.period, 3)) {
            case 0 -> passer.name +
                " inicia a circulação de bola de " +
                team.name +
                ". Após a troca de passes, " +
                receiver.name +
                " recebe e procura uma opção para avançar, com a defesa fechando os espaços.";
            case 1 -> team.name +
                " trabalha a posse a partir de " +
                passer.name +
                ". A bola passa pelos companheiros e chega a " +
                receiver.name +
                ", que domina e observa a movimentação à frente.";
            default -> passer.name +
                " ajuda " +
                team.name +
                " a sair da pressão com passes curtos. " +
                receiver.name +
                " fica com a bola na sequência e tenta encontrar uma brecha na marcação.";
        };
    }

    public static String shot(Player shooter, Player provider, boolean corner, boolean freeKick, boolean longShot) {
        String build =
            provider == null
                ? shooter.name + " prepara a finalização"
                : provider.name +
                  (corner ? " cobra o escanteio em direção a " : " encontra uma linha de passe para ") +
                  shooter.name;
        return (
            build +
            (corner
                ? ". A disputa é pelo alto: " + shooter.name + " tenta o cabeceio em direção ao gol!"
                : freeKick
                  ? ". A barreira se posiciona e o cobrador bate a falta direto para o gol!"
                  : longShot
                    ? ". Há espaço fora da área e o chute sai de longa distância!"
                    : ". A marcação tenta se recompor, mas o arremate já vai em direção ao gol!")
        );
    }

    public static long readingDelay(long base, List<Event> events) {
        long characters = events
            .stream()
            .mapToLong(e -> e.text().length())
            .sum();
        return Math.max(base, Math.min(16000L, characters * 25L));
    }
}
