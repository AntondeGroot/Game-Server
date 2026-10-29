package ADG.services;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import ADG.Lobby.Room;
import ADG.generated.api.RoomsApi;
import ADG.generated.model.CreateRoomRequestDto;
import ADG.generated.model.RoomDto;
import ADG.generated.model.RoomNameDto;
import ADG.generated.model.RoomPasswordDto;
import ADG.generated.model.RoomSettingsDto;
import ADG.generated.model.RoomSummaryDto;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The {@code rooms} part of gameroom_openapi.yaml. Operations not overridden here still answer
 * 501 Not Implemented; the GWT client uses RoomService over RPC for them until they move here.
 * The lobby and room streams are hand-written in {@link LobbySseController} and
 * {@link RoomSseController}.
 */
@RestController
public class RoomsController implements RoomsApi {

    @Autowired
    private RoomManager rooms;

    @Autowired
    private RoomNameService roomNames;

    /** A proxy to the current request; the generated signatures leave out the playerid cookie. */
    @Autowired
    private HttpServletRequest request;

    @Override
    public ResponseEntity<List<RoomSummaryDto>> getRooms() {
        return ResponseEntity.ok(rooms.lobbyRooms().stream().map(RoomDtos::summary).toList());
    }

    @Override
    public ResponseEntity<RoomDto> createRoom(CreateRoomRequestDto body) {
        String callerId = PlayerCookie.idOf(request);
        if (callerId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "The playerid cookie is missing.");
        }
        Room room = rooms.openRoom(body.getName(), body.getGameId(), callerId, body.getHostName(), body.getProfileId());
        return ResponseEntity.created(URI.create("/rooms/" + room.getId())).body(RoomDtos.detail(room, callerId));
    }

    @Override
    public ResponseEntity<RoomDto> getRoom(String roomId) {
        return ResponseEntity.ok(RoomDtos.detail(rooms.lobbyRoom(roomId), PlayerCookie.idOf(request)));
    }

    @Override
    public ResponseEntity<Void> deleteRoom(String roomId) {
        rooms.deleteRoom(roomId, PlayerCookie.idOf(request), request.isUserInRole("ADMIN"));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<RoomDto> setRoomSettings(String roomId, RoomSettingsDto settings) {
        String callerId = PlayerCookie.idOf(request);
        Room room = rooms.changeSettings(roomId, callerId,
                settings.getAnyPlayerCanSelectGame(), settings.getAnyPlayerCanSetOptions());
        return ResponseEntity.ok(RoomDtos.detail(room, callerId));
    }

    @Override
    public ResponseEntity<RoomPasswordDto> generateRoomPassword(String roomId) {
        String password = rooms.protectWithGeneratedPassword(roomId, PlayerCookie.idOf(request));
        return ResponseEntity.ok(new RoomPasswordDto(password));
    }

    @Override
    public ResponseEntity<RoomPasswordDto> setRoomPassword(String roomId, RoomPasswordDto body) {
        String stored = rooms.protectWithPassword(roomId, PlayerCookie.idOf(request), body.getPassword());
        return ResponseEntity.ok(new RoomPasswordDto(stored));
    }

    @Override
    public ResponseEntity<Void> removeRoomPassword(String roomId) {
        rooms.removePassword(roomId, PlayerCookie.idOf(request));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<RoomNameDto> getRandomRoomName() {
        return ResponseEntity.ok(new RoomNameDto(roomNames.randomName()));
    }

    @Override
    public ResponseEntity<List<String>> getRoomNames() {
        return ResponseEntity.ok(roomNames.allNames());
    }
}
