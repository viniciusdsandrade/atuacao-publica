// Rótulos neutros: a mesma linguagem para todos os políticos.

export const CARGOS: Record<string, string> = {
  presidente: 'Presidente da República',
  governador: 'Governador(a)',
  deputado_federal: 'Deputado(a) federal',
  senador: 'Senador(a)',
  deputado_estadual: 'Deputado(a) estadual',
  deputado_distrital: 'Deputado(a) distrital',
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
  despesas_executivo: 'Despesas pagas pela administração direta do município no período do mandato',
};

/** Notas fixas de método, iguais para todos os perfis do bloco. */
export const NOTAS: Record<string, string> = {
  participacao_votacoes:
    'Conta votos registrados em votações nominais. Não é presença. A Câmara e o Senado registram ausências de forma diferente.',
  eventos: 'A fonte lista só quem esteve presente. O site não calcula taxa de presença.',
  despesas: 'Valores líquidos informados pela casa legislativa. Compensações negativas entram na soma.',
  atos_normativos: 'A fonte não informa quem assinou cada ato.',
  despesas_executivo:
    'Valor pago pelo órgão da prefeitura, somado por mês pelo Tribunal de Contas do Estado. Autarquias, fundações e a Câmara ficam fora. A fonte não informa quem autorizou cada pagamento.',
  candidaturas: 'O TSE registra a eleição, não o exercício do cargo.',
  agenda_oficial:
    'Compromissos como publicados na página de cada dia da agenda oficial. Dia sem página publicada fica sem registro. Participantes como na fonte.',
  posicionamentos_declarados: 'Registros manuais revisados, com snapshot arquivado da fonte.',
};

/** Rótulos curtos dos blocos para navegação e contagens. O título completo continua em BLOCOS. */
export const BLOCOS_CURTOS: Record<string, string> = {
  mandatos: 'Mandatos',
  participacao_votacoes: 'Participação em votações',
  votos: 'Votos',
  despesas: 'Despesas da cota',
  proposicoes: 'Proposições',
  discursos: 'Discursos',
  eventos: 'Eventos',
  orgaos_e_frentes: 'Órgãos e frentes',
  candidaturas: 'Candidaturas',
  posicionamentos_declarados: 'Posicionamentos declarados',
  agenda_oficial: 'Agenda oficial',
  atos_normativos: 'Atos normativos',
  despesas_executivo: 'Despesas do Executivo',
};

export const SEM_REGISTRO = 'Sem registro na fonte.';

export type Situacao = 'exercicio' | 'posse' | 'fora';

export const SITUACOES: Record<Situacao, string> = {
  exercicio: 'Em exercício',
  posse: 'Aguardando posse',
  fora: 'Fora de exercício',
};

/** Em exercício, aguardando posse ou fora de exercício (ADR-0006). */
export function situacaoDe(p: { em_exercicio: boolean; situacao: string | null }): Situacao {
  if (p.em_exercicio) {
    return 'exercicio';
  }
  return p.situacao?.includes('posse prevista') ? 'posse' : 'fora';
}

const PARTICULAS = new Set(['de', 'da', 'do', 'das', 'dos', 'e']);

/** Iniciais do primeiro e do último nome, sem partículas: "Luiz Philippe de Orleans e Bragança" vira "LB". */
export function iniciais(nome: string): string {
  const partes = nome
    .trim()
    .split(/\s+/)
    .filter((parte) => parte && !PARTICULAS.has(parte.toLowerCase()));
  if (partes.length === 0) {
    return '?';
  }
  const ultima = partes.length > 1 ? partes[partes.length - 1] : '';
  return (partes[0].charAt(0) + ultima.charAt(0)).toLocaleUpperCase('pt-BR');
}

const inteiros = new Intl.NumberFormat('pt-BR');

/** Inteiro com separador de milhar: 1327 vira "1.327". */
export function inteiro(valor: number): string {
  return inteiros.format(valor);
}

/** O título do bloco muda com a esfera: a fonte dos atos do município é a câmara, não o Senado. */
export function tituloDoBloco(bloco: string, esfera?: string | null): string {
  if (bloco === 'atos_normativos' && esfera === 'municipal') {
    return 'Atos normativos do município no período do mandato';
  }
  if (bloco === 'atos_normativos' && esfera === 'estadual') {
    return 'Atos normativos do Estado no período do mandato';
  }
  if (bloco === 'despesas_executivo' && esfera === 'federal') {
    return 'Cartão de pagamento e viagens da Presidência da República no período do mandato';
  }
  if (bloco === 'despesas_executivo' && esfera === 'estadual') {
    return 'Despesas do Poder Executivo estadual no período do mandato';
  }
  return rotuloBloco(bloco);
}

/** Nota de método do bloco, igual para todos os perfis da mesma esfera. */
export function notaDoBloco(bloco: string, esfera?: string | null): string | null {
  if (bloco === 'atos_normativos' && esfera === 'municipal') {
    return 'Leis, leis complementares e decretos registrados pela câmara municipal no SAPL. A fonte não informa quem assinou cada ato.';
  }
  if (bloco === 'atos_normativos' && esfera === 'estadual') {
    return 'Decretos e leis do Poder Executivo estadual registrados no acervo oficial de legislação do Estado de São Paulo. A fonte não informa quem assinou cada ato.';
  }
  if (bloco === 'despesas_executivo' && esfera === 'federal') {
    return 'Cartão de pagamento do Governo Federal e viagens realizadas do órgão Presidência da República, somados por mês a partir dos arquivos do Portal da Transparência. Transações sob sigilo entram pelo valor, sem detalhe. A fonte não informa quem autorizou cada gasto.';
  }
  if (bloco === 'despesas_executivo' && esfera === 'estadual') {
    return 'Despesas orçamentárias do Gabinete do Governador e secretarias da administração direta, somadas por mês a partir do Portal da Transparência do Estado de São Paulo. A fonte não informa quem autorizou cada gasto.';
  }
  return NOTAS[bloco] ?? null;
}

export function rotuloCurtoBloco(bloco: string, esfera?: string | null): string {
  if (bloco === 'despesas_executivo') {
    return esfera === 'federal' ? 'Cartão e viagens' : (esfera === 'estadual' ? 'Despesas do governo estadual' : 'Despesas da prefeitura');
  }
  return BLOCOS_CURTOS[bloco] ?? bloco;
}

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
