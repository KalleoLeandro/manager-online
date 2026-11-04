import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Access, SaveService } from '../../services/save.service';
import { AuthService } from '../../services/auth.service';
import { LeagueCard } from '../../components/league-card/league-card';
import { LeagueDeleteConfirmation } from '../../components/league-delete-confirmation/league-delete-confirmation';
import { LeagueRetentionNotice } from '../../components/league-retention-notice/league-retention-notice';
import { navigate } from '../../utils/navigation';

@Component({selector:'app-leagues',imports:[LeagueCard,LeagueDeleteConfirmation,LeagueRetentionNotice],templateUrl:'./leagues.html',styleUrl:'./leagues.css'})
export class LeaguesPage implements OnInit {
  private readonly api=inject(SaveService);private readonly destroy=inject(DestroyRef);
  readonly account=inject(AuthService).session();
  readonly leagues=signal<Access[]>([]);readonly loading=signal(false);readonly deleting=signal(false);
  readonly pending=signal<Access|null>(null);readonly error=signal('');readonly notice=signal('');
  ngOnInit(){this.load();}
  load(){this.loading.set(true);this.error.set('');this.api.available().pipe(finalize(()=>this.loading.set(false)),takeUntilDestroyed(this.destroy)).subscribe({next:items=>this.leagues.set([...items].sort((a,b)=>(b.room.lastActivityAt||'').localeCompare(a.room.lastActivityAt||''))),error:e=>this.error.set(e.error?.message||'Não foi possível listar as ligas. Tente novamente.')});}
  open(access:Access){this.api.remember(access);navigate(`lobby/${access.room.id}`);}
  remove(){const access=this.pending();if(!access||this.deleting())return;this.deleting.set(true);this.error.set('');this.api.delete(access.room.id).pipe(finalize(()=>this.deleting.set(false)),takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.leagues.update(items=>items.filter(item=>item.room.id!==access.room.id));this.pending.set(null);this.notice.set('Liga excluída permanentemente.');},error:e=>{this.pending.set(null);this.error.set(e.error?.message||'Não foi possível excluir a liga. Tente novamente.');}});}
}
