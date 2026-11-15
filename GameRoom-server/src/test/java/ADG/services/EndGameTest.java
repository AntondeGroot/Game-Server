package ADG.services;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Checks {@code DELETE /rooms/{roomId}/session} against gameroom_openapi.yaml. The game's own
 * server is a mock; qwixx comes from games.yaml (at http://localhost:8090/qwixx).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class EndGameTest {

  private static final Cookie HOST = new Cookie("playerid", "host-1");
  private static final String QWIXX = "http://localhost:8090/qwixx";

  @Autowired MockMvc mockMvc;
  @Autowired RoomStore roomStore;
  @Autowired GameApi gameApi;

  private final RestTemplate gameServer = mock(RestTemplate.class);
  private RestTemplate realRestTemplate;
  private String roomId;

  @BeforeEach
  void startQwixxAsHost() throws Exception {
    realRestTemplate = gameApi.restTemplate;
    gameApi.restTemplate = gameServer;
    roomStore.rooms.clear();
    String created = mockMvc.perform(post("/rooms")
            .cookie(HOST)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name": "Dragon's Den", "hostName": "Ann", "profileId": "3"}"""))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    roomId = JsonPath.read(created, "$.id");
    mockMvc.perform(put("/rooms/" + roomId + "/game")
            .cookie(HOST)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"gameId": "qwixx"}"""))
        .andExpect(status().isOk());
    when(gameServer.postForObject(eq(QWIXX + "/games"), any(), eq(Map.class)))
        .thenReturn(Map.of("sessionId", "session-1"));
    mockMvc.perform(post("/rooms/" + roomId + "/session").cookie(HOST))
        .andExpect(status().isCreated());
  }

  @AfterEach
  void restoreTheRealGameApiCalls() {
    gameApi.restTemplate = realRestTemplate;
  }

  @Test
  void theHostEndsTheGameAndTheRoomIsWaitingAgain() throws Exception {
    mockMvc.perform(delete("/rooms/" + roomId + "/session").cookie(HOST))
        .andExpect(status().isNoContent());

    verify(gameServer).delete(QWIXX + "/games/session-1");
    mockMvc.perform(get("/rooms/" + roomId).cookie(HOST))
        .andExpect(jsonPath("$.status", is("WAITING")))
        .andExpect(jsonPath("$.gameSessionId").doesNotExist());
  }

  @Test
  void anotherPlayerMayNotEndTheGameUnlessTheSettingsAllowIt() throws Exception {
    mockMvc.perform(delete("/rooms/" + roomId + "/session").cookie(new Cookie("playerid", "player-2")))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    verify(gameServer, never()).delete(anyString());
    mockMvc.perform(get("/rooms/" + roomId).cookie(HOST))
        .andExpect(jsonPath("$.status", is("PLAYING")))
        .andExpect(jsonPath("$.gameSessionId", is("session-1")));
  }

  @Test
  void anotherPlayerMayEndTheGameWhenTheSettingsAllowIt() throws Exception {
    mockMvc.perform(put("/rooms/" + roomId + "/settings")
            .cookie(HOST)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"anyPlayerCanSelectGame": false, "anyPlayerCanSetOptions": true}"""))
        .andExpect(status().isOk());

    mockMvc.perform(delete("/rooms/" + roomId + "/session").cookie(new Cookie("playerid", "player-2")))
        .andExpect(status().isNoContent());

    verify(gameServer).delete(QWIXX + "/games/session-1");
    mockMvc.perform(get("/rooms/" + roomId).cookie(HOST))
        .andExpect(jsonPath("$.status", is("WAITING")));
  }

  @Test
  void aGameServerThatCannotBeReachedStillLetsTheHostEndTheGame() throws Exception {
    // The way out of a stuck game must not depend on the game answering.
    doThrow(new ResourceAccessException("Connection refused"))
        .when(gameServer).delete(QWIXX + "/games/session-1");

    mockMvc.perform(delete("/rooms/" + roomId + "/session").cookie(HOST))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/rooms/" + roomId).cookie(HOST))
        .andExpect(jsonPath("$.status", is("WAITING")))
        .andExpect(jsonPath("$.gameSessionId").doesNotExist());
  }

  @Test
  void endingTheGameOfAnUnknownRoomIsNotFound() throws Exception {
    mockMvc.perform(delete("/rooms/no-such-room/session").cookie(HOST))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", containsString("no-such-room")));

    verify(gameServer, never()).delete(anyString());
  }
}
