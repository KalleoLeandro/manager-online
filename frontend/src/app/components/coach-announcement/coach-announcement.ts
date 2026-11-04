import { AfterViewInit, Component, ElementRef, input, output, viewChild } from '@angular/core';
import { ManagerAvatar } from '../manager-avatar/manager-avatar';
import { ClubCrest } from '../club-crest/club-crest';

@Component({selector:'app-coach-announcement',imports:[ManagerAvatar,ClubCrest],templateUrl:'./coach-announcement.html',styleUrl:'./coach-announcement.css'})
export class CoachAnnouncement implements AfterViewInit {
  readonly coach=input.required<string>();readonly team=input.required<string>();readonly teamId=input.required<number>();
  readonly league=input.required<string>();readonly round=input.required<number>();readonly photoUrl=input<string|null|undefined>(null);
  readonly saving=input(false);readonly error=input('');readonly dismissed=output<void>();
  readonly surname=input<string|null|undefined>(null);
  private readonly dialog=viewChild.required<ElementRef<HTMLDialogElement>>('newspaper');
  ngAfterViewInit():void{this.dialog().nativeElement.showModal();}
  dismiss(event?:Event):void{event?.preventDefault();if(!this.saving())this.dismissed.emit();}
}
