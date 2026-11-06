import {LeagueExit} from '../../components/league-exit/league-exit';
import { LocalPlayers } from '../../components/local-players/local-players';
import { Component, DestroyRef, computed, effect, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { SaveService } from '../../services/save.service';
import { SaveRealtimeService } from '../../services/save-realtime.service';
import { presenceLabel } from '../../utils/presence-label';
import { ClubCrest } from '../../components/club-crest/club-crest';
import { LiveStatus } from '../../components/live-status/live-status';
import { SavePresence } from '../../components/save-presence/save-presence';
import { LeagueRetentionNotice } from '../../components/league-retention-notice/league-retention-notice';
import { currentPath } from '../../utils/navigation';

@Component({ selector: 'app-lobby', providers: [SaveRealtimeService], imports:[LeagueExit,LocalPlayers,ClubCrest, LiveStatus, SavePresence,LeagueRetentionNotice], templateUrl: './lobby.html', styleUrl: './lobby.css' })
export class Lobby implements OnInit {
  private readonly api = inject(SaveService);
  private readonly destroy = inject(DestroyRef);
  readonly id = currentPath().split('/')[1] || '';
  readonly access = this.api.access(this.id);
  readonly realtime = inject(SaveRealtimeService);
  readonly room = this.realtime.room;
  readonly guests = computed(() => this.realtime.presence().filter(p => !p.memberId));
  presenceLabel = presenceLabel;
  presenceFor(id: string) { return this.realtime.presence().find(p => p.memberId === id); }
  readonly error = signal('');
  readonly notice = signal('');
  readonly loading = signal(false);
  readonly me = computed(() => this.room()?.members.find(m => m.id === this.access?.memberId));
  readonly emptySlots = computed(() => Array.from({ length: Math.max(0,(this.room()?.capacity || 0)-(this.room()?.members.length || 0)) }));
  ngOnInit(): void {
    if (!this.access) { this.error.set('Este navegador não possui acesso a essa liga. Entre usando o código de convite.'); return; }
    this.realtime.connect({ saveId: this.id, accessToken: this.access.accessToken, activity: 'LOBBY' });
  }
  ready(): void {
    if (this.loading()||(this.room()?.capacity||1)===1||this.room()?.localMultiplayer) return;
    this.loading.set(true);
    this.api.ready(this.id,!this.me()?.ready).pipe(finalize(() => this.loading.set(false)),takeUntilDestroyed(this.destroy)).subscribe({ error: e => this.error.set(e.error?.message || 'Não foi possível alterar sua confirmação.') });
  }
  async copyInvite(): Promise<void> {
    const code=this.room()?.inviteCode;
    if (!code) return;
    const url = `${window.location.origin}${window.location.pathname}#join/${code}`;
    try { await navigator.clipboard.writeText(url); this.notice.set('Link copiado. Envie para seus amigos.'); }
    catch { this.notice.set(`Compartilhe este código: ${code}`); }
  }
}
