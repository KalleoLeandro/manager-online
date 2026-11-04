export const managementPages = {
  championship:{title:'Campeonato nacional.',description:'Confrontos e calendário de cada temporada da sua liga.'},
  "messages": {
    "title": "Mensagens.",
    "description": "Os avisos do clube e da sua liga, em um só lugar."
  },
  "overview": {
    "title": "Time principal.",
    "description": "Prepare o elenco profissional e sua estratégia para a rodada."
  },
  "roster": {
    "title": "Elenco profissional.",
    "description": "Consulte as fichas e acompanhe a condição dos atletas."
  },
  "tactics": {
    "title": "Escalação e tática.",
    "description": "Escolha os titulares e a formação da equipe."
  },
  "juniors": {
    "title": "Juniores.",
    "description": "A próxima geração do seu clube."
  },
  "training": {
    "title": "Treinamento.",
    "description": "Desenvolvimento técnico e preparação do elenco."
  },
  "transfers": {
    "title": "Mercado da bola.",
    "description": "Acompanhe o mercado e observe possíveis reforços."
  },
  "search": {
    "title": "Buscar jogadores.",
    "description": "Encontre atletas nos elencos desta liga."
  },
  "latest": {
    "title": "Últimas transferências efetivadas.",
    "description": "Negociações concluídas nesta liga."
  },
  "staff": {
    "title": "Staff.",
    "description": "A equipe de apoio ao treinador."
  },
  "details": {
    "title": "Detalhes do clube.",
    "description": "A identidade e a situação do seu clube nesta liga."
  },
  "titles": {
    "title": "Títulos.",
    "description": "As conquistas do clube."
  },
  "stadium": {
    "title": "Estádio.",
    "description": "A casa do seu clube."
  },
  "facilities": {
    "title": "Instalações do clube.",
    "description": "A estrutura de apoio ao futebol."
  },
  "career": {
    "title": "Carreira do treinador.",
    "description": "Seu trabalho à frente do clube nesta liga."
  },
  "options": {
    "title": "Opções.",
    "description": "Preferências de apresentação e acesso às suas ligas."
  },
  "clubs": {
    "title": "Outros times da liga.",
    "description": "Consulte clubes e elencos adversários."
  },
  "finance": {
    "title": "Finanças do clube.",
    "description": "Acompanhe os recursos disponíveis nesta liga."
  }
} as const;
export type ManagementPage = keyof typeof managementPages;
export interface ManagementMenu { id: ManagementPage; icon: string; name: string; pages: ManagementPage[]; links: {id:ManagementPage;name:string}[] }
export const managementNavigation: ManagementMenu[] = [
  {
    "id": "messages",
    "icon": "✉",
    "name": "Mensagens",
    "links": [],
    "pages": [
      "messages"
    ]
  },
  {
    "id": "overview",
    "icon": "♟",
    "name": "Time",
    "links": [
      {
        "id": "overview",
        "name": "Time principal"
      },
      {
        "id": "juniors",
        "name": "Juniores"
      },
      {
        "id": "training",
        "name": "Treinamento"
      }
    ],
    "pages": [
      "overview",
      "overview",
      "juniors",
      "training",
      "roster",
      "tactics"
    ]
  },
  {
    "id": "transfers",
    "icon": "⇄",
    "name": "Transferências",
    "links": [
      {
        "id": "transfers",
        "name": "Mercado da bola"
      },
      {
        "id": "search",
        "name": "Buscar jogadores"
      },
      {
        "id": "latest",
        "name": "Últimas transferências efetivadas"
      }
    ],
    "pages": [
      "transfers",
      "transfers",
      "search",
      "latest"
    ]
  },
  {
    "id": "details",
    "icon": "⚑",
    "name": "Clube",
    "links": [
      {
        "id": "staff",
        "name": "Staff"
      },
      {
        "id": "details",
        "name": "Detalhes do clube"
      },
      {
        "id": "titles",
        "name": "Títulos"
      }
    ],
    "pages": [
      "details",
        "championship",
      "staff",
      "details",
      "titles",
      "clubs",
      "finance"
    ]
  },
  {
    "id": "stadium",
    "icon": "▤",
    "name": "Infraestrutura",
    "links": [
      {
        "id": "stadium",
        "name": "Estádio"
      },
      {
        "id": "facilities",
        "name": "Instalações do clube"
      }
    ],
    "pages": [
      "stadium",
      "stadium",
      "facilities"
    ]
  },
  {
    "id": "career",
    "icon": "☆",
    "name": "Carreira",
    "links": [],
    "pages": [
      "career"
    ]
  },
  {
    "id": "options",
    "icon": "⚙",
    "name": "Opções",
    "links": [],
    "pages": [
      "options"
    ]
  }
];
