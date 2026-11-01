package ADG.services;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import ADG.Lobby.Room;
import ADG.config.SpriteSheetsConfig;
import ADG.config.SpriteSheetsConfig.SheetDef;
import ADG.generated.api.PlayersApi;
import ADG.generated.model.JoinRoomRequestDto;
import ADG.generated.model.RoomDto;
import ADG.generated.model.SpriteSheetDto;
import ADG.services.RoomManager.Seat;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The {@code players} part of gameroom_openapi.yaml. The GWT client still joins rooms over RPC,
 * with the password checked in the browser; the Angular client uses joinRoom.
 */
@RestController
public class PlayersController implements PlayersApi {

    @Autowired
    private RoomStore roomStore;

    @Autowired
    private RoomSseRegistry sseRegistry;

    @Autowired
    private SpriteSheetsConfig spriteSheets;

    @Autowired
    private ProfilePicService profilePics;

    /** A proxy to the current request; the generated signatures leave out the playerid cookie. */
    @Autowired
    private HttpServletRequest request;

    @Autowired
    private RoomManager rooms;

    /** Joins the room, or changes the caller's name or picture in it; an admin needs no password. */
    @Override
    public ResponseEntity<RoomDto> joinRoom(String roomId, String playerId, JoinRoomRequestDto body) {
        if (!playerId.equals(PlayerCookie.idOf(request))) {
            throw new ResponseStatusException(FORBIDDEN, "You can only join a room as yourself.");
        }
        Seat seat = new Seat(playerId, body.getName(), body.getProfileId());
        Room room = rooms.joinRoom(roomId, seat, body.getPassword(), request.isUserInRole("ADMIN"));
        return ResponseEntity.ok(RoomDtos.detail(room, playerId));
    }

    /**
     * Lets a player remove themselves from a room (called by games when the player clicks
     * "leave / exit to lobby"). Only the player identified by their own {@code playerid}
     * cookie may remove themselves — no admin role required.
     */
    @Override
    public ResponseEntity<Void> leaveRoom(String roomId, String playerId) {
        String cookiePlayerId = PlayerCookie.idOf(request);
        if (cookiePlayerId == null || !cookiePlayerId.equals(playerId)) {
            return ResponseEntity.status(403).build();
        }

        Optional<Room> found = roomStore.rooms.stream()
                .filter(r -> r.getId().equals(roomId))
                .findFirst();

        if (found.isEmpty()) return ResponseEntity.notFound().build();

        Room room = found.get();
        synchronized (room) {
            room.removePlayer(playerId);
        }
        sseRegistry.emit(roomId, room);
        return ResponseEntity.noContent().build();
    }

    // Allow any origin — same reason as ChatController (random tunnel URL).
    @Override
    @CrossOrigin(origins = "*")
    public ResponseEntity<List<SpriteSheetDto>> getSpriteSheets() {
        return ResponseEntity.ok(spriteSheets.getSheets().stream().map(PlayersController::toDto).toList());
    }

    @Override
    @CrossOrigin(origins = "*")
    public ResponseEntity<Resource> getProfilePic(Integer index) {
        byte[] png = profilePics.png(index);
        if (png == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Cache-Control", "public, max-age=86400")
                .body(new ByteArrayResource(png));
    }

    private static SpriteSheetDto toDto(SheetDef sheet) {
        return new SpriteSheetDto(sheet.getUrl(), sheet.getCols(), sheet.getRows(),
                sheet.getImgWidth(), sheet.getImgHeight(), sheet.getInsetPx(),
                sheet.getExclude(), sheet.getOnlyInclude(), sheet.isBotOnly());
    }
}
