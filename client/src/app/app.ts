import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** The page shell. Until the lobby moves over (step 2) it only says where to play. */
@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {}
