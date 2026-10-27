package ADG.services;

import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import ADG.generated.api.GamesApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The {@code games} part of gameroom_openapi.yaml. Operations not overridden here still answer
 * 501 Not Implemented; the GWT client uses RoomService over RPC for them until they move here.
 */
@RestController
public class GamesController implements GamesApi {

    private static final Logger logger = LoggerFactory.getLogger(GamesController.class);

    @Autowired private RoomStore roomStore;
    @Autowired private RoomSseRegistry sseRegistry;
    @Autowired private LobbySseRegistry lobbySseRegistry;

    @Override
    public ResponseEntity<Void> gameFinished(String roomId) {
        roomStore.rooms.stream()
                .filter(r -> roomId.equals(r.getId()))
                .findFirst()
                .ifPresent(room -> {
                    logger.info("Game-finished callback received for room {}, resetting to WAITING", roomId);
                    room.setStatus(GameStatus.WAITING);
                    room.setGameSessionId(null);
                    sseRegistry.emit(roomId, room);
                    emitLobbyUpdate();
                });
        return ResponseEntity.noContent().build();
    }

    private void emitLobbyUpdate() {
        List<Room> visible = roomStore.rooms.stream()
                .filter(r -> r.getStatus() != GameStatus.PENDING)
                .collect(Collectors.toList());
        lobbySseRegistry.emit(visible);
    }
}