package ADG.services;

import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Checks the rooms endpoints against gameroom_openapi.yaml: status codes and body shapes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RoomsControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired RoomStore roomStore;

    @BeforeEach
    void clearRooms() {
        roomStore.rooms.clear();
    }

    @Test
    void createRoomMakesTheCallerHostAndFirstPlayer() throws Exception {
        mockMvc.perform(post("/rooms")
                        .cookie(new Cookie("playerid", "host-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Dragon's Den", "hostName": "Ann", "profileId": "3"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/rooms/[0-9a-f-]{36}")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name", is("Dragon's Den")))
                .andExpect(jsonPath("$.hostId", is("host-1")))
                .andExpect(jsonPath("$.status", is("WAITING")))
                .andExpect(jsonPath("$.players", hasSize(1)))
                .andExpect(jsonPath("$.players[0].id", is("host-1")))
                .andExpect(jsonPath("$.players[0].name", is("Ann")))
                .andExpect(jsonPath("$.players[0].profileId", is("3")))
                .andExpect(jsonPath("$.hasPassword", is(false)));
    }

    @Test
    void createRoomRejectsANameThatIsTakenIgnoringCase() throws Exception {
        createRoom("host-1", "Dragon's Den").andExpect(status().isCreated());

        createRoom("host-2", "DRAGON'S DEN")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.detail", not(emptyOrNullString())));

        mockMvc.perform(get("/rooms"))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void createRoomRejectsANameShorterThanThreeCharactersAfterTrimming() throws Exception {
        // Five characters, so it passes the spec's minLength: 3 and reaches the server's own check.
        createRoom("host-1", " ab  ")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("at least 3 characters")));

        mockMvc.perform(get("/rooms"))
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void createRoomWithoutPlayerCookieIsABadRequest() throws Exception {
        mockMvc.perform(post("/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Dragon's Den", "hostName": "Ann", "profileId": "3"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("playerid")));

        mockMvc.perform(get("/rooms"))
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void getRoomShowsThePasswordToTheHostOnly() throws Exception {
        createRoom("host-1", "Dragon's Den").andExpect(status().isCreated());
        // The password endpoints are not built yet, so set it on the stored room.
        Room room = roomStore.rooms.get(0);
        room.setRoomPassword("CAD47");

        mockMvc.perform(get("/rooms/" + room.getId()).cookie(new Cookie("playerid", "host-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword", is(true)))
                .andExpect(jsonPath("$.password", is("CAD47")));

        mockMvc.perform(get("/rooms/" + room.getId()).cookie(new Cookie("playerid", "guest-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword", is(true)))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void getRoomLeavesOutAPendingGwtRoom() throws Exception {
        addPendingGwtRoom("pending-1", "host-1");

        mockMvc.perform(get("/rooms/pending-1").cookie(new Cookie("playerid", "host-1")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void getRoomsListsLobbyRoomsWithoutPendingOnes() throws Exception {
        createRoom("host-1", "Dragon's Den").andExpect(status().isCreated());
        addPendingGwtRoom("pending-1", "host-2");

        mockMvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Dragon's Den")))
                .andExpect(jsonPath("$[0].status", is("WAITING")))
                .andExpect(jsonPath("$[0].playerCount", is(1)))
                .andExpect(jsonPath("$[0].maxPlayers", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].hasPassword", is(false)));
    }

    @Test
    void getRoomLeavesOutUnsetOptionalFields() throws Exception {
        // No gameId: no game is chosen, so there is no base URL or session either.
        createRoom("host-1", "Dragon's Den").andExpect(status().isCreated());
        String roomId = roomStore.rooms.get(0).getId();

        mockMvc.perform(get("/rooms/" + roomId).cookie(new Cookie("playerid", "host-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").doesNotExist())
                .andExpect(jsonPath("$.gameBaseUrl").doesNotExist())
                .andExpect(jsonPath("$.gameSessionId").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(content().string(not(containsString("null"))));
    }

    @Test
    void deleteRoomByAnotherPlayerIsForbiddenAndKeepsTheRoom() throws Exception {
        createRoom("host-1", "Dragon's Den").andExpect(status().isCreated());
        String roomId = roomStore.rooms.get(0).getId();

        mockMvc.perform(delete("/rooms/" + roomId).cookie(new Cookie("playerid", "guest-1")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/rooms/" + roomId))
                .andExpect(status().isOk());
    }

    @Test
    void deleteRoomByTheHostRemovesIt() throws Exception {
        createRoom("host-1", "Dragon's Den").andExpect(status().isCreated());
        String roomId = roomStore.rooms.get(0).getId();

        mockMvc.perform(delete("/rooms/" + roomId).cookie(new Cookie("playerid", "host-1")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/rooms/" + roomId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/rooms"))
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void deleteUnknownRoomIsNotFound() throws Exception {
        mockMvc.perform(delete("/rooms/no-such-room").cookie(new Cookie("playerid", "host-1")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("no-such-room")));
    }

    /** How a GWT host's room looks between RPC createRoom and publishRoom. */
    private void addPendingGwtRoom(String roomId, String hostId) {
        Room pending = new Room();
        pending.setId(roomId);
        pending.setName("Not Yet Published");
        pending.setCreatedByUserId(hostId);
        pending.setStatus(GameStatus.PENDING);
        roomStore.rooms.add(pending);
    }

    private ResultActions createRoom(String playerId, String name) throws Exception {
        return mockMvc.perform(post("/rooms")
                .cookie(new Cookie("playerid", playerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "hostName": "Ann", "profileId": "3"}""".formatted(name)));
    }
}
