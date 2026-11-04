import { Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-attribute-score',
  template: '<span class="score" [class]="range()">{{ value() ?? "—" }}</span>',
  styleUrl: './attribute-score.css',
})
export class AttributeScore {
  readonly value = input.required<number | null>();
  readonly range = computed(() => {
    const value = this.value();
    if (value === null) return 'missing';
    if (value < 40) return 'low';
    if (value < 70) return 'regular';
    if (value <= 85) return 'high';
    return 'excellent';
  });
}
