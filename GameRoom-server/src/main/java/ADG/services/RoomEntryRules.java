package ADG.services;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;

import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import org.springframework.web.server.ResponseStatusException;

/** Who may join a room, under which name and with which picture. */
final class RoomEntryRules {

    private RoomEntryRules() {}

    /** A newcomer needs a room that is not playing or full, and its password unless they are an admin. */
    static void checkCanEnter(Room room, String password, boolean callerIsAdmin) {
        if (room.getStatus() == GameStatus.PLAYING) {
            throw new ResponseStatusException(CONFLICT, "The game in this room has already started.");
        }
        if (room.getStatus() == GameStatus.FULL) {
            throw new ResponseStatusException(CONFLICT, "This room is full.");
        }
        if (room.hasPassword() && !callerIsAdmin && !RoomPasswords.matches(room.getRoomPassword(), password)) {
            throw new ResponseStatusException(FORBIDDEN, "The password is wrong.");
        }
    }

    static void checkPlayerName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Player name cannot be empty.");
        }
        if (name.length() > Room.MAX_PLAYER_NAME_LENGTH) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Player name cannot be longer than " + Room.MAX_PLAYER_NAME_LENGTH + " characters.");
        }
    }

    /** In a room with unique pictures, no other player may already have this one. */
    static void checkPictureFree(Room room, String playerId, String profileId) {
        if (!room.isUniqueProfilePics()) return;
        boolean taken = room.getPlayerProfiles().entrySet().stream()
                .anyMatch(e -> !e.getKey().equals(playerId) && e.getValue().equals(profileId));
        if (taken) {
            throw new ResponseStatusException(CONFLICT, "Another player already has this picture.");
        }
    }
}
