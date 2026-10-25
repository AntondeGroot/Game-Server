package ADG.services;

import ADG.Lobby.Room;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
class RoomSseController {

    @Autowired
    private RoomSseRegistry registry;

    @Autowired
    private RoomStore roomStore;

    @GetMapping(value = "/rooms/{roomId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@PathVariable("roomId") String roomId,
                      @CookieValue(value = "playerid", required = false) String playerId,
                      HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");

        Room room = roomStore.rooms.stream()
                .filter(r -> r.getId().equals(roomId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        SseEmitter emitter = registry.subscribe(roomId, playerId);
        // Broadcast rather than send only to the newcomer: everyone else learns
        // that this player now has the room page open.
        registry.emit(roomId, room);
        return emitter;
    }
}