import { Component, DestroyRef, computed, effect, inject, input, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { Subscription } from "rxjs";
import { TournamentsService, CupEdition, CupDetail } from "../../services/tournaments.service";
import { TrophyImage } from "../../components/trophy-image/trophy-image";
import { ClubCrest } from "../../components/club-crest/club-crest";
import { competitionTrophy } from "../../utils/trophies";
import { dateLabel } from "../../utils/game-calendar";
@Component({
  selector: "app-tournaments",
  imports: [TrophyImage, ClubCrest],
  templateUrl: "./tournaments.html",
  styleUrl: "./tournaments.css",
})
export class TournamentsPage {
  readonly leagueId = input.required<string>();
  readonly revision = input(0);
  private readonly api = inject(TournamentsService);
  private readonly destroy = inject(DestroyRef);
  private listRequest?: Subscription;
  private detailRequest?: Subscription;
  readonly editions = signal<CupEdition[]>([]);
  readonly detail = signal<CupDetail | null>(null);
  readonly selected = signal<number | null>(null);
  readonly stage = signal(0);
  readonly error = signal("");
  readonly loading = signal(false);
  readonly nextWorld = computed(() => {
    const year = Math.max(2026, ...this.editions().map((edition) => edition.year));
    return 2025 + 4 * (Math.floor((year - 2025) / 4) + 1);
  });
  readonly worldRegistered = computed(() => this.editions().some((edition) => edition.key === 'CLUB_WORLD_CUP' && edition.status !== 'FINISHED'));
  readonly trophy = competitionTrophy;
  readonly label = dateLabel;
  readonly games = computed(() => this.detail()?.fixtures.filter((g) => g.stage === this.stage()) || []);
  readonly tables = computed(() => {
    const rows = this.detail()?.standings?.filter((r) => r.stage === this.stage()) || [];
    return [...new Set(rows.map((r) => r.group))].map((name) => ({
      name,
      rows: rows.filter((r) => r.group === name),
    }));
  });
  constructor() {
    effect(() => {
      this.leagueId();
      this.revision();
      this.load();
    });
  }
  load(): void {
    this.listRequest?.unsubscribe();
    this.error.set("");
    this.loading.set(true);
    this.listRequest = this.api
      .list(this.leagueId())
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (editions) => {
          this.editions.set(editions);
          const first = editions.find((e) => e.id === this.selected()) || editions[0];
          if (first) this.select(first.id);
          else {
            this.detail.set(null);
            this.loading.set(false);
          }
        },
        error: (e) => {
          this.error.set(e.error?.message || "Não foi possível consultar as copas.");
          this.loading.set(false);
        },
      });
  }
  select(id: number): void {
    this.detailRequest?.unsubscribe();
    this.selected.set(id);
    this.detail.set(null);
    this.loading.set(true);
    this.detailRequest = this.api
      .detail(this.leagueId(), id)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (d) => {
          this.detail.set(d);
          this.stage.set(d.edition.currentStage);
          this.loading.set(false);
        },
        error: (e) => {
          this.error.set(e.error?.message || "Não foi possível abrir a competição.");
          this.loading.set(false);
        },
      });
  }
  team(id: number): string {
    return this.detail()?.teams.find((t) => t.id === id)?.name || "Classificado a definir";
  }
  winner(number: number): number | null {
    return this.detail()?.ties.find((t) => t.stage === this.stage() && t.number === number)?.winner || null;
  }
}
