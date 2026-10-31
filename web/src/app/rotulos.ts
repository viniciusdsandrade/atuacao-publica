// Rótulos neutros: a mesma linguagem para todos os políticos.

export const CARGOS: Record<string, string> = {
  presidente: 'Presidente da República',
  governador: 'Governador(a)',
  deputado_federal: 'Deputado(a) federal',
  senador: 'Senador(a)',
  prefeito: 'Prefeito(a)',
  vice_prefeito: 'Vice-prefeito(a)',
  vereador: 'Vereador(a)',
};

export const ESFERAS: Record<string, string> = {
  federal: 'Federal',
  estadual: 'Estadual',
  municipal: 'Municipal',
};

/** Entes municipais catalogados (código IBGE de 7 dígitos para nome do município). */
export const ENTES_MUNICIPAIS: Record<string, string> = {
  '3509502': 'Campinas',
};

export const BLOCOS: Record<string, string> = {
  mandatos: 'Mandatos',
  participacao_votacoes: 'Participação em votações nominais',
  votos: 'Votos',
  despesas: 'Despesas da cota parlamentar',
  proposicoes: 'Proposições de autoria',
  discursos: 'Discursos',
  eventos: 'Eventos com presença registrada',
  orgaos_e_frentes: 'Órgãos, comissões e frentes',
  candidaturas: 'Candidaturas',
  posicionamentos_declarados: 'Posicionamentos declarados',
  agenda_oficial: 'Agenda oficial',
  atos_normativos: 'Atos do Poder Executivo federal no período do mandato',
};

/** Notas fixas de método, iguais para todos os perfis do bloco. */
export const NOTAS: Record<string, string> = {
  participacao_votacoes:
    'Conta votos registrados em votações nominais. Não é presença. A Câmara e o Senado registram ausências de forma diferente.',
  eventos: 'A fonte lista só quem esteve presente. O site não calcula taxa de presença.',
  despesas: 'Valores líquidos informados pela casa legislativa. Compensações negativas entram na soma.',
  atos_normativos: 'A fonte não informa quem assinou cada ato.',
  candidaturas: 'O TSE registra a eleição, não o exercício do cargo.',
  posicionamentos_declarados: 'Registros manuais revisados, com snapshot arquivado da fonte.',
};

export const SEM_REGISTRO = 'Sem registro na fonte.';

export function rotuloCargo(cargo: string): string {
  return CARGOS[cargo] ?? cargo;
}

export function rotuloEsfera(esfera: string): string {
  return ESFERAS[esfera] ?? esfera;
}

export function nomeDoEnte(ente: string | null | undefined): string {
  if (!ente) {
    return '';
  }
  return ENTES_MUNICIPAIS[ente] ?? ente;
}

/** Formata a localização de um político ou mandato: "Campinas (SP)", "SP", etc. */
export function rotuloLocal(
  esfera: string | null | undefined,
  ente: string | null | undefined,
  uf: string | null | undefined,
): string {
  if (esfera === 'municipal') {
    const nome = nomeDoEnte(ente);
    return nome ? `${nome}${uf ? ' (' + uf + ')' : ''}` : (uf ?? '');
  }
  if (esfera === 'estadual') {
    return uf ?? nomeDoEnte(ente);
  }
  if (esfera === 'federal') {
    return uf ?? '';
  }
  return uf ?? nomeDoEnte(ente);
}

export function rotuloBloco(bloco: string): string {
  return BLOCOS[bloco] ?? bloco;
}

const reais = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

export function moeda(valor: number | null | undefined): string {
  return valor === null || valor === undefined ? SEM_REGISTRO : reais.format(valor);
}

/** Data ISO (com ou sem hora) para dd/mm/aaaa, sem converter fuso. */
export function data(valor: unknown): string {
  if (typeof valor !== 'string' || valor.length < 10) {
    return '-';
  }
  const [ano, mes, dia] = valor.substring(0, 10).split('-');
  return `${dia}/${mes}/${ano}`;
}

/** Comparação de busca: sem acento e sem diferença de maiúsculas. */
export function chaveDeBusca(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}
