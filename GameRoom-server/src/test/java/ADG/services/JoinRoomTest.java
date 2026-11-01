package ADG.services;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Checks {@code PUT /rooms/{id}/players/{playerId}} against gameroom_openapi.yaml: who may join,
 * status codes and body shapes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class JoinRoomTest {

    private static final Cookie HOST = new Cookie("playerid", "host-1");
    private static final Cookie GUEST = new Cookie("playerid", "guest-1");

    @Autowired MockMvc mockMvc;
    @Autowired RoomStore roomStore;

    private String roomId;

    @BeforeEach
    void createRoomWithPassword() throws Exception {
        roomStore.rooms.clear();
        String created = mockMvc.perform(post("/rooms")
                        .cookie(HOST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Dragon's Den", "hostName": "Ann", "profileId": "3"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        roomId = JsonPath.read(created, "$.id");
        mockMvc.perform(put("/rooms/" + roomId + "/password")
                        .cookie(HOST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "Open Sesame"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void joinWithTheWrongPasswordIsForbiddenAndLeavesTheRoomAsItWas() throws Exception {
        mockMvc.perform(put("/rooms/" + roomId + "/players/guest-1")
                        .cookie(GUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bob", "profileId": "5", "password": "close sesame"}"""))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("password")));

        mockMvc.perform(get("/rooms/" + roomId).cookie(HOST))
                .andExpect(jsonPath("$.players[*].id", contains("host-1")));
    }

    @Test
    void joinWithTheRightPasswordInAnyCaseAndSpacingSeatsThePlayer() throws Exception {
        mockMvc.perform(put("/rooms/" + roomId + "/players/guest-1")
                        .cookie(GUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bob", "profileId": "5", "password": " open  sesame "}"""))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(roomId)))
                .andExpect(jsonPath("$.players[1].id", is("guest-1")))
                .andExpect(jsonPath("$.players[1].name", is("Bob")))
                .andExpect(jsonPath("$.players[1].profileId", is("5")))
                .andExpect(jsonPath("$.hasPassword", is(true)))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void aLoggedInAdminJoinsALockedRoomWithoutThePassword() throws Exception {
        // admin:test matches the BCrypt hash in test/resources/application.yaml.
        String adminAuth = "Basic " + Base64.getEncoder().encodeToString("admin:test".getBytes());
        mockMvc.perform(put("/rooms/" + roomId + "/players/admin-player")
                        .cookie(new Cookie("playerid", "admin-player"))
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Admin", "profileId": "7"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[*].id", contains("host-1", "admin-player")));
    }

    @Test
    void joiningAsAnotherPlayerIsForbidden() throws Exception {
        mockMvc.perform(put("/rooms/" + roomId + "/players/someone-else")
                        .cookie(GUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bob", "profileId": "5", "password": "open sesame"}"""))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("yourself")));

        mockMvc.perform(get("/rooms/" + roomId).cookie(HOST))
                .andExpect(jsonPath("$.players[*].id", contains("host-1")));
    }

    @Test
    void joiningARoomThatTheLastSeatFilledIsAConflict() throws Exception {
        roomStore.rooms.getFirst().setMaxPlayers(2);
        mockMvc.perform(put("/rooms/" + roomId + "/players/guest-1")
                        .cookie(GUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bob", "profileId": "5", "password": "open sesame"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FULL")));

        mockMvc.perform(put("/rooms/" + roomId + "/players/guest-2")
                        .cookie(new Cookie("playerid", "guest-2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Cy", "profileId": "6", "password": "open sesame"}"""))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("full")));

        mockMvc.perform(get("/rooms/" + roomId).cookie(HOST))
                .andExpect(jsonPath("$.players[*].id", contains("host-1", "guest-1")));
    }

    @Test
    void pickingTheHostsPictureInARoomWithUniquePicturesIsAConflict() throws Exception {
        roomStore.rooms.getFirst().setUniqueProfilePics(true);
        mockMvc.perform(put("/rooms/" + roomId + "/players/guest-1")
                        .cookie(GUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bob", "profileId": "3", "password": "open sesame"}"""))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("picture")));

        mockMvc.perform(get("/rooms/" + roomId).cookie(HOST))
                .andExpect(jsonPath("$.players[*].id", contains("host-1")));
    }

    @Test
    void aPlayerInTheRoomChangesNameAndPictureWithoutThePassword() throws Exception {
        mockMvc.perform(put("/rooms/" + roomId + "/players/guest-1")
                        .cookie(GUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bob", "profileId": "5", "password": "open sesame"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(put("/rooms/" + roomId + "/players/guest-1")
                        .cookie(GUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bobby", "profileId": "9"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[*].id", contains("host-1", "guest-1")))
                .andExpect(jsonPath("$.players[1].name", is("Bobby")))
                .andExpect(jsonPath("$.players[1].profileId", is("9")));
    }

    @Test
    void joiningAnotherRoomLeavesTheFirstOne() throws Exception {
        mockMvc.perform(put("/rooms/" + roomId + "/players/guest-1")
                        .cookie(GUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bob", "profileId": "5", "password": "open sesame"}"""))
                .andExpect(status().isOk());
        String created = mockMvc.perform(post("/rooms")
                        .cookie(new Cookie("playerid", "host-2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Goblin Cave", "hostName": "Dee", "profileId": "4"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondRoomId = JsonPath.read(created, "$.id");

        mockMvc.perform(put("/rooms/" + secondRoomId + "/players/guest-1")
                        .cookie(GUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bob", "profileId": "5"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[*].id", contains("host-2", "guest-1")));

        mockMvc.perform(get("/rooms/" + roomId).cookie(HOST))
                .andExpect(jsonPath("$.players[*].id", contains("host-1")));
    }

    @Test
    void joiningARoomThatDoesNotExistIsNotFound() throws Exception {
        mockMvc.perform(put("/rooms/no-such-room/players/guest-1")
                        .cookie(GUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bob", "profileId": "5"}"""))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("no-such-room")));
    }
}
