import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { RoomSummary } from '../../generated/model/models';
import { Lobby } from './lobby';
import { LobbyRoomsService } from './lobby-rooms.service';

/** A room with valid defaults; a test only spells out what it's about. */
function room(overrides: Partial<RoomSummary> = {}): RoomSummary {
  return {
    id: 'r1',
    name: 'Room 1',
    status: 'WAITING',
    playerCount: 1,
    maxPlayers: 4,
    hasPassword: false,
    ...overrides,
  };
}

/** Renders the lobby with a fake service that answers with these rooms. */
async function renderLobby(rooms: RoomSummary[]): Promise<HTMLElement> {
  const fakeRooms: Pick<LobbyRoomsService, 'rooms'> = {
    rooms: () => of(rooms),
  };
  await TestBed.configureTestingModule({
    imports: [Lobby],
    providers: [{ provide: LobbyRoomsService, useValue: fakeRooms }],
  }).compileComponents();

  const fixture = TestBed.createComponent(Lobby);
  await fixture.whenStable();
  return fixture.nativeElement as HTMLElement;
}

describe('Lobby', () => {
  it('lists the room names', async () => {
    const page = await renderLobby([
      room({ id: 'a', name: 'Kitchen' }),
      room({ id: 'b', name: 'Attic' }),
    ]);

    const names = [...page.querySelectorAll('li')].map((li) => li.textContent?.trim());
    expect(names).toEqual(['Kitchen', 'Attic']);
  });

  it('says so when there are no rooms', async () => {
    const page = await renderLobby([]);

    expect(page.textContent).toContain('No rooms yet');
  });
});
