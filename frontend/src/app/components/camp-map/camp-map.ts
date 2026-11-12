import { Component, input, output } from "@angular/core";
import { CampVenue } from "../../services/club-events.service";
@Component({ selector: "app-camp-map", templateUrl: "./camp-map.html", styleUrl: "./camp-map.css" })
export class CampMap {
  readonly venues = input.required<CampVenue[]>();
  readonly selected = input("");
  readonly disabled = input(false);
  readonly choose = output<string>();
  x(v: CampVenue) {
    return (v.longitude + 180) * 2;
  }
  y(v: CampVenue) {
    return (90 - v.latitude) * 2;
  }
}
