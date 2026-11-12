import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from "@angular/core";
import { DecimalPipe } from "@angular/common";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { interval, finalize } from "rxjs";
import { PlayerAwardsService, PlayerCeremony } from "../../services/player-awards.service";
import { PlayerPortrait } from "../player-portrait/player-portrait";
import { ClubCrest } from "../club-crest/club-crest";
import { TrophyImage } from "../trophy-image/trophy-image";
import { awardTimeline, AWARD_TIMING } from "../../utils/award-timeline";
import { SaveRealtimeService } from "../../services/save-realtime.service";
@Component({
  selector: "app-player-awards",
  imports: [DecimalPipe, PlayerPortrait, ClubCrest, TrophyImage],
  templateUrl: "./player-awards.html",
  styleUrl: "./player-awards.css",
})
export class PlayerAwards {
  readonly leagueId = input.required<string>();
  readonly suspended = input(false);
  private readonly api = inject(PlayerAwardsService);
  private readonly destroy = inject(DestroyRef);
  private readonly realtime = inject(SaveRealtimeService, { optional: true });
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>("dialog");
  private fetching = false;
  private automaticPaused = false;
  private readonly deferred = new Set<number>();
  private lastTick = performance.now();
  readonly active = signal<PlayerCeremony | null>(null);
  readonly elapsed = signal(0);
  readonly busy = signal(false);
  readonly error = signal("");
  readonly timeline = computed(() => awardTimeline(this.elapsed(), this.active()?.finalists.length || 0));
  readonly nominees = computed(() =>
    [...(this.active()?.finalists || [])]
      .sort((a, b) => a.player.name.localeCompare(b.player.name, "pt-BR"))
      .slice(0, this.timeline().nominees),
  );
  readonly results = computed(() =>
    [...(this.active()?.finalists || [])].sort((a, b) => b.place - a.place).slice(0, this.timeline().results),
  );
  readonly current = computed(() => this.results().at(-1) || null);
  readonly done = computed(() => this.timeline().phase === "done");
  readonly progress = computed(() =>
    Math.min(
      100,
      (this.elapsed() /
        (AWARD_TIMING.intro +
          (this.active()?.finalists.length || 0) * (AWARD_TIMING.nominee + AWARD_TIMING.result) +
          AWARD_TIMING.suspense)) *
        100,
    ),
  );
  constructor() {
    this.realtime?.gameEvents$.pipe(takeUntilDestroyed(this.destroy)).subscribe(() => this.load());
    effect(() => {
      this.leagueId();
      if (!this.suspended()) this.load();
    });
    effect(() => {
      this.active();
      this.suspended();
      queueMicrotask(() => this.syncDialog());
    });
    interval(250)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe(() => {
        const now = performance.now(),
          delta = Math.min(300, now - this.lastTick);
        this.lastTick = now;
        this.syncDialog();
        if (
          this.active() &&
          !this.suspended() &&
          !document.hidden &&
          this.dialog().nativeElement.open &&
          !this.done()
        )
          this.elapsed.update((value) => value + delta);
      });
    interval(8000)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe(() => this.load());
    this.api.replay$.pipe(takeUntilDestroyed(this.destroy)).subscribe((request) => {
      if (request.leagueId !== this.leagueId()) return;
      this.api
        .detail(request.leagueId, request.awardId)
        .pipe(takeUntilDestroyed(this.destroy))
        .subscribe({
          next: (award) => this.start(award),
          error: () => this.error.set("Não foi possível abrir esta cerimônia."),
        });
    });
  }
  private syncDialog() {
    if (this.destroy.destroyed) return;
    const modal = this.dialog().nativeElement;
    const another = Array.from(document.querySelectorAll<HTMLDialogElement>("dialog[open]")).some(
      (item) => item !== modal,
    );
    if (!this.active() || this.suspended() || another) {
      if (modal.open) modal.close();
      return;
    }
    if (!modal.open && !document.hidden) modal.showModal();
  }
  private start(award: PlayerCeremony) {
    this.active.set(award);
    this.elapsed.set(0);
    this.error.set("");
    this.lastTick = performance.now();
  }
  load() {
    if (
      this.fetching ||
      this.active() ||
      this.automaticPaused ||
      this.suspended() ||
      document.hidden ||
      !this.leagueId()
    )
      return;
    this.fetching = true;
    this.api
      .pending(this.leagueId())
      .pipe(
        finalize(() => (this.fetching = false)),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe({
        next: (awards) => {
          const award = awards.find((item) => !this.deferred.has(item.id));
          if (award) this.start(award);
        },
        error: () => {
          /* Next poll retries without consuming the presentation. */
        },
      });
  }
  later() {
    const award = this.active();
    if (!award || this.busy()) return;
    this.deferred.add(award.id);
    this.automaticPaused = true;
    this.active.set(null);
    this.load();
  }
  close() {
    const award = this.active();
    if (!award || !this.done() || this.busy()) return;
    this.busy.set(true);
    this.api
      .seen(this.leagueId(), award.id)
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe({
        next: () => {
          this.active.set(null);
          this.load();
        },
        error: () => this.error.set("Não foi possível confirmar a apresentação. Tente novamente."),
      });
  }
}
