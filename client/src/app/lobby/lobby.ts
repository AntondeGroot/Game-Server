import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { LobbyRoomsService } from './lobby-rooms.service';

@Component({
  imports: [],
  selector: 'app-lobby',
  styleUrl: './lobby.scss',
  templateUrl: './lobby.html',
})
export class Lobby {
  private readonly roomsService = inject(LobbyRoomsService);
  readonly rooms = toSignal(this.roomsService.rooms(), { initialValue: [] });
}
