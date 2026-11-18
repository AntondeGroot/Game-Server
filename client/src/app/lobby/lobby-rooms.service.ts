import { RoomsService } from '../../generated/api/api';
import { RoomSummary } from '../../generated/model/models';
import { Observable } from 'rxjs';
import { inject, Service } from '@angular/core';

@Service()
export class LobbyRoomsService {
  private readonly api = inject(RoomsService);

  rooms(): Observable<RoomSummary[]> {
    return this.api.getRooms();
  }
}
