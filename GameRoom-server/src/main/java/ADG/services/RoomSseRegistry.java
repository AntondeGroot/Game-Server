package ADG.services;

import ADG.Lobby.Room;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class RoomSseRegistry {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> rooms = new ConcurrentHashMap<>();
    // Which player opened each stream, so every frame can say who has the room page open.
    private final Map<SseEmitter, String> playerIdByEmitter = new ConcurrentHashMap<>();

    @Autowired
    private RoomStore roomStore;

    SseEmitter subscribe(String roomId, String playerId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        CopyOnWriteArrayList<SseEmitter> emitters =
                rooms.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);
        if (playerId != null) playerIdByEmitter.put(emitter, playerId);
        emitter.onCompletion(() -> remove(roomId, emitter));
        emitter.onTimeout(() -> remove(roomId, emitter));
        emitter.onError(e -> remove(roomId, emitter));
        return emitter;
    }

    /**
     * Whether the given room currently has at least one live SSE connection.
     * The heartbeat prunes dead emitters, so an empty (or absent) list means
     * nobody is connected to this room right now.
     */
    public boolean hasSubscribers(String roomId) {
        List<SseEmitter> emitters = rooms.get(roomId);
        return emitters != null && !emitters.isEmpty();
    }

    public void emit(String roomId, Room room) {
        List<SseEmitter> emitters = rooms.get(roomId);
        if (emitters == null) return;
        RoomStreamEvent event = new RoomStreamEvent(room, presentPlayerIds(emitters));
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(event, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        if (!dead.isEmpty()) prune(roomId, emitters, dead);
    }

    public void emitClosed(String roomId) {
        List<SseEmitter> emitters = rooms.remove(roomId);
        if (emitters == null) return;
        for (SseEmitter emitter : emitters) {
            playerIdByEmitter.remove(emitter);
            try {
                emitter.send(SseEmitter.event().name("stream_closed").data(""));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }
    }

    // A closed tab is usually only noticed when a write fails, so this interval
    // is also how long it takes for a player to show up as absent.
    @Scheduled(fixedDelay = 10_000)
    public void heartbeat() {
        for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : rooms.entrySet()) {
            List<SseEmitter> emitters = entry.getValue();
            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    dead.add(emitter);
                }
            }
            if (!dead.isEmpty()) prune(entry.getKey(), emitters, dead);
        }
    }

    private void remove(String roomId, SseEmitter emitter) {
        List<SseEmitter> emitters = rooms.get(roomId);
        if (emitters != null && emitters.contains(emitter)) {
            prune(roomId, emitters, List.of(emitter));
        } else {
            playerIdByEmitter.remove(emitter);
        }
    }

    /** Drops dead streams and tells the remaining ones that those players left. */
    private void prune(String roomId, List<SseEmitter> emitters, List<SseEmitter> dead) {
        emitters.removeAll(dead);
        dead.forEach(playerIdByEmitter::remove);
        if (emitters.isEmpty()) return;
        roomStore.rooms.stream()
                .filter(r -> r.getId().equals(roomId))
                .findFirst()
                .ifPresent(room -> emit(roomId, room));
    }

    private Set<String> presentPlayerIds(List<SseEmitter> emitters) {
        Set<String> present = new HashSet<>();
        for (SseEmitter emitter : emitters) {
            String playerId = playerIdByEmitter.get(emitter);
            if (playerId != null) present.add(playerId);
        }
        return present;
    }
}
