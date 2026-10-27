package ADG.services;

import ADG.generated.api.ChatApi;
import ADG.generated.model.ChatMessageDto;
import ADG.generated.model.SendMessageRequestDto;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** The {@code chat} part of gameroom_openapi.yaml, plus its hand-written message stream. */
@RestController
// The tunnel URL is randomly assigned on each Pi restart, so a fixed origin cannot
// be configured. The risk of * is acceptable for a private server on an ephemeral URL.
@CrossOrigin(origins = "*")
public class ChatController implements ChatApi {

    private final Map<String, CopyOnWriteArrayList<Map<String, String>>> messages = new ConcurrentHashMap<>();

    @Autowired
    private ChatSseRegistry chatSseRegistry;

    @Override
    public ResponseEntity<Void> sendMessage(String roomId, SendMessageRequestDto body) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("sender", body.getSender());
        entry.put("message", body.getMessage());
        String timestampUTC = LocalTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("HH:mm"));
        entry.put("timestampUTC", timestampUTC);
        CopyOnWriteArrayList<Map<String, String>> roomMessages =
                messages.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>());
        roomMessages.add(entry);
        chatSseRegistry.emit(roomId, new ArrayList<>(roomMessages));
        return ResponseEntity.status(201).build();
    }

    @Override
    public ResponseEntity<List<ChatMessageDto>> getMessages(String roomId) {
        return ResponseEntity.ok(messages.getOrDefault(roomId, new CopyOnWriteArrayList<>()).stream()
                .map(m -> new ChatMessageDto(m.get("sender"), m.get("message"), m.get("timestampUTC")))
                .toList());
    }

    @GetMapping(value = "/chat/{roomId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessages(
            @PathVariable("roomId") String roomId,
            HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        List<Map<String, String>> current =
                new ArrayList<>(messages.getOrDefault(roomId, new CopyOnWriteArrayList<>()));
        SseEmitter emitter = chatSseRegistry.subscribe(roomId);
        try {
            emitter.send(SseEmitter.event().data(current, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }
}