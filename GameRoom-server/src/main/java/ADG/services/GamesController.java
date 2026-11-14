package ADG.services;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import ADG.Lobby.GameDefinition;
import ADG.Lobby.GameOption;
import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import ADG.generated.api.GamesApi;
import ADG.generated.model.GameDto;
import ADG.generated.model.GameOptionDto;
import ADG.generated.model.RoomDto;
import ADG.generated.model.SelectGameRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
    @Autowired private RoomManager rooms;

    /** A proxy to the current request, for the language cookie and the admin role. */
    @Autowired private HttpServletRequest request;

    @Override
    public ResponseEntity<List<GameDto>> getGames() {
        String language = ClientCookies.language(request);
        return ResponseEntity.ok(gameCatalog.getAvailableGames(language).stream()
                .map(GamesController::toDto)
                .toList());
    }

    @Override
    public ResponseEntity<List<GameOptionDto>> getGameOptions(String gameId) {
        String language = ClientCookies.language(request);
        boolean isAdmin = request.isUserInRole("ADMIN");
        return gameCatalog.optionsFor(gameId, language, isAdmin)
                .map(options -> ResponseEntity.ok(options.stream().map(GamesController::toDto).toList()))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "There is no game " + gameId + "."));
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

    @Override
    public ResponseEntity<RoomDto> selectGame(String roomId, SelectGameRequestDto body) {
        String callerId = ClientCookies.playerId(request);
        Room room = rooms.selectGame(roomId, callerId, body.getGameId());
        return ResponseEntity.ok(RoomDtos.detail(room, callerId));
    }

    private static GameDto toDto(GameDefinition game) {
        return new GameDto()
                .id(game.getId())
                .name(game.getName())
                .minPlayers(game.getMinPlayers())
                .maxPlayers(game.getMaxPlayers())
                .embeddedSettings(game.isEmbeddedSettings());
    }

    private static GameOptionDto toDto(GameOption option) {
        return new GameOptionDto()
                .key(option.getKey())
                .label(option.getLabel())
                .description(option.getDescription())
                .labelKey(option.getLabelKey())
                .descriptionKey(option.getDescriptionKey())
                .type(option.getType())
                .defaultValue(option.getDefaultValue())
                .choices(option.getChoices())
                .minValue(option.getMinValue())
                .maxValue(option.getMaxValue())
                .incompatibleWith(option.getIncompatibleWith());
    }

    private void emitLobbyUpdate() {
        List<Room> visible = roomStore.rooms.stream()
                .filter(r -> r.getStatus() != GameStatus.PENDING)
                .collect(Collectors.toList());
        lobbySseRegistry.emit(visible);
    }
}