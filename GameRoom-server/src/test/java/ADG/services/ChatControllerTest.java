package ADG.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ── GET ──────────────────────────────────────────────────────────────────

    @Test
    void getMessagesReturnsEmptyListForNewRoom() throws Exception {
        mockMvc.perform(get("/chat/room-new"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── POST ─────────────────────────────────────────────────────────────────

    @Test
    void postMessageReturns201() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("sender", "Alice", "message", "Hello"));

        mockMvc.perform(post("/chat/room-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void postedMessageAppearsInGetResponse() throws Exception {
        String roomId = "room-b";
        String body = objectMapper.writeValueAsString(Map.of("sender", "Bob", "message", "Hi there"));

        mockMvc.perform(post("/chat/" + roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        mockMvc.perform(get("/chat/" + roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sender",  is("Bob")))
                .andExpect(jsonPath("$[0].message", is("Hi there")));
    }

    @Test
    void messageContainsTimestamp() throws Exception {
        String roomId = "room-c";
        String body = objectMapper.writeValueAsString(Map.of("sender", "Carol", "message", "Hey"));

        mockMvc.perform(post("/chat/" + roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        mockMvc.perform(get("/chat/" + roomId))
                .andExpect(jsonPath("$[0].timestampUTC", matchesPattern("\\d{2}:\\d{2}")));
    }

    @Test
    void multipleMessagesStoredInOrder() throws Exception {
        String roomId = "room-d";
        for (String msg : new String[]{"first", "second", "third"}) {
            String body = objectMapper.writeValueAsString(Map.of("sender", "Dave", "message", msg));
            mockMvc.perform(post("/chat/" + roomId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body));
        }

        mockMvc.perform(get("/chat/" + roomId))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].message", is("first")))
                .andExpect(jsonPath("$[1].message", is("second")))
                .andExpect(jsonPath("$[2].message", is("third")));
    }

    @Test
    void differentRoomsHaveIsolatedMessages() throws Exception {
        String bodyA = objectMapper.writeValueAsString(Map.of("sender", "Eve", "message", "Room A message"));
        String bodyB = objectMapper.writeValueAsString(Map.of("sender", "Eve", "message", "Room B message"));

        mockMvc.perform(post("/chat/room-e1").contentType(MediaType.APPLICATION_JSON).content(bodyA));
        mockMvc.perform(post("/chat/room-e2").contentType(MediaType.APPLICATION_JSON).content(bodyB));

        mockMvc.perform(get("/chat/room-e1"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].message", is("Room A message")));

        mockMvc.perform(get("/chat/room-e2"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].message", is("Room B message")));
    }

    @Test
    void missingSenderIsRejected() throws Exception {
        String roomId = "room-f";
        String body = objectMapper.writeValueAsString(Map.of("message", "no sender"));

        mockMvc.perform(post("/chat/" + roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/chat/" + roomId))
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
