import { Component, DestroyRef, computed, effect, inject, input, signal } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { Subscription } from "rxjs";
import { PlayerAwardsService, PlayerCeremony } from "../../services/player-awards.service";
import { TrophyImage } from "../trophy-image/trophy-image";
@Component({
  selector: "app-player-award-history",
  imports: [FormsModule, TrophyImage],
  templateUrl: "./player-award-history.html",
  styleUrl: "./player-award-history.css",
})
export class PlayerAwardHistory {
  readonly leagueId = input.required<string>();
  readonly revision = input(0);
  private readonly api = inject(PlayerAwardsService);
  private readonly destroy = inject(DestroyRef);
  private request?: Subscription;
  readonly awards = signal<PlayerCeremony[]>([]);
  readonly year = signal("");
  readonly kind = signal("");
  readonly loading = signal(false);
  readonly error = signal("");
  readonly years = computed(() => [...new Set(this.awards().map((a) => a.year))].sort((a, b) => b - a));
  readonly visible = computed(() =>
    this.awards().filter(
      (a) => (!this.year() || a.year === Number(this.year())) && (!this.kind() || a.key === this.kind()),
    ),
  );
  constructor() {
    effect(() => {
      this.leagueId();
      this.revision();
      this.load();
    });
  }
  load() {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.request = this.api
      .history(this.leagueId())
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (awards) => {
          this.awards.set(awards);
          this.loading.set(false);
        },
        error: () => {
          this.error.set("Não foi possível consultar as premiações.");
          this.loading.set(false);
        },
      });
  }
  replay(id: number) {
    this.api.replay(this.leagueId(), id);
  }
}
