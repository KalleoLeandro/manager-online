import type { MatchEvent, MatchTeam } from '../services/matchday.service';

export type MatchSide = 'home' | 'away';
export interface NarrationPart { text: string; side?: MatchSide }
interface Mention { id: number; side: MatchSide }
export interface NarrationRoster { pattern: RegExp | null; names: Map<string, Mention[]> }

/** Match names as literal text, longest first, including substitutes and dismissed players. */
export function narrationRoster(home: MatchTeam, away: MatchTeam): NarrationRoster {
  const names = new Map<string, Mention[]>();
  for (const [side, team] of [['home', home], ['away', away]] as const) {
    for (const player of team.players) {
      if (!player.name?.trim()) continue;
      const mentions = names.get(player.name) || [];
      mentions.push({ id: player.id, side });
      names.set(player.name, mentions);
    }
  }
  const alternatives = [...names.keys()].sort((a, b) => b.length - a.length)
    .map(name => name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  return {
    names,
    pattern: alternatives.length
      ? new RegExp('(^|[^\\p{L}\\p{M}\\p{N}_])(' + alternatives.join('|') + ')(?![\\p{L}\\p{M}\\p{N}_])', 'gu')
      : null,
  };
}

export function narrationParts(event: MatchEvent, roster: NarrationRoster): NarrationPart[] {
  if (!roster.pattern) return [{ text: event.text }];
  const parts: NarrationPart[] = [];
  let cursor = 0;
  roster.pattern.lastIndex = 0;
  for (const match of event.text.matchAll(roster.pattern)) {
    const start = match.index! + match[1].length;
    if (start > cursor) parts.push({ text: event.text.slice(cursor, start) });
    const candidates = roster.names.get(match[2])!;
    const involved = candidates.filter(p => p.id === event.playerId || p.id === event.relatedPlayerId);
    const sides = new Set((involved.length ? involved : candidates).map(p => p.side));
    // An identical name on both clubs cannot be attributed safely without an event player ID.
    const side = sides.size === 1 ? [...sides][0] : undefined;
    parts.push({ text: match[2], side });
    cursor = start + match[2].length;
  }
  if (cursor < event.text.length) parts.push({ text: event.text.slice(cursor) });
  return parts.length ? parts : [{ text: event.text }];
}
