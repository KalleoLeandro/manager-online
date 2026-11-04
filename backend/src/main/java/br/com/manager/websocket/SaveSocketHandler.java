package br.com.manager.websocket;

import br.com.manager.models.dto.PresenceDto;
import br.com.manager.models.events.LeagueDeletedEvent;
import br.com.manager.models.events.SaveChangedEvent;
import br.com.manager.models.events.TeamUpdatedEvent;
import br.com.manager.models.responses.LeagueRoomResponse;
import br.com.manager.models.responses.RealtimeSnapshotResponse;
import br.com.manager.service.SaveService;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SaveSocketHandler extends TextWebSocketHandler {

    private final SaveService saves;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private static final Set<String> ACTIVITIES = Set.of("SELECTING_TEAM", "VIEWING_ROSTER", "LOBBY", "MANAGING_TEAM");

    private static final class Client {

        final WebSocketSession socket;
        volatile long lastSeen = System.currentTimeMillis();
        Long teamId;
        String saveId,
            memberId,
            name = "Convidado",
            activity = "SELECTING_TEAM";

        Client(WebSocketSession socket) {
            this.socket = new ConcurrentWebSocketSessionDecorator(socket, 5000, 128 * 1024);
        }
    }

    private static final class Channel {

        LeagueRoomResponse room;
        final Map<Long, String> teams;
        final Map<String, Client> clients = new LinkedHashMap<>();
        long revision = 0;

        Channel(LeagueRoomResponse room, Map<Long, String> teams) {
            this.room = room;
            this.teams = teams;
        }
    }

    public SaveSocketHandler(SaveService saves) {
        this.saves = saves;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(4096);
        clients.put(session.getId(), new Client(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        var client = clients.get(session.getId());
        if (client == null) return;
        client.lastSeen = System.currentTimeMillis();
        try {
            JsonNode input = json.readTree(message.getPayload());
            var type = text(input, "type");
            if ("subscribe".equals(type) && client.saveId == null) subscribe(client, input);
            else if ("ping".equals(type) && client.saveId != null) send(client, Map.of("type", "pong"));
            else if ("presence".equals(type) && client.saveId != null) {
                var channel = channels.get(client.saveId);
                synchronized (channel) {
                    updatePresence(client, channel, input);
                    broadcast(channel);
                }
            } else throw new IllegalArgumentException("Mensagem não permitida.");
        } catch (Exception e) {
            send(
                client,
                Map.of(
                    "type",
                    "ERROR",
                    "message",
                    "Não foi possível acessar o canal da liga. Confira o convite ou seu acesso."
                )
            );
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    private void subscribe(Client client, JsonNode input) throws IOException {
        LeagueRoomResponse room;
        var token = text(input, "accessToken");
        if (token != null && !token.isBlank()) {
            var id = text(input, "saveId");
            var member = saves.realtimeMember(id, token);
            var renew = input.get("renewActivity");
            room =
                renew != null && renew.isBoolean() && !renew.asBoolean()
                    ? saves.room(id)
                    : saves.authorizedRoom(id, "Bearer " + token);
            client.memberId = member.id();
            client.name = member.name();
            client.teamId = member.teamId();
            client.activity = "LOBBY";
        } else {
            var code = text(input, "inviteCode");
            if (code == null || code.length() != 8) throw new IllegalArgumentException();
            room = saves.byCode(code);
        }
        final var saveId = room.id();
        channels.compute(saveId, (id, existing) -> {
            var channel = existing == null ? new Channel(saves.room(id), saves.selectionTeams(id)) : existing;
            synchronized (channel) {
                // Lê novamente após entrar no canal para não perder um commit ocorrido durante a inscrição.
                channel.room = saves.room(room.id());
                client.saveId = room.id();
                updatePresence(client, channel, input);
                channel.clients.put(client.socket.getId(), client);
                send(
                    client,
                    Map.of(
                        "type",
                        "SUBSCRIBED",
                        "presenceId",
                        client.memberId == null ? client.socket.getId() : client.memberId
                    )
                );
                broadcast(channel);
            }
            return channel;
        });
    }

    private void updatePresence(Client client, Channel channel, JsonNode input) {
        if (client.memberId == null) {
            var name = text(input, "name");
            if (name != null) client.name = name.strip().isEmpty()
                ? "Convidado"
                : name.strip().substring(0, Math.min(40, name.strip().length()));
            var teamNode = input.get("teamId");
            Long teamId =
                teamNode != null && teamNode.isIntegralNumber() && teamNode.canConvertToLong()
                    ? teamNode.longValue()
                    : null;
            client.teamId = teamId != null && channel.teams.containsKey(teamId) ? teamId : null;
        }
        var activity = text(input, "activity");
        if (activity != null && ACTIVITIES.contains(activity)) {
            client.activity =
                client.memberId == null && !Set.of("SELECTING_TEAM", "VIEWING_ROSTER").contains(activity)
                    ? "SELECTING_TEAM"
                    : activity;
        }
    }

    private List<PresenceDto> presence(Channel channel) {
        var result = new ArrayList<PresenceDto>();
        var identities = new HashSet<String>();
        for (var client : channel.clients.values()) {
            var id = client.memberId == null ? client.socket.getId() : client.memberId;
            if (!identities.add(id)) continue;
            var member = channel.room
                .members()
                .stream()
                .filter(m -> m.id().equals(client.memberId))
                .findFirst();
            var activity = member.isPresent() && member.get().ready() ? "READY" : client.activity;
            result.add(
                new PresenceDto(
                    id,
                    client.memberId,
                    client.name,
                    client.teamId,
                    channel.teams.get(client.teamId),
                    activity
                )
            );
        }
        return result;
    }

    private void broadcast(Channel channel) {
        var payload = new RealtimeSnapshotResponse(
            "SNAPSHOT",
            ++channel.revision,
            channel.room,
            presence(channel),
            System.currentTimeMillis()
        );
        for (var client : List.copyOf(channel.clients.values())) send(client, payload);
    }

    private void send(Client client, Object payload) {
        try {
            if (client.socket.isOpen()) client.socket.sendMessage(new TextMessage(json.writeValueAsString(payload)));
        } catch (Exception e) {
            try {
                client.socket.close(CloseStatus.SERVER_ERROR);
            } catch (IOException ignored) {}
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void teamUpdated(TeamUpdatedEvent event) {
        deliverGameEvent(event.saveId(), "TEAM_UPDATED", Map.of("teamId", event.teamId(), "version", event.version()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void committed(SaveChangedEvent event) {
        var channel = channels.get(event.saveId());
        if (channel == null) return;
        synchronized (channel) {
            try {
                channel.room = saves.room(event.saveId());
            } catch (br.com.manager.exception.DefaultErrorException missing) {
                if (missing.getStatusCode().value() == 404) return;
                throw missing;
            }
            broadcast(channel);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deleted(LeagueDeletedEvent event) {
        var channel = channels.remove(event.saveId());
        if (channel == null) return;
        synchronized (channel) {
            for (var client : List.copyOf(channel.clients.values())) {
                send(
                    client,
                    Map.of(
                        "type",
                        "LEAGUE_DELETED",
                        "reason",
                        event.reason(),
                        "message",
                        "INACTIVE".equals(event.reason())
                            ? "Esta liga foi apagada após mais de 60 dias sem atividade."
                            : "Esta liga foi excluída pelo criador."
                    )
                );
                clients.remove(client.socket.getId());
                client.saveId = null;
                try {
                    client.socket.close(CloseStatus.POLICY_VIOLATION);
                } catch (IOException ignored) {}
            }
            channel.clients.clear();
        }
    }

    /** Extensão para eventos de rodada, mercado e partida; nunca encaminha dados privados a visitantes. */
    public void publishGameEvent(String saveId, String eventType, Object payload) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deliverGameEvent(saveId, eventType, payload);
                    }
                }
            );
        } else deliverGameEvent(saveId, eventType, payload);
    }

    private void deliverGameEvent(String saveId, String eventType, Object payload) {
        var channel = channels.get(saveId);
        if (channel == null) return;
        synchronized (channel) {
            var event = Map.of(
                "type",
                "GAME_EVENT",
                "revision",
                ++channel.revision,
                "event",
                eventType,
                "payload",
                payload,
                "serverTime",
                System.currentTimeMillis()
            );
            for (var client : channel.clients.values()) if (client.memberId != null) send(client, event);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        var client = clients.remove(session.getId());
        if (client == null || client.saveId == null) return;
        var channel = channels.get(client.saveId);
        if (channel == null) return;
        synchronized (channel) {
            channel.clients.remove(session.getId());
            if (!channel.clients.isEmpty()) broadcast(channel);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable error) throws Exception {
        session.close(CloseStatus.SERVER_ERROR);
    }

    @Scheduled(fixedDelay = 15000)
    public void expireConnections() {
        var now = System.currentTimeMillis();
        for (var client : clients.values()) {
            if (now - client.lastSeen > (client.saveId == null ? 10000 : 75000)) {
                try {
                    client.socket.close(CloseStatus.SESSION_NOT_RELIABLE);
                } catch (IOException ignored) {}
            }
        }
        for (var id : channels.keySet())
            channels.computeIfPresent(id, (key, channel) -> {
                synchronized (channel) {
                    return channel.clients.isEmpty() ? null : channel;
                }
            });
    }

    private String text(JsonNode input, String name) {
        var value = input.get(name);
        return value != null && value.isString() ? value.asString() : null;
    }
}
