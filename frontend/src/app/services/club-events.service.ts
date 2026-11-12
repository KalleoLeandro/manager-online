import { Injectable, inject } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { SaveService } from "./save.service";
export interface CampVenue {
  key: string;
  name: string;
  country: string;
  latitude: number;
  longitude: number;
  dailyCost: number;
  fatigue: number;
  focus: string[];
  description: string;
}
export interface ClubEventKind {
  key: string;
  name: string;
  description: string;
  cooldownDays: number;
  annual: boolean;
}
export interface EventOpponent {
  id: number;
  name: string;
  rating: number;
  human: boolean;
  fee: number;
}
export interface ClubBooking {
  id: number;
  teamId: number;
  teamName: string;
  opponentId: number | null;
  opponentName: string | null;
  type: string;
  date: string;
  endDate: string;
  venue: string | null;
  cost: number;
  status: string;
  fixtureId: number | null;
  processedDays: number;
  revenue: number;
  details: string;
}
export interface ClubEventOptions {
  teamId: number;
  currentDate: string;
  foundationDate: string | null;
  nextAnniversary: string | null;
  seasonEnd: string | null;
  cash: number;
  fanTrust: number;
  squadTrust: number;
  kinds: ClubEventKind[];
  venues: CampVenue[];
  opponents: EventOpponent[];
  bookings: ClubBooking[];
}
export interface ClubEventInput {
  type: string;
  date: string;
  days: number;
  opponentId: number | null;
  venue: string | null;
  foundationDate: string | null;
}
export interface ClubEventQuote {
  date: string;
  endDate: string;
  cost: number;
  description: string;
}
@Injectable({ providedIn: "root" })
export class ClubEventsService {
  private readonly http = inject(HttpClient);
  private readonly saves = inject(SaveService);
  private url(id: string) {
    return `/api/saves/${id}/management/calendar/events`;
  }
  private options(id: string) {
    return { headers: { Authorization: `Bearer ${this.saves.access(id)?.accessToken || ""}` } };
  }
  optionsFor(id: string) {
    return this.http.get<ClubEventOptions>(this.url(id), this.options(id));
  }
  quote(id: string, input: ClubEventInput) {
    return this.http.post<ClubEventQuote>(this.url(id) + "/quote", input, this.options(id));
  }
  create(id: string, input: ClubEventInput) {
    return this.http.post<ClubBooking>(this.url(id), input, this.options(id));
  }
  accept(id: string, event: number) {
    return this.http.post<ClubBooking>(this.url(id) + `/${event}/accept`, {}, this.options(id));
  }
  cancel(id: string, event: number) {
    return this.http.delete<ClubBooking>(this.url(id) + `/${event}`, this.options(id));
  }
}
