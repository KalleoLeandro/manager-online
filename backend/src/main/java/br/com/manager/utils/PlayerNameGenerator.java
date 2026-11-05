package br.com.manager.utils;

import java.text.Normalizer;
import java.util.*;
import java.util.random.RandomGenerator;

/** Shared by youth intake, replacement players and future retirement/recruitment processing. */
public final class PlayerNameGenerator {

    /** Optional offline catalog-import CLI; uses exactly the same generator as youth intake. */
    public static void main(String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("Uso: PlayerNameGenerator país quantidade semente");
        int count = Integer.parseInt(args[1]);
        if (count < 1 || count > 20000) throw new IllegalArgumentException("Quantidade deve ficar entre 1 e 20000.");
        var random = new Random(Long.parseLong(args[2]));
        var names = new HashSet<String>();
        for (int i = 0; i < count; i++) System.out.println(next(args[0], random, names));
    }

    private PlayerNameGenerator() {}

    private record Pool(String[] first, String[] family) {}

    private static Pool pool(String first, String family) {
        return new Pool(first.split("\\|"), family.split("\\|"));
    }

    private static final Pool PORTUGUESE = pool(
        "Lucas|Gabriel|João|Pedro|Miguel|Arthur|Davi|Rafael|Matheus|Gustavo|Felipe|Bruno|Caio|Vitor|André|Eduardo|Henrique|Leonardo|Thiago|Diego|Samuel|Daniel|Nicolas|Vinícius|Murilo|Heitor|Luiz|Otávio|Breno|Renan|Igor|Wesley|Danilo|Marcos|Hugo|Alexandre",
        "Silva|Santos|Oliveira|Souza|Costa|Pereira|Ferreira|Almeida|Rodrigues|Lima|Carvalho|Ribeiro|Gomes|Martins|Araújo|Melo|Barbosa|Rocha|Dias|Teixeira|Mendes|Castro|Nunes|Moreira|Cardoso|Correia|Monteiro|Moura|Azevedo|Cavalcanti|Farias|Andrade|Pinto|Tavares|Vieira|Borges|Machado|Duarte|Freitas|Campos"
    );
    private static final Pool SPANISH = pool(
        "Mateo|Santiago|Diego|Alejandro|Pablo|Javier|Álvaro|Sergio|Nicolás|Martín|Adrián|Juan|Carlos|Luis|Andrés|Fernando|Emilio|Raúl|Rubén|Hugo|Iker|Joaquín|Agustín|Tomás|Lautaro|Facundo|Gonzalo|Rodrigo|Matías|Ezequiel",
        "García|Rodríguez|González|Fernández|López|Martínez|Sánchez|Pérez|Gómez|Díaz|Hernández|Torres|Ruiz|Romero|Suárez|Moreno|Alonso|Navarro|Ramos|Ortega|Castillo|Molina|Vega|Rojas|Medina|Silva|Acosta|Benítez|Vargas|Cabrera"
    );
    private static final Pool ENGLISH = pool(
        "Oliver|Harry|Jack|George|Noah|Oscar|Leo|James|William|Thomas|Henry|Charlie|Joshua|Daniel|Samuel|Benjamin|Jacob|Alexander|Luke|Joseph|Matthew|Ethan|Liam|Nathan|Aaron|Callum|Lewis|Owen|Connor|Tyler",
        "Smith|Jones|Taylor|Brown|Williams|Wilson|Johnson|Davies|Robinson|Wright|Thompson|Evans|Walker|White|Roberts|Green|Hall|Wood|Jackson|Clarke|Harris|Cooper|King|Baker|Hill|Scott|Moore|Allen|Young|Turner"
    );
    private static final Pool ITALIAN = pool(
        "Lorenzo|Matteo|Leonardo|Francesco|Alessandro|Andrea|Marco|Luca|Davide|Federico|Giovanni|Riccardo|Niccolò|Simone|Filippo|Edoardo|Antonio|Gabriele|Pietro|Tommaso|Giulio|Enrico|Paolo|Stefano",
        "Rossi|Russo|Ferrari|Esposito|Bianchi|Romano|Colombo|Ricci|Marino|Greco|Bruno|Gallo|Conti|De Luca|Mancini|Costa|Giordano|Rizzo|Lombardi|Moretti|Barbieri|Fontana|Caruso|Santoro"
    );
    private static final Pool FRENCH = pool(
        "Louis|Gabriel|Jules|Arthur|Raphaël|Lucas|Hugo|Adam|Léo|Nathan|Antoine|Maxime|Théo|Julien|Alexandre|Clément|Paul|Mathis|Baptiste|Rayan|Yanis|Ibrahim|Moussa|Amadou",
        "Martin|Bernard|Thomas|Petit|Robert|Richard|Durand|Dubois|Moreau|Laurent|Simon|Michel|Lefebvre|Leroy|Roux|David|Bertrand|Girard|Bonnet|François|Diop|Diallo|Traoré|Konaté"
    );
    private static final Pool GERMAN = pool(
        "Leon|Paul|Jonas|Finn|Elias|Noah|Felix|Lukas|Maximilian|Ben|Jakob|Tim|Jan|Luca|Julian|Florian|Niklas|Moritz|David|Anton|Emil|Theo|Hannes|Fabian",
        "Müller|Schmidt|Schneider|Fischer|Weber|Meyer|Wagner|Becker|Schulz|Hoffmann|Koch|Richter|Wolf|Schröder|Neumann|Schwarz|Zimmermann|Braun|Krüger|Hofmann|Hartmann|Lange|Klein|Werner"
    );

    private static String normalize(String country) {
        return Normalizer.normalize(country == null ? "" : country, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }

    public static String next(String country, RandomGenerator random, Set<String> reserved) {
        var p = switch (normalize(country)) {
            case "brasil", "brazil", "bra", "portugal", "por" -> PORTUGUESE;
            case
                "espanha",
                "spain",
                "esp",
                "argentina",
                "arg",
                "uruguai",
                "uruguay",
                "uru",
                "chile",
                "chi",
                "colombia",
                "col",
                "paraguai",
                "paraguay",
                "par",
                "equador",
                "ecuador",
                "ecu",
                "peru",
                "per",
                "venezuela",
                "ven",
                "bolivia",
                "bol",
                "mexico",
                "mex" -> SPANISH;
            case "italia", "italy", "ita" -> ITALIAN;
            case
                "franca",
                "france",
                "fra",
                "senegal",
                "sen",
                "costa do marfim",
                "civ",
                "mali",
                "mli",
                "camaroes",
                "cameroon",
                "cmr" -> FRENCH;
            case "alemanha", "germany", "ger", "austria", "aut", "suica", "switzerland", "sui" -> GERMAN;
            default -> ENGLISH;
        };
        for (int attempt = 0; attempt < 10000; attempt++) {
            String first = p.first[random.nextInt(p.first.length)],
                family = p.family[random.nextInt(p.family.length)];
            String name = first + " " + family;
            if (attempt > 100 || random.nextInt(4) == 0) {
                String second;
                do {
                    second = p.family[random.nextInt(p.family.length)];
                } while (second.equals(family));
                name = first + " " + family + " " + second;
            }
            if (reserved.add(name)) return name;
        }
        throw new IllegalStateException("Não foi possível gerar um nome distinto nesta população.");
    }
}
