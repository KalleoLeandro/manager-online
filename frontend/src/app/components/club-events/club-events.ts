import { Component, DestroyRef, computed, effect, inject, input, output, signal } from "@angular/core";
import { CurrencyPipe } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import {
  ClubEventsService,
  ClubEventOptions,
  ClubEventInput,
  ClubEventQuote,
  ClubBooking,
} from "../../services/club-events.service";
import { ClubCrest } from "../club-crest/club-crest";
import { CampMap } from "../camp-map/camp-map";
import { dateLabel } from "../../utils/game-calendar";
import { Subscription } from "rxjs";
@Component({
  selector: "app-club-events",
  imports: [FormsModule, CurrencyPipe, ClubCrest, CampMap],
  templateUrl: "./club-events.html",
  styleUrl: "./club-events.css",
})
export class ClubEvents {
  readonly leagueId = input.required<string>();
  readonly revision = input(0);
  readonly disabled = input(false);
  readonly changed = output<void>();
  readonly navigate = output<string>();
  private readonly api = inject(ClubEventsService);
  private readonly destroy = inject(DestroyRef);
  private request?: Subscription;
  readonly data = signal<ClubEventOptions | null>(null);
  readonly expanded = signal(false);
  readonly kind = signal("FRIENDLY");
  readonly date = signal("");
  readonly days = signal(3);
  readonly venue = signal("ATIBAIA");
  readonly opponent = signal<number | null>(null);
  readonly search = signal("");
  readonly foundation = signal("");
  readonly quote = signal<ClubEventQuote | null>(null);
  readonly quoteError = signal("");
  readonly error = signal("");
  readonly success = signal("");
  readonly busy = signal(false);
  readonly loading = signal(false);
  readonly quoting = signal(false);
  readonly label = dateLabel;
  readonly selectedKind = computed(() => this.data()?.kinds.find((k) => k.key === this.kind()));
  readonly selectedVenue = computed(() => this.data()?.venues.find((v) => v.key === this.venue()));
  readonly opponents = computed(() => {
    const term = this.search()
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .toLowerCase();
    return (this.data()?.opponents || [])
      .filter((o) =>
        o.name
          .normalize("NFD")
          .replace(/[\u0300-\u036f]/g, "")
          .toLowerCase()
          .includes(term),
      )
      .slice(0, 24);
  });
  readonly bookings = computed(() =>
    [...(this.data()?.bookings || [])].sort((a, b) => {
      const active = (v: ClubBooking) => (["PROPOSED", "SCHEDULED"].includes(v.status) ? 0 : 1);
      return (
        active(a) - active(b) ||
        (active(a) === 0 ? a.date.localeCompare(b.date) : b.date.localeCompare(a.date))
      );
    }),
  );
  readonly endDate = computed(() => {
    if (!this.date()) return "";
    const d = new Date(this.date() + "T12:00:00Z");
    d.setUTCDate(d.getUTCDate() + (this.kind() === "CAMP" ? this.days() - 1 : 0));
    return d.toISOString().slice(0, 10);
  });
  readonly attr: Record<string, string> = {
    stamina: "Resistência",
    teamwork: "Trabalho em equipe",
    short_passing: "Passes curtos",
    technique: "Técnica",
    finishing: "Finalização",
    off_ball: "Movimentação",
    dribbling: "Drible",
    vision: "Visão de jogo",
    strength: "Força",
    tackling: "Desarme",
    marking: "Marcação",
    acceleration: "Aceleração",
    pace: "Velocidade",
    composure: "Controle emocional",
    concentration: "Concentração",
    decisions: "Decisões",
  };
  constructor() {
    effect(() => {
      this.leagueId();
      this.revision();
      this.load();
    });
    effect((onCleanup) => {
      this.kind();
      this.date();
      this.days();
      this.venue();
      this.opponent();
      this.foundation();
      if (!this.expanded() || !this.data() || !this.date()) return;
      this.quote.set(null);
      this.quoteError.set("");
      this.quoting.set(true);
      let request: Subscription | undefined;
      const timer = setTimeout(() => {
        request = this.api
          .quote(this.leagueId(), this.input())
          .pipe(takeUntilDestroyed(this.destroy))
          .subscribe({
            next: (q) => {
              this.quote.set(q);
              this.quoting.set(false);
            },
            error: (e) => {
              this.quoteError.set(e.error?.message || "Confira a data e as opções do evento.");
              this.quoting.set(false);
            },
          });
      }, 300);
      onCleanup(() => {
        clearTimeout(timer);
        request?.unsubscribe();
      });
    });
  }
  load() {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.request = this.api
      .optionsFor(this.leagueId())
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (d) => {
          this.data.set(d);
          if (!this.date() || this.date() < d.currentDate) this.date.set(d.currentDate);
          this.foundation.set(d.foundationDate || this.foundation());
          this.loading.set(false);
        },
        error: (e) => {
          this.error.set(e.error?.message || "Não foi possível carregar os eventos.");
          this.loading.set(false);
        },
      });
  }
  choose(kind: string) {
    this.kind.set(kind);
    this.error.set("");
    this.success.set("");
    const d = this.data();
    if (kind === "ANNIVERSARY") {
      if (d?.nextAnniversary) {
        this.date.set(d.nextAnniversary);
        this.navigate.emit(d.nextAnniversary);
      } else this.foundationChange(this.foundation());
    } else if (["SEASON_PARTY", "PLAYER_AWARDS"].includes(kind) && d?.seasonEnd) {
      const date = new Date(d.seasonEnd + "T12:00:00Z");
      date.setUTCDate(date.getUTCDate() + 1);
      const value = date.toISOString().slice(0, 10);
      this.date.set(value < d.currentDate ? d.currentDate : value);
      this.navigate.emit(this.date());
    }
  }
  foundationChange(value: string) {
    this.foundation.set(value);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || !this.data()) return;
    let year = Number(this.data()!.currentDate.slice(0, 4));
    let date = year + value.slice(4);
    if (date < this.data()!.currentDate) date = year + 1 + value.slice(4);
    if (value.endsWith("-02-29") && new Date(date + "T12:00:00Z").getUTCMonth() !== 1)
      date = date.slice(0, 4) + "-02-28";
    this.date.set(date);
    this.navigate.emit(date);
  }
  input(): ClubEventInput {
    return {
      type: this.kind(),
      date: this.date(),
      days: this.kind() === "CAMP" ? Number(this.days()) : 1,
      opponentId: this.kind() === "FRIENDLY" ? this.opponent() : null,
      venue: this.kind() === "CAMP" ? this.venue() : null,
      foundationDate:
        this.kind() === "ANNIVERSARY" && !this.data()?.foundationDate ? this.foundation() || null : null,
    };
  }
  create() {
    if (this.busy() || !this.quote() || this.disabled()) return;
    this.busy.set(true);
    this.error.set("");
    this.api
      .create(this.leagueId(), this.input())
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (e) => {
          this.busy.set(false);
          this.expanded.set(false);
          this.success.set(
            e.status === "PROPOSED"
              ? "Convite enviado. O adversário precisa aceitar o amistoso."
              : "Evento reservado no calendário do clube.",
          );
          this.navigate.emit(e.date);
          this.changed.emit();
          this.load();
        },
        error: (e) => {
          this.busy.set(false);
          this.error.set(e.error?.message || "Não foi possível organizar o evento.");
        },
      });
  }
  action(event: ClubBooking, accept = false) {
    if (this.busy() || this.disabled()) return;
    this.busy.set(true);
    this.error.set("");
    (accept ? this.api.accept(this.leagueId(), event.id) : this.api.cancel(this.leagueId(), event.id))
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.success.set(
            accept
              ? "Amistoso confirmado."
              : "Evento cancelado. O valor não utilizado foi devolvido ao organizador.",
          );
          this.changed.emit();
          this.load();
        },
        error: (e) => {
          this.busy.set(false);
          this.error.set(e.error?.message || "Não foi possível alterar o evento.");
        },
      });
  }
  name(type: string) {
    return this.data()?.kinds.find((k) => k.key === type)?.name || type;
  }
  status(event: ClubBooking) {
    return event.status === "PROPOSED"
      ? "Aguardando aceite"
      : event.status === "COMPLETED"
        ? "Concluído"
        : event.status === "CANCELLED"
          ? "Cancelado"
          : event.processedDays > 0
            ? "Em andamento"
            : "Agendado";
  }
}
