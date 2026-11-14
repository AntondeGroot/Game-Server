package ADG.services;

import ADG.Lobby.GameDefinition;
import ADG.Lobby.GameOption;
import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class GamesControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired RoomStore roomStore;
    @MockitoBean GameCatalogService gameCatalog;

    @BeforeEach
    void setUp() {
        roomStore.rooms.clear();
    }

    @Test
    void returns204AndResetsRoomToWaiting() throws Exception {
        Room room = playingRoom();
        roomStore.rooms.add(room);

        mockMvc.perform(post("/rooms/" + room.getId() + "/game-finished"))
                .andExpect(status().isNoContent());

        assertEquals(GameStatus.WAITING, room.getStatus());
        assertNull(room.getGameSessionId());
    }

    @Test
    void returns204ForUnknownRoom() throws Exception {
        mockMvc.perform(post("/rooms/no-such-room/game-finished"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getGamesListsTheGamesWithoutTheirInternalUrls() throws Exception {
        when(gameCatalog.getAvailableGames("en")).thenReturn(List.of(game("qwixx", "Qwixx", 1, 5, true)));

        mockMvc.perform(get("/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("qwixx"))
                .andExpect(jsonPath("$[0].name").value("Qwixx"))
                .andExpect(jsonPath("$[0].minPlayers").value(1))
                .andExpect(jsonPath("$[0].maxPlayers").value(5))
                .andExpect(jsonPath("$[0].embeddedSettings").value(true))
                .andExpect(jsonPath("$[0].baseUrl").doesNotExist())
                .andExpect(jsonPath("$[0].healthUrl").doesNotExist());
    }

    @Test
    void getGamesNamesTheGamesInTheLanguageFromTheCookie() throws Exception {
        when(gameCatalog.getAvailableGames("nl")).thenReturn(List.of(game("keezen", "Keezen", 2, 8, false)));

        mockMvc.perform(get("/games").cookie(new Cookie("language", "nl")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("keezen"));
    }

    @Test
    void getGamesAnswersAnEmptyListWhenNoGameIsReachable() throws Exception {
        when(gameCatalog.getAvailableGames("en")).thenReturn(List.of());

        mockMvc.perform(get("/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getGameOptionsListsTheOptionsTheGameOffers() throws Exception {
        GameOption rounds = new GameOption();
        rounds.setKey("rounds");
        rounds.setLabel("Rounds");
        rounds.setType("number");
        rounds.setDefaultValue("3");
        rounds.setMinValue(1);
        rounds.setMaxValue(10);
        when(gameCatalog.optionsFor("keezen", "en", false)).thenReturn(Optional.of(List.of(rounds)));

        mockMvc.perform(get("/games/keezen/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].key").value("rounds"))
                .andExpect(jsonPath("$[0].label").value("Rounds"))
                .andExpect(jsonPath("$[0].type").value("number"))
                .andExpect(jsonPath("$[0].defaultValue").value("3"))
                .andExpect(jsonPath("$[0].minValue").value(1))
                .andExpect(jsonPath("$[0].maxValue").value(10))
                .andExpect(jsonPath("$[0].adminOnly").doesNotExist());
    }

    @Test
    void getGameOptionsAnswers404ForAnUnknownGame() throws Exception {
        when(gameCatalog.optionsFor("chess", "en", false)).thenReturn(Optional.empty());

        mockMvc.perform(get("/games/chess/options"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("chess")));
    }

    @Test
    void getGameOptionsShowsALoggedInAdminTheAdminOnlyOptions() throws Exception {
        GameOption birthdayMode = new GameOption();
        birthdayMode.setKey("birthdayMode");
        birthdayMode.setType("boolean");
        when(gameCatalog.optionsFor("keezen", "en", true)).thenReturn(Optional.of(List.of(birthdayMode)));
        // admin:test matches the BCrypt hash in test/resources/application.yaml.
        String adminAuth = "Basic " + Base64.getEncoder().encodeToString("admin:test".getBytes());

        mockMvc.perform(get("/games/keezen/options").header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("birthdayMode"));
    }

    @Test
    void getGameOptionsLabelsTheOptionsInTheLanguageFromTheCookie() throws Exception {
        GameOption rounds = new GameOption();
        rounds.setKey("rounds");
        rounds.setLabel("Rondes");
        rounds.setType("number");
        when(gameCatalog.optionsFor("keezen", "nl", false)).thenReturn(Optional.of(List.of(rounds)));

        mockMvc.perform(get("/games/keezen/options").cookie(new Cookie("language", "nl")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Rondes"));
    }

    private GameDefinition game(String id, String name, int minPlayers, int maxPlayers, boolean embeddedSettings) {
        GameDefinition g = new GameDefinition();
        g.setId(id);
        g.setName(name);
        g.setBaseUrl("http://localhost:8090/" + id);
        g.setHealthUrl("http://localhost:80/" + id + "/");
        g.setMinPlayers(minPlayers);
        g.setMaxPlayers(maxPlayers);
        g.setEmbeddedSettings(embeddedSettings);
        return g;
    }

    private Room playingRoom() {
        Room r = new Room();
        r.setId(UUID.randomUUID().toString());
        r.setName("Test Room");
        r.setCreatedByUserId("player-1");
        r.setStatus(GameStatus.PLAYING);
        r.setGameSessionId("session-" + UUID.randomUUID());
        return r;
    }
}