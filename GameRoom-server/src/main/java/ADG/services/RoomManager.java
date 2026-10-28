package ADG.services;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import ADG.Lobby.GameStatus;
import ADG.Lobby.Room;
import ADG.config.GamesConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The room rules shared by the REST controllers and the GWT-RPC {@link RoomServiceImpl}, which
 * delegates here until the switch to Angular deletes it. A broken rule is thrown as a
 * {@link ResponseStatusException}: {@link ApiErrorHandler} turns it into a problem response, and
 * RoomServiceImpl into the RoomServiceException the GWT client shows.
 */
@Service
public class RoomManager {

    private static final int MIN_NAME_LENGTH = 3;

    @Autowired
    private GamesConfig gamesConfig;

    @Autowired
    private RoomStore roomStore;

    @Autowired
    private RoomSseRegistry sseRegistry;

    @Autowired
    private LobbySseRegistry lobbySseRegistry;

    /** The rooms in the lobby: all but those a GWT host is still setting up. */
    public synchronized List<Room> lobbyRooms() {
        return roomStore.rooms.stream()
                .filter(r -> r.getStatus() != GameStatus.PENDING)
                .toList();
    }

    public synchronized Optional<Room> findRoom(String roomId) {
        return roomStore.rooms.stream()
                .filter(r -> r.getId().equals(roomId))
                .findFirst();
    }

    /** The lobby room with this id; a room its GWT host is still setting up does not count. */
    public synchronized Room lobbyRoom(String roomId) {
        return findRoom(roomId)
                .filter(r -> r.getStatus() != GameStatus.PENDING)
                .orElseThrow(() -> roomNotFound(roomId));
    }

    /** Opens a room with the caller as its host and first player, and shows it in the lobby. */
    public synchronized Room openRoom(String name, String gameId, String hostId, String hostName, String profileId) {
        // Not Room(name, creator): that constructor gets its id from GWT JavaScript, which only
        // runs in the browser.
        Room room = new Room();
        room.setId(UUID.randomUUID().toString());
        room.setName(name);
        room.setCreatedByUserId(hostId);
        room.setGameId(gameId);
        room.addPlayer(hostId);
        room.addPlayerName(hostId, hostName);
        room.addPlayerProfile(hostId, profileId);
        addRoom(room, GameStatus.WAITING);
        if (room.getNrOfPlayers() >= room.getMaxPlayers()) {
            room.setStatus(GameStatus.FULL);
        }
        emitLobbyUpdate();
        return room;
    }

    /** The GWT flow: the room stays out of the lobby until its host joins and publishes it. */
    public synchronized Room addPendingRoom(Room room) {
        addRoom(room, GameStatus.PENDING);
        return room;
    }

    /** Deletes the room; only its host may. */
    public synchronized void deleteRoom(String roomId, String callerId) {
        Room room = findRoom(roomId).orElseThrow(() -> roomNotFound(roomId));
        if (!room.getCreatedByUserId().equals(callerId)) {
            throw new ResponseStatusException(FORBIDDEN, "Only the host can delete the room.");
        }
        roomStore.deleteRoom(roomId);
        sseRegistry.emitClosed(roomId);
        emitLobbyUpdate();
    }

    /**
     * Takes the player out of the room, unless it is playing. The next player becomes host when
     * the host leaves.
     */
    public synchronized void removePlayer(String playerId, String roomId) {
        for (Room room : roomStore.rooms) {
            if (room.getId().equals(roomId)) {
                if (room.getStatus() == GameStatus.PLAYING) return;
                room.removePlayer(playerId);
                if (room.getStatus() == GameStatus.FULL && room.getNrOfPlayers() < room.getMaxPlayers()) {
                    room.setStatus(GameStatus.WAITING);
                }
                if (playerId.equals(room.getCreatedByUserId()) && !room.getPlayerIds().isEmpty()) {
                    room.setCreatedByUserId(room.getPlayerIds().get(0));
                }
            }
        }
        emitRoomUpdate(roomId);
        emitLobbyUpdate();
    }

    public void emitLobbyUpdate() {
        lobbySseRegistry.emit(lobbyRooms());
    }

    public void emitRoomUpdate(String roomId) {
        findRoom(roomId).ifPresent(r -> sseRegistry.emit(roomId, r));
    }

    private void addRoom(Room room, GameStatus status) {
        checkName(room.getName());
        leaveOtherRoom(room.getCreatedByUserId());
        gamesConfig.findById(room.getGameId()).ifPresent(game -> {
            room.setMinPlayers(game.getMinPlayers());
            room.setMaxPlayers(game.getMaxPlayers());
            room.setGameBaseUrl(game.getBaseUrl());
        });
        room.setStatus(status);
        roomStore.rooms.add(room);
    }

    private void checkName(String name) {
        if (name.isBlank() || name.trim().length() < MIN_NAME_LENGTH) {
            throw new ResponseStatusException(BAD_REQUEST, "Room name must be at least 3 characters.");
        }
        boolean nameAlreadyExists = roomStore.rooms.stream()
                .anyMatch(r -> r.getName().equalsIgnoreCase(name.trim()));
        if (nameAlreadyExists) {
            throw new ResponseStatusException(CONFLICT, "A room with this name already exists.");
        }
    }

    /** A player is in one room at a time; a game in progress keeps its players. */
    private void leaveOtherRoom(String playerId) {
        roomStore.rooms.stream()
                .filter(r -> r.getPlayerIds().contains(playerId) && r.getStatus() != GameStatus.PLAYING)
                .findFirst()
                .ifPresent(r -> removePlayer(playerId, r.getId()));
    }

    private static ResponseStatusException roomNotFound(String roomId) {
        return new ResponseStatusException(NOT_FOUND, "There is no room " + roomId + ".");
    }
}
