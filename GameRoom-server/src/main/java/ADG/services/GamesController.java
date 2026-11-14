package ADG.services;

import ADG.Lobby.GameDefinition;
import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import ADG.generated.api.GamesApi;
import ADG.generated.model.GameDto;
import jakarta.servlet.http.HttpServletRequest;
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
    @Autowired private GameCatalogService gameCatalog;

    /** A proxy to the current request, for the language cookie. */
    @Autowired private HttpServletRequest request;

    @Override
    public ResponseEntity<List<GameDto>> getGames() {
        String language = ClientCookies.language(request);
        return ResponseEntity.ok(gameCatalog.getAvailableGames(language).stream()
                .map(GamesController::toDto)
                .toList());
    }

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

    private static GameDto toDto(GameDefinition game) {
        return new GameDto()
                .id(game.getId())
                .name(game.getName())
                .minPlayers(game.getMinPlayers())
                .maxPlayers(game.getMaxPlayers())
                .embeddedSettings(game.isEmbeddedSettings());
    }

    private void emitLobbyUpdate() {
        List<Room> visible = roomStore.rooms.stream()
                .filter(r -> r.getStatus() != GameStatus.PENDING)
                .collect(Collectors.toList());
        lobbySseRegistry.emit(visible);
    }
}