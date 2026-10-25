package ADG.services;

import ADG.Lobby.Room;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.Set;

/**
 * One frame on the room stream: the room state plus the players who currently
 * have the room page open. A seated player missing from {@code presentPlayerIds}
 * has closed the tab or is still on the page of a previous game.
 */
record RoomStreamEvent(@JsonUnwrapped Room room, Set<String> presentPlayerIds) {
}
