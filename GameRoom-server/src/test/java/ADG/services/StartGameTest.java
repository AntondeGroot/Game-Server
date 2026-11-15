package ADG.services;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Checks {@code POST /rooms/{roomId}/session} against gameroom_openapi.yaml. The game's own
 * server is a mock; qwixx comes from games.yaml (1–5 players, at http://localhost:8090/qwixx).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class StartGameTest {

  private static final Cookie HOST = new Cookie("playerid", "host-1");
  private static final String QWIXX = "http://localhost:8090/qwixx";

  @Autowired MockMvc mockMvc;
  @Autowired RoomStore roomStore;
  @Autowired GameApi gameApi;

  private final RestTemplate gameServer = mock(RestTemplate.class);
  private RestTemplate realRestTemplate;
  private String roomId;

  @BeforeEach
  void createRoomWithQwixxAsHost() throws Exception {
    realRestTemplate = gameApi.restTemplate;
    gameApi.restTemplate = gameServer;
    roomStore.rooms.clear();
    roomId = createRoom(HOST, "Dragon's Den");
    mockMvc.perform(put("/rooms/" + roomId + "/game")
            .cookie(HOST)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"gameId": "qwixx"}"""))
        .andExpect(status().isOk());
  }

  @AfterEach
  void restoreTheRealGameApiCalls() {
    gameApi.restTemplate = realRestTemplate;
  }

  @Test
  void theHostStartsTheGameAndTheRoomIsPlaying() throws Exception {
    when(gameServer.postForObject(eq(QWIXX + "/games"), any(), eq(Map.class)))
        .thenReturn(Map.of("sessionId", "session-1"));

    mockMvc.perform(post("/rooms/" + roomId + "/session").cookie(HOST))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status", is("PLAYING")))
        .andExpect(jsonPath("$.gameSessionId", is("session-1")))
        .andExpect(jsonPath("$.gameBaseUrl", is("/qwixx")));
  }

  @Test
  void startingWithoutAChosenGameIsAConflict() throws Exception {
    Cookie otherHost = new Cookie("playerid", "host-2");
    String roomWithoutGame = createRoom(otherHost, "Empty Table");

    mockMvc.perform(post("/rooms/" + roomWithoutGame + "/session").cookie(otherHost))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", containsString("No game selected")));
    verifyNoInteractions(gameServer);
  }

  @Test
  void aGameServerThatCannotBeReachedIsABadGatewayAndTheRoomKeepsWaiting() throws Exception {
    when(gameServer.postForObject(eq(QWIXX + "/games"), any(), eq(Map.class)))
        .thenThrow(new ResourceAccessException("Connection refused"));

    mockMvc.perform(post("/rooms/" + roomId + "/session").cookie(HOST))
        .andExpect(status().isBadGateway())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", containsString("Could not reach game server")));

    mockMvc.perform(get("/rooms/" + roomId).cookie(HOST))
        .andExpect(jsonPath("$.status", is("WAITING")))
        .andExpect(jsonPath("$.gameSessionId").doesNotExist());
  }

  @Test
  void startingTheGameOfAnUnknownRoomIsNotFound() throws Exception {
    mockMvc.perform(post("/rooms/no-such-room/session").cookie(HOST))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", containsString("no-such-room")));
    verifyNoInteractions(gameServer);
  }

  private String createRoom(Cookie host, String name) throws Exception {
    String created = mockMvc.perform(post("/rooms")
            .cookie(host)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name": "%s", "hostName": "Ann", "profileId": "3"}""".formatted(name)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return JsonPath.read(created, "$.id");
  }
}
