import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RoomSummary } from '../../generated/model/models';
import { provideApi } from '../../generated/provide-api';
import { LobbyRoomsService } from './lobby-rooms.service';

describe('LobbyRoomsService', () => {
  let service: LobbyRoomsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi('')],
    });
    service = TestBed.inject(LobbyRoomsService);
    http = TestBed.inject(HttpTestingController);
  });

  // Fails the test if a request was made that no test expected.
  afterEach(() => http.verify());

  it('fetches the lobby rooms from GET /rooms', () => {
    const rooms: RoomSummary[] = [
      {
        id: 'a',
        name: 'Kitchen',
        status: 'WAITING',
        playerCount: 1,
        maxPlayers: 4,
        hasPassword: false,
      },
    ];
    let received: RoomSummary[] | undefined;

    service.rooms().subscribe((value) => (received = value));
    const request = http.expectOne('/rooms');
    request.flush(rooms);

    expect(request.request.method).toBe('GET');
    expect(received).toEqual(rooms);
  });
});
