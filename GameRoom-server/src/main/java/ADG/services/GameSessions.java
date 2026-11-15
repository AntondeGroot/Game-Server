package ADG.services;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import ADG.Lobby.GameDefinition;
import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import ADG.config.GamesConfig;
import ADG.config.SpriteSheetsConfig;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Starts and ends a room's game on the game's own server. The GWT RPC calls and
 * {@code /rooms/{roomId}/session} use this. A broken rule is thrown as a
 * {@link ResponseStatusException}, like in {@link RoomManager}.
 */
@Service
public class GameSessions {

  private static final Logger logger = LoggerFactory.getLogger(GameSessions.class);

  @Autowired private RoomManager rooms;
  @Autowired private RoomStore roomStore;
  @Autowired private GamesConfig gamesConfig;
  @Autowired private SpriteSheetsConfig spriteSheetsConfig;
  @Autowired private GameApi gameApi;

  @Value("${server.port:4100}")
  private int serverPort;

  /** Starts the room's game and sets the room to playing. */
  public synchronized Room start(String roomId) {
    Room room = findRoom(roomId);
    GameDefinition game = chosenGame(room);
    checkEnoughPlayers(room, game);
    String sessionId = createSession(room, game.getBaseUrl());
    try {
      addPlayers(room, game.getBaseUrl(), sessionId);
      gameApi.startSession(game.getBaseUrl(), sessionId, spriteSheetsConfig.botProfileIndices(),
          "http://localhost:" + serverPort + "/rooms/" + roomId + "/game-finished");
    } catch (RestClientException e) {
      logger.error("Error during game start: {}", e.getMessage(), e);
      deleteSessionQuietly(game.getBaseUrl(), sessionId);
      throw new ResponseStatusException(BAD_GATEWAY, GameApi.errorMessage(e));
    }
    room.setGameSessionId(sessionId);
    room.setGameBaseUrl("/" + game.getId());
    room.setStatus(GameStatus.PLAYING);
    rooms.emitRoomUpdate(roomId);
    rooms.emitLobbyUpdate();
    logger.info("Game started successfully for room: {}", roomId);
    return room;
  }

  /**
   * Ends the room's running game; nothing happens when it is not playing. The host may, and so
   * may anyone when the room lets every player set the options.
   */
  public synchronized void end(String roomId, String callerId) {
    Room room = findRoom(roomId);
    if (!room.getCreatedByUserId().equals(callerId) && !room.isAnyPlayerCanSetOptions()) {
      throw new ResponseStatusException(FORBIDDEN, "Only the host can end the game.");
    }
    if (room.getStatus() != GameStatus.PLAYING) return;
    logger.info("Game in room {} ended by a player", roomId);
    closeSession(room);
    returnToWaiting(room);
    rooms.emitLobbyUpdate();
  }

  /**
   * Best-effort removal of the room's session on the game server. A failure is not fatal: the
   * way out of a stuck game must not depend on the game answering.
   */
  void closeSession(Room room) {
    String sessionId = room.getGameSessionId();
    if (sessionId == null) return;
    gamesConfig.findById(room.getGameId()).ifPresent(game -> {
      try {
        gameApi.deleteSession(game.getBaseUrl(), sessionId);
      } catch (RestClientException e) {
        logger.warn("Failed to delete game session {}: {}", sessionId, e.getMessage());
      }
    });
  }

  /**
   * Returns the room to choosing a game. Only the game link is cleared: the room and its
   * players stay, so the same group can pick another game straight away.
   */
  void returnToWaiting(Room room) {
    room.setStatus(GameStatus.WAITING);
    room.setGameSessionId(null);
    roomStore.gameStateVersions.remove(room.getId());
    roomStore.gameStateVersionTimestamps.remove(room.getId());
    rooms.emitRoomUpdate(room.getId());
  }

  private Room findRoom(String roomId) {
    return rooms.findRoom(roomId)
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "There is no room " + roomId + "."));
  }

  private GameDefinition chosenGame(Room room) {
    if (room.getGameId() == null || room.getGameId().isEmpty()) {
      throw new ResponseStatusException(CONFLICT, "No game selected. Please select a game before starting.");
    }
    return gamesConfig.findById(room.getGameId())
        .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Unknown game: " + room.getGameId()));
  }

  private static void checkEnoughPlayers(Room room, GameDefinition game) {
    int players = room.getPlayerNames().size();
    if (players < game.getMinPlayers()) {
      throw new ResponseStatusException(CONFLICT, "Not enough players. Need at least "
          + game.getMinPlayers() + ", but only " + players + " have joined.");
    }
  }

  private String createSession(Room room, String baseUrl) {
    try {
      return gameApi.createSession(baseUrl, room.getName(), room.getPlayerNames().size(),
          room.getGameOptions());
    } catch (RestClientException e) {
      logger.error("Failed to create session: {}", e.getMessage(), e);
      throw new ResponseStatusException(BAD_GATEWAY,
          "Could not reach game server at " + baseUrl + ": " + e.getMessage());
    }
  }

  private void addPlayers(Room room, String baseUrl, String sessionId) {
    for (Map.Entry<String, String> player : room.getPlayerNames().entrySet()) {
      String playerId = player.getKey();
      gameApi.addPlayer(baseUrl, sessionId, playerId, player.getValue(),
          profilePic(room.getPlayerProfiles().get(playerId)));
    }
  }

  /** The profile picture as a number, or 0 when it is missing or not a number. */
  private static int profilePic(String profileId) {
    if (profileId == null) return 0;
    try {
      return Integer.parseInt(profileId);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private void deleteSessionQuietly(String baseUrl, String sessionId) {
    try {
      gameApi.deleteSession(baseUrl, sessionId);
    } catch (RestClientException ignored) {
      // The game cleans up abandoned sessions itself.
    }
  }
}
