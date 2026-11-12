import { Component, DestroyRef, effect, inject, input, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { Subscription } from "rxjs";
import { TournamentsService, ClubTitle } from "../../services/tournaments.service";
import { TrophyImage } from "../../components/trophy-image/trophy-image";
import { dateLabel } from "../../utils/game-calendar";
import { PlayerAwardHistory } from "../../components/player-award-history/player-award-history";
@Component({
  selector: "app-club-titles",
  imports: [TrophyImage,PlayerAwardHistory],
  templateUrl: "./club-titles.html",
  styleUrl: "./club-titles.css",
})
export class ClubTitlesPage {
  readonly leagueId = input.required<string>();
  readonly teamId = input.required<number>();
  readonly revision = input(0);
  readonly titles = signal<ClubTitle[]>([]);
  readonly error = signal("");
  readonly loading = signal(false);
  readonly label = dateLabel;
  private readonly api = inject(TournamentsService);
  private readonly destroy = inject(DestroyRef);
  private request?: Subscription;
  constructor() {
    effect(() => {
      this.leagueId();
      this.teamId();
      this.revision();
      this.load();
    });
  }
  load(): void {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.error.set("");
    this.request = this.api
      .titles(this.leagueId(), this.teamId())
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (titles) => {
          this.titles.set(titles);
          this.loading.set(false);
        },
        error: (e) => {
          this.error.set(e.error?.message || "Não foi possível consultar as conquistas.");
          this.loading.set(false);
        },
      });
  }
}
