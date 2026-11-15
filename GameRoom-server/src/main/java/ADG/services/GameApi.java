package ADG.services;

import ADG.Lobby.GameOption;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * A game's own REST API, as GameRoom calls it to run a session. Failures are thrown as
 * {@link RestClientException}; {@link GameSessions} decides what they mean for the room.
 */
@Component
class GameApi {

  private static final Logger logger = LoggerFactory.getLogger(GameApi.class);

  RestTemplate restTemplate = new RestTemplate();

  /**
   * POSTs {baseUrl}/games and returns the new session's id. Without options (the options dialog
   * was never opened) the game's own defaults are sent, so a game never starts blank.
   */
  String createSession(String baseUrl, String roomName, int players, Map<String, String> options) {
    Map<String, String> toSend = options.isEmpty() ? defaultOptions(baseUrl) : options;
    Map<String, Object> request = new HashMap<>();
    request.put("roomName", roomName);
    request.put("maxPlayers", players);
    if (!toSend.isEmpty()) {
      request.put("gameOptions", GameOptionValues.parse(toSend));
    }
    Map<?, ?> response = restTemplate.postForObject(baseUrl + "/games", request, Map.class);
    return response.get("sessionId").toString();
  }

  void addPlayer(String baseUrl, String sessionId, String playerId, String name, int profilePic) {
    Map<String, Object> request = new HashMap<>();
    request.put("id", playerId);
    request.put("name", name);
    request.put("profilePic", profilePic);
    restTemplate.postForObject(baseUrl + "/games/" + sessionId + "/players", request, Map.class);
  }

  /**
   * POSTs {baseUrl}/games/{sessionId} with a robot picture for each bot and the URL the game
   * calls when everyone has left.
   */
  void startSession(String baseUrl, String sessionId, List<Integer> botProfilePics, String callbackUrl) {
    Map<String, Object> request = new HashMap<>();
    request.put("botProfilePics", botProfilePics);
    request.put("callbackUrl", callbackUrl);
    restTemplate.postForObject(baseUrl + "/games/" + sessionId, request, Void.class);
  }

  void deleteSession(String baseUrl, String sessionId) {
    restTemplate.delete(baseUrl + "/games/" + sessionId);
  }

  /** The game's own "message" for a 4xx answer, so the player sees why it refused. */
  static String errorMessage(RestClientException e) {
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

  /** The default of each of the game's options ({baseUrl}/game-options), or none on failure. */
  private Map<String, String> defaultOptions(String baseUrl) {
    try {
      GameOption[] options = restTemplate.getForObject(baseUrl + "/game-options", GameOption[].class);
      Map<String, String> defaults = new HashMap<>();
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
}
