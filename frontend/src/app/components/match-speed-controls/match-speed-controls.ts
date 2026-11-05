import { Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({selector:'app-match-speed-controls',imports:[FormsModule],templateUrl:'./match-speed-controls.html',styleUrl:'./match-speed-controls.css'})
export class MatchSpeedControls {
 readonly percent=input(100);readonly allowed=input(false);readonly local=input(false);readonly busy=input(false);readonly changed=output<number>();
}
