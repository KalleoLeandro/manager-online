export interface PlayerTechnical { attributes: Record<string,number>; potential: number; sourcedAttributes: string[]; sourceName: string|null; sourceUrl: string|null }
export interface PlayerCondition { fitness: number; effectiveAbility: number; available: boolean; status: 'AVAILABLE'|'TIRED'|'UNFIT'|'INJURED'|'SUSPENDED'; injuryName: string|null; returnRound: number|null; morale: number; sharpness: number; performanceFactor: number }
export const attributeDefinitions = [
  {
    "key": "shot_power",
    "label": "Potência do chute",
    "group": "technical"
  },
  {
    "key": "long_shots",
    "label": "Chutes de longe",
    "group": "technical"
  },
  {
    "key": "dribbling",
    "label": "Drible",
    "group": "technical"
  },
  {
    "key": "technique",
    "label": "Técnica",
    "group": "technical"
  },
  {
    "key": "crossing",
    "label": "Cruzamento",
    "group": "technical"
  },
  {
    "key": "vision",
    "label": "Visão de jogo",
    "group": "technical"
  },
  {
    "key": "finishing",
    "label": "Finalização",
    "group": "technical"
  },
  {
    "key": "short_passing",
    "label": "Passe curto",
    "group": "technical"
  },
  {
    "key": "long_passing",
    "label": "Passe longo",
    "group": "technical"
  },
  {
    "key": "tackling",
    "label": "Desarme",
    "group": "technical"
  },
  {
    "key": "marking",
    "label": "Marcação",
    "group": "technical"
  },
  {
    "key": "heading",
    "label": "Cabeceio",
    "group": "technical"
  },
  {
    "key": "first_touch",
    "label": "Domínio de bola",
    "group": "technical"
  },
  {
    "key": "off_ball",
    "label": "Movimentação",
    "group": "technical"
  },
  {
    "key": "free_kicks",
    "label": "Faltas",
    "group": "set_pieces"
  },
  {
    "key": "corners",
    "label": "Escanteios",
    "group": "set_pieces"
  },
  {
    "key": "penalties",
    "label": "Pênaltis",
    "group": "set_pieces"
  },
  {
    "key": "anticipation",
    "label": "Antecipação",
    "group": "mental"
  },
  {
    "key": "aggression",
    "label": "Agressividade",
    "group": "mental"
  },
  {
    "key": "composure",
    "label": "Compostura",
    "group": "mental"
  },
  {
    "key": "concentration",
    "label": "Concentração",
    "group": "mental"
  },
  {
    "key": "decisions",
    "label": "Decisões",
    "group": "mental"
  },
  {
    "key": "determination",
    "label": "Determinação",
    "group": "mental"
  },
  {
    "key": "teamwork",
    "label": "Trabalho em equipe",
    "group": "mental"
  },
  {
    "key": "leadership",
    "label": "Liderança",
    "group": "mental"
  },
  {
    "key": "consistency",
    "label": "Regularidade",
    "group": "mental"
  },
  {
    "key": "acceleration",
    "label": "Aceleração",
    "group": "physical"
  },
  {
    "key": "pace",
    "label": "Velocidade",
    "group": "physical"
  },
  {
    "key": "work_rate",
    "label": "Intensidade",
    "group": "physical"
  },
  {
    "key": "strength",
    "label": "Força",
    "group": "physical"
  },
  {
    "key": "agility",
    "label": "Agilidade",
    "group": "physical"
  },
  {
    "key": "jumping",
    "label": "Impulsão",
    "group": "physical"
  },
  {
    "key": "stamina",
    "label": "Resistência",
    "group": "physical"
  },
  {
    "key": "injury_resistance",
    "label": "Resistência a lesões",
    "group": "physical"
  },
  {
    "key": "reflexes",
    "label": "Reflexos",
    "group": "goalkeeping"
  },
  {
    "key": "handling",
    "label": "Defesa e encaixe",
    "group": "goalkeeping"
  },
  {
    "key": "gk_positioning",
    "label": "Posicionamento no gol",
    "group": "goalkeeping"
  },
  {
    "key": "one_on_ones",
    "label": "Um contra um",
    "group": "goalkeeping"
  },
  {
    "key": "kicking",
    "label": "Reposição",
    "group": "goalkeeping"
  },
  {
    "key": "diving",
    "label": "Elasticidade",
    "group": "goalkeeping"
  }
] as const;
export function available(player: {fitness?:number;condition?:PlayerCondition}): boolean {return player.condition?.available ?? (player.fitness??100)>=40;}
export function effectiveAbility(player: {ability:number;fitness?:number;condition?:PlayerCondition}): number {return player.condition?.effectiveAbility ?? (available(player)?player.ability:0);}
