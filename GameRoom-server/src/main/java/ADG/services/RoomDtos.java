package ADG.services;

import ADG.Lobby.Room;
import ADG.generated.model.PlayerDto;
import ADG.generated.model.RoomDto;
import ADG.generated.model.RoomSettingsDto;
import ADG.generated.model.RoomStatusDto;
import ADG.generated.model.RoomSummaryDto;
import java.util.List;

/** Converts the Room that GWT-RPC shares with the client into the API's room types. */
final class RoomDtos {

    private RoomDtos() {}

    static RoomSummaryDto summary(Room room) {
        return new RoomSummaryDto(room.getId(), room.getName(), status(room),
                room.getNrOfPlayers(), room.getMaxPlayers(), room.hasPassword())
                .gameId(room.getGameId());
    }

    /** The room as {@code callerId} sees it: only the host gets the password. */
    static RoomDto detail(Room room, String callerId) {
        boolean callerIsHost = room.getCreatedByUserId().equals(callerId);
        return new RoomDto(room.getId(), room.getName(), room.getCreatedByUserId(), status(room),
                players(room), room.getMinPlayers(), room.getMaxPlayers(), room.isUniqueProfilePics(),
                new RoomSettingsDto(room.isAnyPlayerCanSelectGame(), room.isAnyPlayerCanSetOptions()),
                room.getGameOptions(), room.hasPassword(), room.isEmbeddedSettings())
                .gameId(room.getGameId())
                .gameBaseUrl(room.getGameBaseUrl())
                .gameSessionId(room.getGameSessionId())
                .password(callerIsHost && room.hasPassword() ? room.getRoomPassword() : null);
    }

    private static List<PlayerDto> players(Room room) {
        return room.getPlayerIds().stream()
                .map(id -> new PlayerDto(id, room.getPlayerNames().get(id), room.getPlayerProfiles().get(id)))
                .toList();
    }

    /** A pending room is never sent: the lobby and lookups leave it out. */
    private static RoomStatusDto status(Room room) {
        return RoomStatusDto.fromValue(room.getStatus().name());
    }
}
