package ADG.services;

import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoomSseControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RoomStore roomStore;

    @Autowired
    private RoomSseRegistry sseRegistry;

    @BeforeEach
    void clearRooms() {
        roomStore.rooms.removeIf(r -> !"Test Room".equals(r.getName()));
    }

    // ── endpoint contract ────────────────────────────────────────────────────

    @Test
    void setsXAccelBufferingNoHeader() throws Exception {
        Room room = seedRoom("Alpha");
        HttpURLConnection conn = openSse("/rooms/" + room.getId() + "/stream");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        assertThat(conn.getHeaderField("X-Accel-Buffering")).isEqualTo("no");
        conn.disconnect();
    }

    @Test
    void contentTypeIsTextEventStream() throws Exception {
        Room room = seedRoom("Beta");
        HttpURLConnection conn = openSse("/rooms/" + room.getId() + "/stream");
        assertThat(conn.getResponseCode()).isEqualTo(200);
        assertThat(conn.getHeaderField("Content-Type")).contains("text/event-stream");
        conn.disconnect();
    }

    @Test
    void returns404ForUnknownRoom() throws Exception {
        HttpURLConnection conn = openSse("/rooms/does-not-exist/stream");
        assertThat(conn.getResponseCode()).isEqualTo(404);
        conn.disconnect();
    }

    // ── initial event ────────────────────────────────────────────────────────

    @Test
    void sendsInitialRoomStateOnSubscribe() throws Exception {
        Room room = seedRoom("Gamma");
        HttpURLConnection conn = openSse("/rooms/" + room.getId() + "/stream");
        assertThat(conn.getResponseCode()).isEqualTo(200);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String dataLine = reader.readLine();
            assertThat(dataLine).startsWith("data:");
            assertThat(dataLine).contains("Gamma");
        }
        conn.disconnect();
    }

    // ── push update ──────────────────────────────────────────────────────────

    @Test
    void emitSendsUpdatesToSubscribers() throws Exception {
        Room room = seedRoom("Delta");
        HttpURLConnection conn = openSse("/rooms/" + room.getId() + "/stream");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            reader.readLine(); // data:{...}
            reader.readLine(); // empty separator

            room.addPlayer("player-1");
            room.addPlayerName("player-1", "Alice");
            sseRegistry.emit(room.getId(), room);

            String dataLine = reader.readLine();
            assertThat(dataLine).contains("Alice");
        }
        conn.disconnect();
    }

    // ── presence ─────────────────────────────────────────────────────────────

    @Test
    void reportsPlayersWithTheRoomPageOpen() throws Exception {
        Room room = seedRoom("Epsilon");
        HttpURLConnection conn = openSse("/rooms/" + room.getId() + "/stream", "playerid=player-1");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String dataLine = reader.readLine();
            assertThat(dataLine).contains("\"presentPlayerIds\":[\"player-1\"]");
            // Room fields sit at the top level, next to presentPlayerIds.
            assertThat(dataLine).contains("\"name\":\"Epsilon\"").doesNotContain("\"room\":");
        }
        conn.disconnect();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private HttpURLConnection openSse(String path) throws Exception {
        return openSse(path, null);
    }

    private HttpURLConnection openSse(String path, String cookie) throws Exception {
        URL url = new URL("http://localhost:" + port + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Accept", "text/event-stream");
        if (cookie != null) conn.setRequestProperty("Cookie", cookie);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.connect();
        return conn;
    }

    private Room seedRoom(String name) {
        Room r = new Room();
        r.setId(UUID.randomUUID().toString());
        r.setName(name);
        r.setCreatedByUserId("creator-1");
        r.setStatus(GameStatus.WAITING);
        r.setGameId("keezen");
        roomStore.rooms.add(r);
        return r;
    }
}