package ADG.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ADG.Lobby.GameDefinition;
import ADG.Lobby.GameOption;
import ADG.config.GamesConfig;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/** Moved from RoomServiceImplTest together with the code. */
@ExtendWith(MockitoExtension.class)
class GameCatalogServiceTest {

    @Mock private GamesConfig gamesConfig;
    @Mock private RestTemplate restTemplate;

    private GameCatalogService catalog;

    @BeforeEach
    void setUp() throws Exception {
        catalog = spy(new GameCatalogService());
        Field gamesConfigField = GameCatalogService.class.getDeclaredField("gamesConfig");
        gamesConfigField.setAccessible(true);
        gamesConfigField.set(catalog, gamesConfig);
        catalog.restTemplate = restTemplate;
    }

    @Test
    void returnsNothingWhenNoGamesAreConfigured() {
        when(gamesConfig.getAvailable()).thenReturn(List.of());
        assertTrue(catalog.getAvailableGames("en").isEmpty());
    }

    @Test
    void usesLocalizedNameFromGame() {
        when(gamesConfig.getAvailable()).thenReturn(List.of(gameDef("keezen", "Keezen", "http://game")));
        doReturn(true).when(catalog).isReachable("http://game/health");
        when(restTemplate.getForObject("http://game/game-name?locale=de", Map.class))
                .thenReturn(Map.of("name", "Dog"));

        List<GameDefinition> games = catalog.getAvailableGames("de");

        assertEquals(1, games.size());
        assertEquals("Dog", games.get(0).getName(), "name comes from the game's /game-name");
        assertEquals("keezen", games.get(0).getId(), "other fields are preserved");
        assertEquals(8, games.get(0).getMaxPlayers());
    }

    @Test
    void fallsBackToConfigNameWhenGameHasNoLocalizedName() {
        when(gamesConfig.getAvailable()).thenReturn(List.of(gameDef("keezen", "Keezen", "http://game")));
        doReturn(true).when(catalog).isReachable("http://game/health");
        when(restTemplate.getForObject("http://game/game-name?locale=de", Map.class))
                .thenThrow(new ResourceAccessException("game down"));

        List<GameDefinition> games = catalog.getAvailableGames("de");

        assertEquals("Keezen", games.get(0).getName(), "falls back to the configured name");
    }

    @Test
    void cachesLocalizedNamePerLanguage() {
        when(gamesConfig.getAvailable()).thenReturn(List.of(gameDef("keezen", "Keezen", "http://game")));
        doReturn(true).when(catalog).isReachable("http://game/health");
        when(restTemplate.getForObject("http://game/game-name?locale=de", Map.class))
                .thenReturn(Map.of("name", "Dog"));

        catalog.getAvailableGames("de");
        catalog.getAvailableGames("de");

        verify(restTemplate, times(1)).getForObject("http://game/game-name?locale=de", Map.class);
    }

    @Test
    void optionsComeFromTheGameInTheRequestedLanguage() {
        when(gamesConfig.findById("keezen")).thenReturn(Optional.of(gameDef("keezen", "Keezen", "http://game")));
        GameOption[] fromGame = {option("teams", false)};
        when(restTemplate.getForObject("http://game/game-options?locale=nl", GameOption[].class))
                .thenReturn(fromGame);

        List<GameOption> options = catalog.optionsFor("keezen", "nl", false).orElseThrow();

        assertEquals(List.of("teams"), options.stream().map(GameOption::getKey).toList());
    }

    @Test
    void playersDoNotSeeAdminOnlyOptions() {
        when(gamesConfig.findById("keezen")).thenReturn(Optional.of(gameDef("keezen", "Keezen", "http://game")));
        GameOption[] fromGame = {option("teams", false), option("birthdayMode", true)};
        when(restTemplate.getForObject("http://game/game-options?locale=en", GameOption[].class))
                .thenReturn(fromGame);

        List<GameOption> options = catalog.optionsFor("keezen", "en", false).orElseThrow();

        assertEquals(List.of("teams"), options.stream().map(GameOption::getKey).toList());
    }

    @Test
    void adminsAlsoSeeAdminOnlyOptions() {
        when(gamesConfig.findById("keezen")).thenReturn(Optional.of(gameDef("keezen", "Keezen", "http://game")));
        GameOption[] fromGame = {option("teams", false), option("birthdayMode", true)};
        when(restTemplate.getForObject("http://game/game-options?locale=en", GameOption[].class))
                .thenReturn(fromGame);

        List<GameOption> options = catalog.optionsFor("keezen", "en", true).orElseThrow();

        assertEquals(List.of("teams", "birthdayMode"), options.stream().map(GameOption::getKey).toList());
    }

    @Test
    void aGameThatIsDownOffersNoOptions() {
        when(gamesConfig.findById("keezen")).thenReturn(Optional.of(gameDef("keezen", "Keezen", "http://game")));
        when(restTemplate.getForObject("http://game/game-options?locale=en", GameOption[].class))
                .thenThrow(new ResourceAccessException("game down"));

        assertEquals(Optional.of(List.of()), catalog.optionsFor("keezen", "en", false));
    }

    @Test
    void anUnknownGameHasNoOptionsAtAll() {
        when(gamesConfig.findById("chess")).thenReturn(Optional.empty());

        assertEquals(Optional.empty(), catalog.optionsFor("chess", "en", false));
        verifyNoInteractions(restTemplate);
    }

    private GameDefinition gameDef(String id, String name, String baseUrl) {
        GameDefinition g = new GameDefinition();
        g.setId(id);
        g.setName(name);
        g.setBaseUrl(baseUrl);
        g.setHealthUrl(baseUrl + "/health");
        g.setMinPlayers(2);
        g.setMaxPlayers(8);
        return g;
    }

    private GameOption option(String key, boolean adminOnly) {
        GameOption o = new GameOption();
        o.setKey(key);
        o.setAdminOnly(adminOnly);
        return o;
    }
}
