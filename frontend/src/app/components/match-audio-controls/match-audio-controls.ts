import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatchAudioService } from '../../services/match-audio.service';
import { GameIcon } from '../game-icon/game-icon';

@Component({selector:'app-match-audio-controls',imports:[FormsModule,GameIcon],templateUrl:'./match-audio-controls.html',styleUrl:'./match-audio-controls.css'})
export class MatchAudioControls {readonly audio=inject(MatchAudioService);}
