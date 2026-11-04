import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { Room } from './save.service';

export type Activity = 'SELECTING_TEAM' | 'VIEWING_ROSTER' | 'LOBBY' | 'MANAGING_TEAM' | 'READY';
export interface Presence { id: string; memberId: string | null; name: string; teamId: number | null; teamName: string | null; activity: Activity }
export interface GameEvent { type: 'GAME_EVENT'; revision: number; event: string; payload: unknown; serverTime: number }
interface Subscription { saveId?: string; accessToken?: string; inviteCode?: string; name?: string; teamId?: number | null; activity?: Activity }

// Uma instância por página: nenhuma inscrição permanece ativa após sair da liga.
@Injectable()
export class SaveRealtimeService {
  readonly status = signal<'disconnected' | 'connecting' | 'connected' | 'reconnecting' | 'error'>('disconnected');
  readonly room = signal<Room | null>(null);
  readonly presence = signal<Presence[]>([]);
  readonly error = signal('');
  readonly selfId = signal<string | null>(null);
  readonly deleted = signal(false);
  private readonly events = new Subject<GameEvent>();
  readonly gameEvents$ = this.events.asObservable();
  private socket: WebSocket | null = null;
  private subscription: Subscription | null = null;
  private retry: ReturnType<typeof setTimeout> | undefined;
  private heartbeat: ReturnType<typeof setInterval> | undefined;
  private presenceTimer: ReturnType<typeof setTimeout> | undefined;
  private attempt = 0;
  private revision = -1;
  private hasSubscribed = false;

  constructor() { inject(DestroyRef).onDestroy(() => { this.disconnect(); this.events.complete(); }); }
  connect(subscription: Subscription): void {
    this.disconnect(); this.subscription = { ...subscription }; this.attempt = 0;
    this.deleted.set(false);
    this.hasSubscribed=false;
    this.error.set(''); this.room.set(null); this.presence.set([]); this.open();
  }
  updatePresence(change: Pick<Subscription, 'name' | 'teamId' | 'activity'>): void {
    if (!this.subscription) return;
    Object.assign(this.subscription, change);
    clearTimeout(this.presenceTimer);
    this.presenceTimer = setTimeout(() => this.send({ type: 'presence', ...this.presenceData() }), 150);
  }
  private presenceData() {
    const { name, teamId, activity } = this.subscription || {};
    return { name, teamId, activity };
  }
  private open(): void {
    if (!this.subscription) return;
    this.status.set(this.attempt ? 'reconnecting' : 'connecting');
    const socket = new WebSocket(`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/saves`);
    this.socket = socket; this.revision = -1;
    socket.onopen = () => {
      if (this.socket !== socket) return;
      this.send({ type: 'subscribe', ...this.subscription, renewActivity: !this.hasSubscribed });
      this.heartbeat = setInterval(() => this.send({ type: 'ping' }), 20000);
    };
    socket.onmessage = event => {
      if (this.socket !== socket) return;
      try {
        const message = JSON.parse(event.data);
        if(message.type==='LEAGUE_DELETED') {
          this.deleted.set(true);this.error.set(message.message);this.room.set(null);this.presence.set([]);
          this.status.set('error');clearInterval(this.heartbeat);socket.close();return;
        }
        if (message.type === 'ERROR') { this.error.set(message.message); this.status.set('error'); return; }
        if (message.type === 'SUBSCRIBED') { this.hasSubscribed=true;this.selfId.set(message.presenceId); return; }
        if (message.type === 'pong') return;
        if (typeof message.revision !== 'number' || message.revision <= this.revision) return;
        this.revision = message.revision;
        if (message.type === 'SNAPSHOT') {
          this.room.set(message.room); this.presence.set(message.presence);
          this.status.set('connected'); this.error.set(''); this.attempt = 0;
        } else if (message.type === 'GAME_EVENT') this.events.next(message);
      } catch { this.error.set('Não foi possível ler a atualização da liga.'); socket.close(); }
    };
    socket.onclose = event => {
      if (this.socket !== socket) return;
      clearInterval(this.heartbeat);
      this.presence.set([]);
      if (event.code === 1008) { this.status.set('error'); return; }
      this.status.set('reconnecting');
      const delay = Math.min(15000, 1000 * 2 ** this.attempt++) + Math.random() * 300;
      this.retry = setTimeout(() => this.open(), delay);
    };
    socket.onerror = () => { if (this.socket === socket) socket.close(); };
  }
  private send(message: unknown): void {
    if (this.socket?.readyState === WebSocket.OPEN) this.socket.send(JSON.stringify(message));
  }
  disconnect(): void {
    clearTimeout(this.retry); clearTimeout(this.presenceTimer); clearInterval(this.heartbeat);
    const socket = this.socket; this.socket = null; this.subscription = null;
    socket?.close(); this.status.set('disconnected');
    this.room.set(null); this.presence.set([]); this.selfId.set(null);
  }
}
