package ADG.services;

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

/** Checks {@code PUT /rooms/{roomId}/game-options} against gameroom_openapi.yaml. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SetGameOptionsTest {

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
  void theHostSetsTheGameOptions() throws Exception {
    mockMvc.perform(put("/rooms/" + roomId + "/game-options")
            .cookie(HOST)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"extraRow": "true", "rounds": "3"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gameOptions.extraRow", is("true")))
        .andExpect(jsonPath("$.gameOptions.rounds", is("3")));
  }

  @Test
  void anotherPlayerMayNotSetTheOptionsUnlessTheSettingsAllowIt() throws Exception {
    mockMvc.perform(put("/rooms/" + roomId + "/game-options")
            .cookie(new Cookie("playerid", "player-2"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"extraRow": "true"}"""))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void anotherPlayerMaySetTheOptionsWhenTheSettingsAllowIt() throws Exception {
    mockMvc.perform(put("/rooms/" + roomId + "/settings")
            .cookie(HOST)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"anyPlayerCanSelectGame": false, "anyPlayerCanSetOptions": true}"""))
        .andExpect(status().isOk());

    mockMvc.perform(put("/rooms/" + roomId + "/game-options")
            .cookie(new Cookie("playerid", "player-2"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"extraRow": "true"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gameOptions.extraRow", is("true")));
  }

  @Test
  void newOptionsReplaceTheOldOnesInsteadOfBeingMergedIn() throws Exception {
    setOptions("""
        {"extraRow": "true", "rounds": "3"}""");

    mockMvc.perform(put("/rooms/" + roomId + "/game-options")
            .cookie(HOST)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"rounds": "5"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gameOptions.rounds", is("5")))
        .andExpect(jsonPath("$.gameOptions.extraRow").doesNotExist());
  }

  @Test
  void settingTheOptionsOfAnUnknownRoomIsNotFound() throws Exception {
    mockMvc.perform(put("/rooms/no-such-room/game-options")
            .cookie(HOST)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"extraRow": "true"}"""))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", containsString("no-such-room")));
  }

  private void setOptions(String json) throws Exception {
    mockMvc.perform(put("/rooms/" + roomId + "/game-options")
            .cookie(HOST)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json))
        .andExpect(status().isOk());
  }
}
