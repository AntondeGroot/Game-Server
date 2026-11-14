package ADG.services;

import ADG.Lobby.GameDefinition;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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