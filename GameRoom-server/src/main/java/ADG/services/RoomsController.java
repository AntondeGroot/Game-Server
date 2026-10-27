package ADG.services;

import ADG.generated.api.RoomsApi;
import ADG.generated.model.RoomNameDto;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * The {@code rooms} part of gameroom_openapi.yaml. Operations not overridden here still answer
 * 501 Not Implemented; the GWT client uses RoomService over RPC for them until they move here.
 * The lobby and room streams are hand-written in {@link LobbySseController} and
 * {@link RoomSseController}.
 */
@RestController
public class RoomsController implements RoomsApi {

    @Autowired
    private RoomNameService roomNames;

    @Override
    public ResponseEntity<RoomNameDto> getRandomRoomName() {
        return ResponseEntity.ok(new RoomNameDto(roomNames.randomName()));
    }

    @Override
    public ResponseEntity<List<String>> getRoomNames() {
        return ResponseEntity.ok(roomNames.allNames());
    }
}
