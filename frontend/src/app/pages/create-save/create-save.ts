import { Component, computed, effect, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { Catalog, CatalogService, Player } from '../../services/catalog.service';
import { Room, SaveService } from '../../services/save.service';
import { ClubCrest } from '../../components/club-crest/club-crest';
import { TeamPicker } from '../../components/team-picker/team-picker';
import { TeamRoster } from '../../components/team-roster/team-roster';
import { currentPath, navigate } from '../../utils/navigation';
import { readLocal, writeLocal } from '../../utils/local-storage';
import { SaveRealtimeService } from '../../services/save-realtime.service';
import { LiveStatus } from '../../components/live-status/live-status';
import { SavePresence } from '../../components/save-presence/save-presence';
import { LeagueRetentionNotice } from '../../components/league-retention-notice/league-retention-notice';

@Component({ selector: 'app-create-save', providers: [SaveRealtimeService], imports: [FormsModule, ClubCrest, TeamPicker, TeamRoster, LiveStatus, SavePresence,LeagueRetentionNotice], templateUrl: './create-save.html', styleUrl: './create-save.css' })
export class CreateSave implements OnInit {
  private readonly catalogApi = inject(CatalogService);
  private readonly saves = inject(SaveService);
  readonly realtime = inject(SaveRealtimeService);
  readonly otherPresence = computed(() => this.realtime.presence().filter(p => p.id !== this.realtime.selfId()));
  constructor() {
    effect(() => {
      const room=this.realtime.room();
      if (room) {
        this.invitation.set(room);
        if (room.members.some(m => m.teamId === this.selected())) {
          this.selected.set(null); this.players.set([]);
          this.error.set('Outro treinador confirmou este clube. Escolha outro disponível.');
          this.realtime.updatePresence({ teamId: null, activity: 'SELECTING_TEAM' });
        }
      }
    });
  }
  readonly catalog = signal<Catalog | null>(null);
  readonly selected = signal<number | null>(null);
  readonly players = signal<Player[]>([]);
  readonly team = computed(() => this.catalog()?.teams.find(t => t.id === this.selected()));
  readonly invitation = signal<Room | null>(null);
  readonly occupied = computed(() => this.invitation()?.members.map(m => m.teamId) || []);
  readonly loading = signal(false);
  readonly rosterLoading = signal(false);
  readonly error = signal('');
  readonly joined = signal(currentPath().startsWith('join'));
  readonly countOptions = Array.from({ length: 20 }, (_, i) => i + 1);
  readonly lastSave = this.saves.lastSave();
  saveName = 'Liga dos amigos';
  managerName = readLocal<string>('manager-name') || '';
  capacity = 4;
  code = currentPath().split('/')[1] || '';
  navigate = navigate;

  ngOnInit(): void {
    this.catalogApi.catalog$.subscribe({ next: data => this.catalog.set(data), error: () => this.error.set('Não foi possível carregar os clubes. Verifique o servidor e recarregue a página.') });
    if (this.code && this.joined()) this.findInvite();
  }
  choose(id: number): void {
    this.selected.set(id); this.players.set([]); this.rosterLoading.set(true);
    this.realtime.updatePresence({ teamId: id, name: this.managerName, activity: 'VIEWING_ROSTER' });
    this.catalogApi.players(id).subscribe({ next: players => { if (this.selected() === id) { this.players.set(players); this.rosterLoading.set(false); } }, error: () => { if (this.selected() === id) { this.error.set('Não foi possível carregar o plantel. Selecione o clube novamente.'); this.rosterLoading.set(false); } } });
  }
  findInvite(): void {
    if (!this.code.trim()) { this.error.set('Informe o código da liga.'); return; }
    this.realtime.disconnect();
    this.loading.set(true); this.error.set(''); this.invitation.set(null); this.selected.set(null); this.players.set([]);
    this.saves.invite(this.code).pipe(finalize(() => this.loading.set(false))).subscribe({ next: room => {
      this.invitation.set(room);
      this.realtime.connect({ inviteCode: room.inviteCode, name: this.managerName || 'Convidado', teamId: null, activity: 'SELECTING_TEAM' });
      if (room.members.length >= room.capacity) this.error.set('Esta liga já está completa.');
    }, error: e => this.error.set(e.error?.message || 'Não foi possível encontrar a liga.') });
  }
  submit(): void {
    this.error.set('');
    if (!this.selected() || this.managerName.trim().length < 2 || (!this.joined() && this.saveName.trim().length < 2)) { this.error.set('Informe seu nome, o nome da liga e escolha um clube para continuar.'); return; }
    if (this.joined() && !this.invitation()) { this.error.set('Busque o convite antes de escolher seu clube.'); return; }
    if (this.loading()) return;
    this.loading.set(true);
    const request = this.joined()
      ? this.saves.join(this.invitation()!.inviteCode, this.managerName.trim(), this.selected()!)
      : this.saves.create({ name: this.saveName.trim(), managerName: this.managerName.trim(), capacity: +this.capacity, teamId: this.selected()! });
    request.pipe(finalize(() => this.loading.set(false))).subscribe({ next: access => {
      writeLocal('manager-name',this.managerName.trim()); navigate(`lobby/${access.room.id}`);
    }, error: e => this.error.set(e.error?.message || 'Não foi possível salvar. Verifique a conexão e tente novamente.') });
  }
}
