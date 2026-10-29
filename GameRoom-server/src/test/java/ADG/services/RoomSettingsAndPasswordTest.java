package ADG.services;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Checks the host-only room settings and password endpoints against gameroom_openapi.yaml:
 * status codes and body shapes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RoomSettingsAndPasswordTest {

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
    void setRoomPasswordStoresItWithoutSpacesAndUpperCased() throws Exception {
        mockMvc.perform(put("/rooms/" + roomId + "/password")
                        .cookie(HOST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": " my pass 7 "}"""))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.password", is("MYPASS7")));

        mockMvc.perform(get("/rooms/" + roomId).cookie(HOST))
                .andExpect(jsonPath("$.hasPassword", is(true)))
                .andExpect(jsonPath("$.password", is("MYPASS7")));
    }

    @Test
    void setRoomPasswordThatIsOnlySpacesIsABadRequest() throws Exception {
        // Three spaces pass the spec's minLength: 1, so this reaches the server's own check.
        mockMvc.perform(put("/rooms/" + roomId + "/password")
                        .cookie(HOST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("empty")));

        mockMvc.perform(get("/rooms/" + roomId).cookie(HOST))
                .andExpect(jsonPath("$.hasPassword", is(false)));
    }

    @Test
    void generateRoomPasswordGivesAPasswordTheLobbyShowsAsProtected() throws Exception {
        // Consonant, vowel, consonant and two digits, without the look-alikes 0, 1, O and I.
        mockMvc.perform(post("/rooms/" + roomId + "/password").cookie(HOST))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.password", matchesPattern("[CDFHJKMNPRTWXY][AEU][CDFHJKMNPRTWXY][2-9]{2}")));

        mockMvc.perform(get("/rooms"))
                .andExpect(jsonPath("$[0].hasPassword", is(true)))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void removeRoomPasswordOpensTheRoomAgain() throws Exception {
        mockMvc.perform(post("/rooms/" + roomId + "/password").cookie(HOST))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/rooms/" + roomId + "/password").cookie(HOST))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/rooms/" + roomId).cookie(HOST))
                .andExpect(jsonPath("$.hasPassword", is(false)))
                .andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(get("/rooms"))
                .andExpect(jsonPath("$[0].hasPassword", is(false)));
    }

    @Test
    void setRoomPasswordByAnotherPlayerIsForbidden() throws Exception {
        mockMvc.perform(put("/rooms/" + roomId + "/password")
                        .cookie(new Cookie("playerid", "guest-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "LOCKED"}"""))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("host")));

        mockMvc.perform(get("/rooms/" + roomId).cookie(HOST))
                .andExpect(jsonPath("$.hasPassword", is(false)));
    }

    @Test
    void setRoomSettingsReturnsTheRoomWithTheNewSettings() throws Exception {
        // Both start false; different values per field catch the two being swapped.
        mockMvc.perform(put("/rooms/" + roomId + "/settings")
                        .cookie(HOST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anyPlayerCanSelectGame": true, "anyPlayerCanSetOptions": false}"""))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(roomId)))
                .andExpect(jsonPath("$.settings.anyPlayerCanSelectGame", is(true)))
                .andExpect(jsonPath("$.settings.anyPlayerCanSetOptions", is(false)));

        mockMvc.perform(get("/rooms/" + roomId).cookie(new Cookie("playerid", "guest-1")))
                .andExpect(jsonPath("$.settings.anyPlayerCanSelectGame", is(true)))
                .andExpect(jsonPath("$.settings.anyPlayerCanSetOptions", is(false)));
    }

    @Test
    void setRoomSettingsByAnotherPlayerIsForbiddenAndKeepsThem() throws Exception {
        mockMvc.perform(put("/rooms/" + roomId + "/settings")
                        .cookie(new Cookie("playerid", "guest-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anyPlayerCanSelectGame": true, "anyPlayerCanSetOptions": true}"""))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("host")));

        mockMvc.perform(get("/rooms/" + roomId).cookie(HOST))
                .andExpect(jsonPath("$.settings.anyPlayerCanSelectGame", is(false)))
                .andExpect(jsonPath("$.settings.anyPlayerCanSetOptions", is(false)));
    }

    @Test
    void setRoomSettingsWithAMissingFieldIsABadRequest() throws Exception {
        // A PUT replaces both settings, so the spec requires both; this is not a partial update.
        mockMvc.perform(put("/rooms/" + roomId + "/settings")
                        .cookie(HOST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"anyPlayerCanSelectGame": true}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/rooms/" + roomId).cookie(HOST))
                .andExpect(jsonPath("$.settings.anyPlayerCanSelectGame", is(false)));
    }

    @Test
    void passwordOfAnUnknownRoomIsNotFound() throws Exception {
        // Not a 403: the room check comes before the host check.
        mockMvc.perform(post("/rooms/no-such-room/password").cookie(HOST))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("no-such-room")));
    }
}
