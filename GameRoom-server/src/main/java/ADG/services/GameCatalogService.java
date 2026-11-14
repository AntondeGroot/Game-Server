package ADG.services;

import ADG.Lobby.GameDefinition;
import ADG.config.GamesConfig;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * The games a room can play: the configured games whose health check answers, each named in
 * the player's language. Both the GWT RPC call and {@code GET /games} read from here.
 */
@Service
public class GameCatalogService {

    private static final Logger logger = LoggerFactory.getLogger(GameCatalogService.class);
    private static final int HEALTH_TIMEOUT_MS = 1500;

    @Autowired
    private GamesConfig gamesConfig;

    RestTemplate restTemplate = new RestTemplate();

    /** Localized names by "gameId|language"; only successful lookups are cached. */
    private final Map<String, String> gameNameCache = new ConcurrentHashMap<>();

    /** The reachable games, each named in {@code language}. */
    public List<GameDefinition> getAvailableGames(String language) {
        return gamesConfig.getAvailable().stream()
                .filter(game -> isReachable(game.getHealthUrl()))
                .map(game -> withLocalizedName(game, language))
                .toList();
    }

    /** Whether the game answers its health URL with anything below 500. */
    boolean isReachable(String healthUrl) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(healthUrl).toURL().openConnection();
            connection.setConnectTimeout(HEALTH_TIMEOUT_MS);
            connection.setReadTimeout(HEALTH_TIMEOUT_MS);
            connection.setRequestMethod("HEAD");
            return connection.getResponseCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * A copy of {@code game} named by the game itself ({baseUrl}/game-name?locale=xx), so the
     * game owns its name in every language. Falls back to the configured name.
     */
    private GameDefinition withLocalizedName(GameDefinition game, String language) {
        String cacheKey = game.getId() + "|" + language;
        String name = gameNameCache.get(cacheKey);
        if (name == null) {
            name = fetchGameName(game.getBaseUrl(), language);
            if (name != null) gameNameCache.put(cacheKey, name);
        }
        GameDefinition copy = new GameDefinition();
        copy.setId(game.getId());
        copy.setName(name != null ? name : game.getName());
        copy.setBaseUrl(game.getBaseUrl());
        copy.setHealthUrl(game.getHealthUrl());
        copy.setMinPlayers(game.getMinPlayers());
        copy.setMaxPlayers(game.getMaxPlayers());
        copy.setEmbeddedSettings(game.isEmbeddedSettings());
        return copy;
    }

    /** GETs {baseUrl}/game-name?locale=xx and returns the name, or null on any failure. */
    private String fetchGameName(String baseUrl, String language) {
        try {
            Map<?, ?> body = restTemplate.getForObject(baseUrl + "/game-name?locale=" + language, Map.class);
            if (body != null && body.get("name") != null) {
                return body.get("name").toString();
            }
        } catch (Exception e) {
            logger.warn("Could not fetch localized game name from {} (locale {}): {}",
                    baseUrl, language, e.getMessage());
        }
        return null;
    }
}
