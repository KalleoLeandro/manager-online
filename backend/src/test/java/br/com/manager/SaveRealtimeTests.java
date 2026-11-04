package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.models.requests.CreateLeagueRequest;
import br.com.manager.models.requests.JoinLeagueRequest;
import br.com.manager.models.requests.UpdateTeamRequest;
import br.com.manager.models.responses.LeagueAccessResponse;
import br.com.manager.service.CatalogService;
import br.com.manager.service.ManagementService;
import br.com.manager.service.SaveService;
import br.com.manager.websocket.SaveSocketHandler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SaveRealtimeTests {

    @Autowired
    ManagementService management;

    @Value("${local.server.port}")
    int port;

    @Autowired
    SaveService saves;

    @Autowired
    CatalogService catalog;

    @Autowired
    JdbcTemplate db;

    @Autowired
    SaveSocketHandler hub;

    @Autowired
    PlatformTransactionManager transactions;

    final JsonMapper json = JsonMapper.builder().build();

    private LeagueAccessResponse create() {
        return saves.create(new CreateLeagueRequest("Tempo real", 2, "Criador", catalog.teams(null).getFirst().id()));
    }

    private void cleanup(String id) {
        db.update("DELETE FROM save_lineups WHERE save_id=?", id);
        db.update("DELETE FROM save_team_management WHERE save_id=?", id);
        db.update("DELETE FROM save_members WHERE save_id=?", id);
        db.update("DELETE FROM save_players WHERE save_id=?", id);
        db.update("DELETE FROM save_teams WHERE save_id=?", id);
        db.update("DELETE FROM save_roster_policy_leagues WHERE save_id=?", id);
        db.update("DELETE FROM save_roster_policies WHERE save_id=?", id);
        db.update("DELETE FROM game_saves WHERE id=?", id);
    }

    private final class Peer implements WebSocket.Listener, AutoCloseable {

        final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        final StringBuilder text = new StringBuilder();
        final WebSocket socket;

        Peer() {
            socket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/saves"), this)
                .join();
        }

        @Override
        public void onOpen(WebSocket socket) {
            socket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                messages.add(json.readTree(text.toString()));
                text.setLength(0);
            }
            socket.request(1);
            return null;
        }

        void send(Object value) {
            socket.sendText(json.writeValueAsString(value), true).join();
        }

        JsonNode await(Predicate<JsonNode> condition) throws Exception {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < until) {
                var value = messages.poll(Math.max(1, until - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (value != null && condition.test(value)) return value;
            }
            throw new AssertionError("Evento WebSocket esperado não foi recebido.");
        }

        JsonNode snapshot() throws Exception {
            return await(n -> "SNAPSHOT".equals(n.get("type").asString()));
        }

        @Override
        public void close() {
            if (!socket.isOutputClosed()) {
                try {
                    socket.sendClose(WebSocket.NORMAL_CLOSURE, "test complete").join();
                } catch (CompletionException alreadyClosed) {
                    socket.abort();
                }
            }
        }
    }

    private void subscribe(Peer peer, LeagueAccessResponse access) {
        peer.send(Map.of("type", "subscribe", "saveId", access.room().id(), "accessToken", access.accessToken()));
    }

    @Test
    void heartbeatsPresenceGuestsAndAutomaticReconnectDoNotExtendRetention() throws Exception {
        var owner = create();
        var id = owner.room().id();
        var old = java.sql.Timestamp.from(java.time.Instant.parse("2000-01-01T00:00:00Z"));
        try (var member = new Peer(); var guest = new Peer(); var reconnect = new Peer()) {
            subscribe(member, owner);
            member.snapshot();
            db.update("UPDATE game_saves SET last_activity_at=? WHERE id=?", old, id);
            member.send(Map.of("type", "ping"));
            member.await(n -> "pong".equals(n.path("type").asString()));
            member.send(Map.of("type", "presence", "activity", "MANAGING_TEAM"));
            member.snapshot();
            guest.send(Map.of("type", "subscribe", "inviteCode", owner.room().inviteCode()));
            guest.snapshot();
            reconnect.send(
                Map.of("type", "subscribe", "saveId", id, "accessToken", owner.accessToken(), "renewActivity", false)
            );
            reconnect.snapshot();
            assertThat(
                db.queryForObject("SELECT last_activity_at FROM game_saves WHERE id=?", java.sql.Timestamp.class, id)
            ).isEqualTo(old);
        } finally {
            cleanup(id);
        }
    }

    @Test
    void committedDeletionClosesMembersAndGuestsButRollbackDoesNotNotify() throws Exception {
        var owner = create();
        var id = owner.room().id();
        try (var member = new Peer(); var guest = new Peer()) {
            subscribe(member, owner);
            member.snapshot();
            guest.send(Map.of("type", "subscribe", "inviteCode", owner.room().inviteCode()));
            guest.snapshot();
            var transaction = new TransactionTemplate(transactions);
            transaction.executeWithoutResult(status -> {
                saves.delete(id, "Bearer " + owner.accessToken());
                status.setRollbackOnly();
            });
            assertThat(saves.room(id).id()).isEqualTo(id);
            saves.delete(id, "Bearer " + owner.accessToken());
            assertThat(
                member
                    .await(n -> "LEAGUE_DELETED".equals(n.path("type").asString()))
                    .path("reason")
                    .asString()
            ).isEqualTo("DELETED_BY_OWNER");
            assertThat(
                guest
                    .await(n -> "LEAGUE_DELETED".equals(n.path("type").asString()))
                    .path("reason")
                    .asString()
            ).isEqualTo("DELETED_BY_OWNER");
        } finally {
            cleanup(id);
        }
    }

    @Test
    void presenceAndCommittedRoomUpdatesAreDeliveredWithoutPolling() throws Exception {
        var owner = create();
        var id = owner.room().id();
        try (var a = new Peer(); var guest = new Peer()) {
            subscribe(a, owner);
            a.snapshot();
            guest.send(
                Map.of(
                    "type",
                    "subscribe",
                    "inviteCode",
                    owner.room().inviteCode(),
                    "name",
                    "Amigo",
                    "activity",
                    "SELECTING_TEAM"
                )
            );
            guest.snapshot();
            a.await(n -> "SNAPSHOT".equals(n.get("type").asString()) && n.get("presence").size() == 2);
            var team = catalog.teams(null).get(1).id();
            guest.send(Map.of("type", "presence", "name", "Amigo", "teamId", team, "activity", "VIEWING_ROSTER"));
            var preview = a.await(
                n ->
                    "SNAPSHOT".equals(n.get("type").asString()) &&
                    n.get("presence").toString().contains("VIEWING_ROSTER")
            );
            assertThat(preview.get("room").get("members").size()).isEqualTo(1);
            var selecting = java.util.stream.StreamSupport.stream(preview.path("presence").spliterator(), false)
                .filter(p -> "Amigo".equals(p.path("name").asString()))
                .findFirst()
                .orElseThrow();
            assertThat(selecting.path("teamId").isIntegralNumber()).isTrue();
            assertThat(selecting.path("teamId").asLong()).isEqualTo(team);
            assertThat(selecting.path("teamName").asString()).isNotBlank();
            saves.join(owner.room().inviteCode(), new JoinLeagueRequest("Amigo", team));
            var updated = a.await(
                n -> "SNAPSHOT".equals(n.get("type").asString()) && n.get("room").get("members").size() == 2
            );
            assertThat(updated.get("revision").asLong()).isGreaterThan(preview.get("revision").asLong());
        } finally {
            cleanup(id);
        }
    }

    @Test
    void privateGameEventsAreScopedToMembersAndRollbackDoesNotBroadcast() throws Exception {
        var owner = create();
        var id = owner.room().id();
        try (var a = new Peer(); var guest = new Peer()) {
            subscribe(a, owner);
            a.snapshot();
            guest.send(Map.of("type", "subscribe", "inviteCode", owner.room().inviteCode()));
            guest.snapshot();
            a.await(n -> "SNAPSHOT".equals(n.get("type").asString()) && n.get("presence").size() == 2);
            hub.publishGameEvent(id, "ROUND_UPDATED", Map.of("round", 2));
            assertThat(
                a
                    .await(n -> "GAME_EVENT".equals(n.get("type").asString()))
                    .get("event")
                    .asString()
            ).isEqualTo("ROUND_UPDATED");
            assertThat(guest.messages.poll(250, TimeUnit.MILLISECONDS)).isNull();
            new TransactionTemplate(transactions).execute(status -> {
                saves.ready(id, "Bearer " + owner.accessToken(), true);
                hub.publishGameEvent(id, "ROUND_UPDATED", Map.of("round", 999));
                status.setRollbackOnly();
                return null;
            });
            assertThat(a.messages.poll(250, TimeUnit.MILLISECONDS)).isNull();
            hub.publishGameEvent("outro-save", "ROUND_UPDATED", Map.of("round", 3));
            assertThat(a.messages.poll(250, TimeUnit.MILLISECONDS)).isNull();
        } finally {
            cleanup(id);
        }
    }

    @Test
    void teamManagementEventsArePublishedOnlyAfterCommitAndOnlyToMembers() throws Exception {
        var owner = create();
        var id = owner.room().id();
        var token = "Bearer " + owner.accessToken();
        var state = management.state(id, token);
        var update = new UpdateTeamRequest(state.formation(), "ATTACKING", state.lineup(), 0);
        try (var member = new Peer(); var guest = new Peer()) {
            subscribe(member, owner);
            member.snapshot();
            guest.send(Map.of("type", "subscribe", "inviteCode", owner.room().inviteCode()));
            guest.snapshot();
            member.await(n -> "SNAPSHOT".equals(n.path("type").asString()) && n.path("presence").size() == 2);
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                management.update(id, token, update);
                status.setRollbackOnly();
            });
            assertThat(member.messages.poll(250, TimeUnit.MILLISECONDS)).isNull();
            management.update(id, token, update);
            var event = member.await(n -> "GAME_EVENT".equals(n.path("type").asString()));
            assertThat(event.path("event").asString()).isEqualTo("TEAM_UPDATED");
            assertThat(event.path("payload").path("version").asInt()).isEqualTo(1);
            assertThat(guest.messages.poll(250, TimeUnit.MILLISECONDS)).isNull();
        } finally {
            cleanup(id);
        }
    }

    @Test
    void invalidMemberTokenIsRejectedAndReconnectResynchronizes() throws Exception {
        var owner = create();
        var id = owner.room().id();
        try (var invalid = new Peer()) {
            invalid.send(Map.of("type", "subscribe", "saveId", id, "accessToken", "invalido"));
            assertThat(invalid.await(n -> "ERROR".equals(n.get("type").asString()))).isNotNull();
        }
        try (var reconnected = new Peer()) {
            saves.ready(id, "Bearer " + owner.accessToken(), true);
            subscribe(reconnected, owner);
            assertThat(reconnected.snapshot().get("room").get("members").get(0).get("ready").asBoolean()).isTrue();
        } finally {
            cleanup(id);
        }
    }
}
