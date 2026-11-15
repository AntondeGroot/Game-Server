package ADG.services;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import ADG.Lobby.GameDefinition;
import ADG.Lobby.GameOption;
import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import ADG.config.GamesConfig;
import ADG.config.SpriteSheetsConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Starts a room's game on the game's own server: creates a session, adds the players, and
 * starts it. Both the GWT RPC call and {@code POST /rooms/{roomId}/session} use this. A broken
 * rule is thrown as a {@link ResponseStatusException}, like in {@link RoomManager}.
 */
@Service
public class GameSessions {

  private static final Logger logger = LoggerFactory.getLogger(GameSessions.class);

  @Autowired private RoomManager rooms;
  @Autowired private GamesConfig gamesConfig;
  @Autowired private SpriteSheetsConfig spriteSheetsConfig;

  @Value("${server.port:4100}")
  private int serverPort;

  RestTemplate restTemplate = new RestTemplate();

  /** Starts the room's game and sets the room to playing. */
  public synchronized Room start(String roomId) {
    Room room = rooms.findRoom(roomId)
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "There is no room " + roomId + "."));
    GameDefinition game = chosenGame(room);
    checkEnoughPlayers(room, game);
    String sessionId = createSession(room, game.getBaseUrl());
    try {
      addPlayers(room, game.getBaseUrl(), sessionId);
      startSession(roomId, game.getBaseUrl(), sessionId);
    } catch (RestClientException e) {
      logger.error("Error during game start: {}", e.getMessage(), e);
      deleteSession(game.getBaseUrl(), sessionId);
      throw new ResponseStatusException(BAD_GATEWAY, gameErrorMessage(e));
    }
    room.setGameSessionId(sessionId);
    room.setGameBaseUrl("/" + game.getId());
    room.setStatus(GameStatus.PLAYING);
    rooms.emitRoomUpdate(roomId);
    rooms.emitLobbyUpdate();
    logger.info("Game started successfully for room: {}", roomId);
    return room;
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

  /**
   * POSTs {baseUrl}/games and returns the new session's id. When the room has no options (the
   * options dialog was never opened), the game's own defaults are sent, so a game never starts
   * with a blank configuration.
   */
  private String createSession(Room room, String baseUrl) {
    HashMap<String, String> options = room.getGameOptions();
    if (options.isEmpty()) {
      options = defaultOptions(baseUrl);
    }
    Map<String, Object> request = new HashMap<>();
    request.put("roomName", room.getName());
    request.put("maxPlayers", room.getPlayerNames().size());
    if (!options.isEmpty()) {
      request.put("gameOptions", GameOptionValues.parse(options));
    }
    try {
      Map<?, ?> response = restTemplate.postForObject(baseUrl + "/games", request, Map.class);
      return response.get("sessionId").toString();
    } catch (RestClientException e) {
      logger.error("Failed to create session: {}", e.getMessage(), e);
      throw new ResponseStatusException(BAD_GATEWAY,
          "Could not reach game server at " + baseUrl + ": " + e.getMessage());
    }
  }

  private void addPlayers(Room room, String baseUrl, String sessionId) {
    for (Map.Entry<String, String> player : room.getPlayerNames().entrySet()) {
      Map<String, Object> request = new HashMap<>();
      request.put("id", player.getKey());
      request.put("name", player.getValue());
      request.put("profilePic", profilePic(room.getPlayerProfiles().get(player.getKey())));
      restTemplate.postForObject(baseUrl + "/games/" + sessionId + "/players", request, Map.class);
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

  /**
   * POSTs {baseUrl}/games/{sessionId} with a unique robot picture for each bot and the URL the
   * game calls when everyone has left.
   */
  private void startSession(String roomId, String baseUrl, String sessionId) {
    Map<String, Object> request = new HashMap<>();
    request.put("botProfilePics", spriteSheetsConfig.botProfileIndices());
    request.put("callbackUrl", "http://localhost:" + serverPort + "/rooms/" + roomId + "/game-finished");
    restTemplate.postForObject(baseUrl + "/games/" + sessionId, request, Void.class);
  }

  /** Best-effort removal of a session that could not be started. */
  private void deleteSession(String baseUrl, String sessionId) {
    try {
      restTemplate.delete(baseUrl + "/games/" + sessionId);
    } catch (RestClientException ignored) {
      // The game cleans up abandoned sessions itself.
    }
  }

  /**
   * The default value of each of the game's options ({baseUrl}/game-options), or none when the
   * game cannot say.
   */
  private HashMap<String, String> defaultOptions(String baseUrl) {
    try {
      GameOption[] options = restTemplate.getForObject(baseUrl + "/game-options", GameOption[].class);
      HashMap<String, String> defaults = new HashMap<>();
      if (options == null) return defaults;
      for (GameOption option : options) {
        if (option.getDefaultValue() != null) {
          defaults.put(option.getKey(), option.getDefaultValue());
        }
      }
      return defaults;
    } catch (Exception e) {
      logger.warn("Could not fetch default game options from {}: {}", baseUrl, e.getMessage());
      return new HashMap<>();
    }
  }

  /** The game's own "message" for a 4xx answer, so the player sees why it refused. */
  private static String gameErrorMessage(RestClientException e) {
    if (e instanceof HttpClientErrorException clientError) {
      try {
        Map<?, ?> body = new ObjectMapper().readValue(clientError.getResponseBodyAsString(), Map.class);
        Object message = body.get("message");
        if (message != null) return message.toString();
      } catch (Exception ignored) {
        // Not JSON; fall back to the status text.
      }
      return clientError.getStatusText();
    }
    return e.getMessage();
  }
}
