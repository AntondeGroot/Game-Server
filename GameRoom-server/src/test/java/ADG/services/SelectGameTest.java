package ADG.services;

import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Checks {@code PUT /rooms/{roomId}/game} against gameroom_openapi.yaml. The games come from
 * games.yaml: keezen (2–8 players) and qwixx (1–5, embedded settings).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SelectGameTest {

  private static final Cookie HOST = new Cookie("playerid", "host-1");

  @Autowired MockMvc mockMvc;
  @Autowired RoomStore roomStore;

  private String roomId;

  @BeforeEach
  void createRoomAsHost() throws Exception {
    roomStore.rooms.clear();
    String created = mockMvc.perform(post("/rooms")
            .cookie(HOST)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name": "Dragon's Den", "hostName": "Ann", "profileId": "3"}"""))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    roomId = JsonPath.read(created, "$.id");
  }

  @Test
  void theHostChoosesTheGameAndTheRoomTakesItsPlayerLimits() throws Exception {
    mockMvc.perform(put("/rooms/" + roomId + "/game")
            .cookie(HOST)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"gameId": "qwixx"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gameId", is("qwixx")))
        .andExpect(jsonPath("$.minPlayers", is(1)))
        .andExpect(jsonPath("$.maxPlayers", is(5)))
        .andExpect(jsonPath("$.embeddedSettings", is(true)));
  }

  @Test
  void anotherPlayerMayNotChooseTheGameUnlessTheSettingsAllowIt() throws Exception {
    mockMvc.perform(put("/rooms/" + roomId + "/game")
            .cookie(new Cookie("playerid", "player-2"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"gameId": "qwixx"}"""))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void anotherPlayerMayChooseTheGameWhenTheSettingsAllowIt() throws Exception {
    mockMvc.perform(put("/rooms/" + roomId + "/settings")
            .cookie(HOST)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"anyPlayerCanSelectGame": true, "anyPlayerCanSetOptions": false}"""))
        .andExpect(status().isOk());

    mockMvc.perform(put("/rooms/" + roomId + "/game")
            .cookie(new Cookie("playerid", "player-2"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"gameId": "keezen"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gameId", is("keezen")));
  }

  @Test
  void choosingAnotherGameClearsThePreviousGamesOptions() throws Exception {
    selectGame("qwixx");
    // PUT /rooms/{roomId}/game-options is not built yet, so set an option in the store.
    roomStore.rooms.get(0).getGameOptions().put("extraRow", "true");

    mockMvc.perform(put("/rooms/" + roomId + "/game")
            .cookie(HOST)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"gameId": "keezen"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gameOptions", anEmptyMap()));
  }

  @Test
  void choosingTheGameOfAnUnknownRoomIsNotFound() throws Exception {
    mockMvc.perform(put("/rooms/no-such-room/game")
            .cookie(HOST)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"gameId": "qwixx"}"""))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", containsString("no-such-room")));
  }

  private void selectGame(String gameId) throws Exception {
    mockMvc.perform(put("/rooms/" + roomId + "/game")
            .cookie(HOST)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"gameId\": \"" + gameId + "\"}"))
        .andExpect(status().isOk());
  }
}
