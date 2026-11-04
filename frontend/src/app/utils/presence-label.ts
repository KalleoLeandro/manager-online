import { Activity } from '../services/save-realtime.service';

export function presenceLabel(activity: Activity): string {
  return ({ SELECTING_TEAM: 'Escolhendo clube', VIEWING_ROSTER: 'Analisando plantel', LOBBY: 'No saguão', MANAGING_TEAM: 'Gerenciando o time', READY: 'Pronto' })[activity];
}
