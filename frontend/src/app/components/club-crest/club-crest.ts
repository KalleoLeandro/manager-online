import { Directive, computed, input } from '@angular/core';

@Directive({selector:'img[appClubCrest]',host:{'[src]':'source()'}})
export class ClubCrest {
  readonly clubId=input.required<number>();
  readonly source=computed(()=>`/api/teams/${encodeURIComponent(String(this.clubId()))}/crest`);
}
