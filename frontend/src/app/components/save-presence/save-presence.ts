import { Component, input } from '@angular/core';
import { Presence } from '../../services/save-realtime.service';
import { presenceLabel } from '../../utils/presence-label';

@Component({ selector: 'app-save-presence', template: `
  @if (people().length) {
    <section class="presence" aria-label="Atividade dos participantes" aria-live="polite">
      <h3>{{ title() }}</h3>
      @for (person of people(); track person.id) {
        <div><i></i><p><strong>{{ person.name }}</strong><span>{{ label(person.activity) }}{{ person.teamName ? ' · ' + person.teamName : '' }}</span></p></div>
      }
    </section>
  }`, styles: `
    .presence{border:1px solid #384d30;background:#c0f46b06;padding:16px;border-radius:8px;margin:20px 0}.presence h3{font-size:11px;color:var(--green);margin:0 0 12px}.presence>div{display:flex;gap:9px;align-items:center;margin-top:10px}.presence i{width:5px;height:5px;border-radius:50%;background:var(--green);flex-shrink:0}.presence p{margin:0}.presence strong{font-size:11px;font-weight:500;display:block}.presence span{font-size:10px;color:var(--muted);display:block;margin-top:4px;line-height:1.6}
  ` })
export class SavePresence {
  readonly people = input.required<Presence[]>();
  readonly title = input('Atividade ao vivo');
  label = presenceLabel;
}
