// Rótulos neutros: a mesma linguagem para todos os políticos.

export const CARGOS: Record<string, string> = {
  presidente: 'Presidente da República',
  vice_presidente: 'Vice-presidente da República',
  governador: 'Governador(a)',
  vice_governador: 'Vice-governador(a)',
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

/** Nome de cada unidade da federação, pela sigla. */
export const NOMES_DAS_UFS: Record<string, string> = {
  AC: 'Acre',
  AL: 'Alagoas',
  AM: 'Amazonas',
  AP: 'Amapá',
  BA: 'Bahia',
  CE: 'Ceará',
  DF: 'Distrito Federal',
  ES: 'Espírito Santo',
  GO: 'Goiás',
  MA: 'Maranhão',
  MG: 'Minas Gerais',
  MS: 'Mato Grosso do Sul',
  MT: 'Mato Grosso',
  PA: 'Pará',
  PB: 'Paraíba',
  PE: 'Pernambuco',
  PI: 'Piauí',
  PR: 'Paraná',
  RJ: 'Rio de Janeiro',
  RN: 'Rio Grande do Norte',
  RO: 'Rondônia',
  RR: 'Roraima',
  RS: 'Rio Grande do Sul',
  SC: 'Santa Catarina',
  SE: 'Sergipe',
  SP: 'São Paulo',
  TO: 'Tocantins',
};

/** Entes municipais catalogados (código IBGE de 7 dígitos para nome do município). */
export const ENTES_MUNICIPAIS: Record<string, string> = {
  '3509502': 'Campinas',
  '3510500': 'Caraguatatuba',
  '3536505': 'Paulínia',
  '3550308': 'São Paulo',
};

/** Município do piloto (ADR-0007): o único com atos normativos pelo SAPL. */
const ENTE_DO_PILOTO = '3509502';
/** Caraguatatuba (ADR-0057): a câmara não mantém a situação de tramitação. */
const ENTE_DE_CARAGUATATUBA = '3510500';
/** Paulínia (ADR-0066): o sistema legislativo da câmara não permite coleta automática. */
const ENTE_DE_PAULINIA = '3536505';
/** O que a Câmara de Paulínia publica só no sistema legislativo, que o projeto não coleta. */
const BLOCOS_NAO_COLETADOS_EM_PAULINIA = new Set(['proposicoes', 'votos', 'participacao_votacoes', 'eventos']);

export const TIPOS_ORGAO: Record<string, string> = {
  comissao: 'Comissão',
  frente: 'Frente parlamentar',
  orgao: 'Órgão / Conselho',
};

export function rotuloTipoOrgao(tipo: string | null | undefined): string {
  if (!tipo) {
    return SEM_REGISTRO;
  }
  return TIPOS_ORGAO[tipo] ?? tipo;
}

const REGEX_LIDERANCA =
  /^(presidente|presidenta|1[ºª]?\s*vice|2[ºª]?\s*vice|3[ºª]?\s*vice|vice-presid|coordenador|relator|ouvidor|procurador|corregedor|secret[aá]ri)/i;

/** Identifica papéis de liderança em comissões, frentes e colegiados (Presidente, Vice, Relator, Coordenador, etc.). */
export function ePapelLideranca(papel: string | null | undefined): boolean {
  if (!papel) {
    return false;
  }
  return REGEX_LIDERANCA.test(papel.trim());
}

export const BLOCOS: Record<string, string> = {
  mandatos: 'Mandatos',
  participacao_votacoes: 'Participação em votações nominais',
  votos: 'Votos',
  despesas: 'Despesas da cota parlamentar',
  proposicoes: 'Proposições de autoria',
  emendas: 'Emendas individuais ao Orçamento da União',
  discursos: 'Discursos',
  eventos: 'Eventos e presença',
  orgaos_e_frentes: 'Órgãos, comissões e frentes',
  candidaturas: 'Candidaturas',
  financiamento_campanha: 'Financiamento de campanha',
  posicionamentos_declarados: 'Posicionamentos declarados',
  agenda_oficial: 'Agenda oficial',
  atos_normativos: 'Atos do Poder Executivo federal no período do mandato',
  despesas_executivo: 'Despesas pagas pela administração direta do município no período do mandato',
};

/** Presença em evento como a fonte registra. */
export const PRESENCAS: Record<string, string> = {
  registrada: 'Presente',
  ausencia_justificada: 'Ausência justificada',
  falta: 'Falta',
};

/** Notas fixas de método, iguais para todos os perfis do bloco. */
export const NOTAS: Record<string, string> = {
  participacao_votacoes:
    'Conta votos registrados em votações nominais. Não é presença. A Câmara e o Senado registram ausências de forma diferente.',
  eventos:
    'Na Câmara dos Deputados, no piloto municipal e nas assembleias em SAPL (ALPB, ALEAM, ALERO, ALEPI e ALEAC), a fonte lista só quem esteve presente. Na ALESC, lista também a ausência justificada em cada sessão ordinária; na ALBA e na Câmara Municipal de Caraguatatuba, a falta justificada e a falta sem justificativa em cada sessão plenária. O site não calcula taxa de presença.',
  despesas: 'Valores líquidos informados pela casa legislativa. Compensações negativas entram na soma.',
  atos_normativos: 'A fonte não informa quem assinou cada ato.',
  despesas_executivo:
    'Valor pago pelo órgão da prefeitura, somado por mês pelo Tribunal de Contas do Estado. Autarquias, fundações e a Câmara ficam fora. A fonte não informa quem autorizou cada pagamento.',
  candidaturas:
    'O TSE registra a eleição, não o exercício do cargo. Redes e sites: endereços declarados pelo próprio candidato no registro da candidatura. O site não confere o conteúdo dessas páginas.',
  financiamento_campanha:
    'Receitas e despesas contratadas declaradas na prestação de contas ao TSE, somadas por categoria. Fonte e origem são dois recortes do mesmo total. Doadores e fornecedores pessoa física entram só nas somas, sem nome.',
  emendas:
    'Emendas individuais do parlamentar, por ano do orçamento, com os valores como o Portal da Transparência publica: empenhado, liquidado, pago e restos a pagar. Emendas de bancada, de comissão e de relator não têm autor individual na fonte e ficam fora.',
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
  emendas: 'Emendas',
  discursos: 'Discursos',
  eventos: 'Eventos',
  orgaos_e_frentes: 'Órgãos e frentes',
  candidaturas: 'Candidaturas',
  financiamento_campanha: 'Financiamento',
  posicionamentos_declarados: 'Posicionamentos declarados',
  agenda_oficial: 'Agenda oficial',
  atos_normativos: 'Atos normativos',
  despesas_executivo: 'Despesas do Executivo',
};

export const SEM_REGISTRO = 'Sem registro na fonte.';
/** O dado existe numa fonte oficial que o projeto não coleta. Não é ausência na fonte. */
export const NAO_COLETADO = 'Não coletado pelo site.';

/** O bloco é do vereador de Paulínia e o dado fica no sistema legislativo da câmara (ADR-0066). */
function naoColetadoEmPaulinia(bloco: string, esfera?: string | null, ente?: string | null, cargo?: string | null): boolean {
  return esfera === 'municipal' && ente === ENTE_DE_PAULINIA && cargo === 'vereador' && BLOCOS_NAO_COLETADOS_EM_PAULINIA.has(bloco);
}

/** Texto do bloco sem registro: "não coletado" quando o dado existe numa fonte que o projeto não coleta. */
export function textoDoBlocoVazio(bloco: string, esfera?: string | null, ente?: string | null, cargo?: string | null): string {
  return naoColetadoEmPaulinia(bloco, esfera, ente, cargo) ? NAO_COLETADO : SEM_REGISTRO;
}

export type Situacao = 'exercicio' | 'posse' | 'fora';

export const SITUACOES: Record<Situacao, string> = {
  exercicio: 'Em exercício',
  posse: 'Aguardando posse',
  fora: 'Fora de exercício',
};

/**
 * Casa legislativa do cargo, ou null quando o cargo não é de parlamentar.
 * O lado a lado só põe lado a lado dois parlamentares da mesma casa (ADR-0036).
 */
export function casaLegislativa(cargo: string, uf: string | null | undefined, ente: string | null | undefined): string | null {
  switch (cargo) {
    case 'deputado_federal':
      return 'camara-dos-deputados';
    case 'senador':
      return 'senado';
    case 'deputado_estadual':
    case 'deputado_distrital':
      return uf ? `assembleia-${uf}` : null;
    case 'vereador':
      return ente ? `camara-municipal-${ente}` : null;
    default:
      return null;
  }
}

/** Primeiro e último ano de uma lista: "2022 a 2026", ou o ano sozinho. Vazio quando não há ano. */
export function periodoDosAnos(anos: number[]): string {
  if (anos.length === 0) {
    return '';
  }
  const de = Math.min(...anos);
  const ate = Math.max(...anos);
  return de === ate ? String(de) : `${de} a ${ate}`;
}

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
  if (bloco === 'despesas' && esfera === 'municipal') {
    return 'Despesas informadas pela câmara municipal';
  }
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

/** Nota de método do bloco, igual para todos os perfis da mesma esfera e, na municipal, do mesmo município. */
export function notaDoBloco(bloco: string, esfera?: string | null, ente?: string | null, cargo?: string | null): string | null {
  if (naoColetadoEmPaulinia(bloco, esfera, ente, cargo)) {
    return 'A Câmara Municipal de Paulínia publica proposituras, votações e lista de frequência no sistema legislativo dela. O sistema não permite coleta automática, e o site não o coleta. O bloco vazio não quer dizer que o vereador não tenha registro.';
  }
  if (bloco === 'despesas' && esfera === 'municipal' && ente === ENTE_DE_PAULINIA) {
    return 'Em 2026-11-15, a Câmara Municipal de Paulínia informava, na página "Verba Indenizatória" do portal, que não há cota parlamentar nem verba indenizatória (Lei Orgânica do Município, art. 94, § 4º). O projeto não tem fonte validada para outros gastos por vereador, como diárias.';
  }
  if (bloco === 'atos_normativos' && esfera === 'municipal') {
    if (ente && ente !== ENTE_DO_PILOTO) {
      return 'O projeto ainda não tem fonte validada para os atos normativos deste município.';
    }
    return 'Leis, leis complementares e decretos registrados pela câmara municipal no SAPL. A fonte não informa quem assinou cada ato.';
  }
  if (bloco === 'despesas' && esfera === 'municipal') {
    return 'Só as despesas que a câmara publica por vereador. No piloto municipal: combustível, material de escritório, hospedagem e passagens. Na Câmara Municipal de Caraguatatuba: diárias, pela data de concessão.';
  }
  if (bloco === 'proposicoes' && ente === ENTE_DE_CARAGUATATUBA) {
    return 'A Câmara Municipal de Caraguatatuba não mantém a situação de tramitação no sistema legislativo. Por isso o site não a publica.';
  }
  if (esfera === 'estadual' && (bloco === 'atos_normativos' || bloco === 'despesas_executivo' || bloco === 'agenda_oficial')) {
    return notaDoGovernoEstadual(bloco, ente);
  }
  if (bloco === 'despesas_executivo' && esfera === 'federal') {
    return 'Cartão de pagamento do Governo Federal e viagens realizadas do órgão Presidência da República, somados por mês a partir dos arquivos do Portal da Transparência. Transações sob sigilo entram pelo valor, sem detalhe. A fonte não informa quem autorizou cada gasto.';
  }
  return NOTAS[bloco] ?? null;
}

/** Fontes do governo estadual, por UF (ADR-0014 e ADR-0075). Sem fonte validada, o bloco vazio diz isso. */
const NOTAS_DO_GOVERNO_ESTADUAL: Record<string, Record<string, string>> = {
  SP: {
    atos_normativos:
      'Decretos e leis do Poder Executivo estadual registrados no acervo oficial de legislação do Estado de São Paulo. A fonte não informa quem assinou cada ato.',
    despesas_executivo:
      'Despesas orçamentárias do Gabinete do Governador e secretarias da administração direta, somadas por mês a partir do Portal da Transparência do Estado de São Paulo. A fonte não informa quem autorizou cada gasto.',
  },
  MG: {
    atos_normativos:
      'Decretos numerados do Poder Executivo de Minas Gerais, como registrados nos dados abertos da Assembleia Legislativa. Decretos sem número ficam fora. A fonte não informa quem assinou cada ato.',
    agenda_oficial:
      'Compromissos como publicados na agenda do Governador pela Agência Minas, com a hora publicada. Dia marcado "Sem agenda oficial" fica sem registro.',
  },
};

function notaDoGovernoEstadual(bloco: string, uf: string | null | undefined): string | null {
  const nota = uf ? NOTAS_DO_GOVERNO_ESTADUAL[uf]?.[bloco] : undefined;
  if (nota) {
    return nota;
  }
  if (bloco === 'agenda_oficial') {
    return uf && NOTAS_DO_GOVERNO_ESTADUAL[uf] ? NOTAS[bloco] ?? null : 'O projeto ainda não tem fonte validada para a agenda do governo deste estado.';
  }
  return bloco === 'atos_normativos'
    ? 'O projeto ainda não tem fonte validada para os atos normativos do governo deste estado.'
    : 'O projeto ainda não tem fonte validada para as despesas do governo deste estado.';
}

export function rotuloCurtoBloco(bloco: string, esfera?: string | null): string {
  if (bloco === 'despesas' && esfera === 'municipal') {
    return 'Despesas';
  }
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

/**
 * Valor em reais que não cabe no cartão de total com a letra padrão: de R$ 100 milhões para cima.
 * O cartão usa uma letra menor, e o número não quebra no meio.
 */
export function valorLongo(valor: number | null | undefined): boolean {
  return valor !== null && valor !== undefined && Math.abs(valor) >= 100_000_000;
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

/** Pedaço de um texto, marcado ou não como trecho que casou com a busca. */
export interface Trecho {
  t: string;
  m: boolean;
}

/**
 * Parte o texto em trechos e marca onde aparece cada termo da busca.
 * A comparação ignora acento e caixa, como `chaveDeBusca`. O texto mostrado é o original:
 * juntar os trechos devolve o texto sem nenhuma alteração.
 */
export function realcar(texto: string, termos: string[]): Trecho[] {
  if (!texto || termos.length === 0) {
    return [{ t: texto, m: false }];
  }
  // Chave de comparação, com a posição de cada caractere dela no texto original.
  let chave = '';
  const origem: number[] = [];
  for (let i = 0; i < texto.length; i++) {
    const base = texto[i].normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
    for (let k = 0; k < base.length; k++) {
      chave += base[k];
      origem.push(i);
    }
  }
  const marcado = new Array<boolean>(texto.length).fill(false);
  for (const termo of termos) {
    if (!termo) {
      continue;
    }
    for (let de = chave.indexOf(termo); de !== -1; de = chave.indexOf(termo, de + termo.length)) {
      const ate = de + termo.length;
      // Vai até o próximo caractere da chave: o acento solto depois da última letra entra junto.
      const fim = ate < origem.length ? origem[ate] : texto.length;
      for (let i = origem[de]; i < fim; i++) {
        marcado[i] = true;
      }
    }
  }
  const trechos: Trecho[] = [];
  let inicio = 0;
  for (let i = 1; i <= texto.length; i++) {
    if (i === texto.length || marcado[i] !== marcado[inicio]) {
      trechos.push({ t: texto.slice(inicio, i), m: marcado[inicio] });
      inicio = i;
    }
  }
  return trechos;
}

/** Nomes oficiais por extenso dos partidos do TSE (ativos e históricos), indexados pela sigla sem acento nem pontuação. */
export const NOMES_DOS_PARTIDOS: Record<string, string> = {
  AGIR: 'Agir',
  AVANTE: 'Avante',
  CIDAD: 'Cidadania',
  CIDADANIA: 'Cidadania',
  DC: 'Democracia Cristã',
  DEM: 'Democratas',
  DEMOCRATA: 'Democrata',
  MDB: 'Movimento Democrático Brasileiro',
  MISSAO: 'Partido Missão',
  MOBILIZA: 'Mobilização Nacional',
  NOVO: 'Partido Novo',
  PATRIOTA: 'Patriota',
  PCB: 'Partido Comunista Brasileiro',
  PCDOB: 'Partido Comunista do Brasil',
  PCO: 'Partido da Causa Operária',
  PDT: 'Partido Democrático Trabalhista',
  PHS: 'Partido Humanista da Solidariedade',
  PL: 'Partido Liberal',
  PMB: 'Partido da Mulher Brasileira',
  PMN: 'Partido da Mobilização Nacional',
  PODE: 'Podemos',
  PODEMOS: 'Podemos',
  PP: 'Progressistas',
  PROGRESSISTAS: 'Progressistas',
  PPL: 'Partido Pátria Livre',
  PPS: 'Partido Popular Socialista',
  PR: 'Partido da República',
  PRB: 'Partido Republicano Brasileiro',
  PRD: 'Partido Renovação Democrática',
  PROS: 'Partido Republicano da Ordem Social',
  PRP: 'Partido Republicano Progressista',
  PRTB: 'Partido Renovador Trabalhista Brasileiro',
  PSB: 'Partido Socialista Brasileiro',
  PSC: 'Partido Social Cristão',
  PSD: 'Partido Social Democrático',
  PSDB: 'Partido da Social Democracia Brasileira',
  PSL: 'Partido Social Liberal',
  PSOL: 'Partido Socialismo e Liberdade',
  PSTU: 'Partido Socialista dos Trabalhadores Unificado',
  PT: 'Partido dos Trabalhadores',
  PTB: 'Partido Trabalhista Brasileiro',
  PTC: 'Partido Trabalhista Cristão',
  PV: 'Partido Verde',
  REDE: 'Rede Sustentabilidade',
  REP: 'Republicanos',
  REPUB: 'Republicanos',
  REPUBLICANOS: 'Republicanos',
  SD: 'Solidariedade',
  SDD: 'Solidariedade',
  SOLIDARIEDADE: 'Solidariedade',
  UB: 'União Brasil',
  UNIAO: 'União Brasil',
  UNIAOBRASIL: 'União Brasil',
  UP: 'Unidade Popular',
};

function chaveDeSigla(valor: string): string {
  return chaveDeBusca(valor).replace(/[^a-z0-9]/g, '').toUpperCase();
}

/** Nome completo do partido pela sigla do TSE, ou nulo quando não há tradução cadastrada. */
export function nomeDoPartido(sigla: string | null | undefined): string | null {
  if (!sigla) {
    return null;
  }
  const limpo = sigla.trim();
  if (!limpo || chaveDeBusca(limpo) === 'sem partido') {
    return null;
  }
  const chave = chaveDeSigla(limpo);
  return NOMES_DOS_PARTIDOS[chave] ?? null;
}

/** Sigla com nome completo ao lado quando o nome traz informação além da própria sigla. */
export function partidoComNome(sigla: string | null | undefined): string | null {
  if (!sigla) {
    return null;
  }
  const nome = nomeDoPartido(sigla);
  if (!nome || chaveDeBusca(nome) === chaveDeBusca(sigla)) {
    return sigla;
  }
  return `${sigla} · ${nome}`;
}

/** Rótulo para leitor de tela em cartões compactos onde a pílula mostra só a sigla. */
export function rotuloAcessivelPartido(sigla: string | null | undefined): string | null {
  if (!sigla) {
    return null;
  }
  const nome = nomeDoPartido(sigla);
  if (!nome || chaveDeBusca(nome) === chaveDeBusca(sigla)) {
    return null;
  }
  return `${sigla}, ${nome}`;
}

/** Siglas de tipos de proposição usadas pela Câmara, Senado e sistemas SAPL. */
export const TIPOS_PROPOSICAO: Record<string, string> = {
  CON: 'Consulta',
  CVO: 'Complementação de Voto',
  DEN: 'Denúncia',
  DOC: 'Indicação de Liderança',
  DTQ: 'Destaque',
  EMA: 'Emenda Aglutinativa',
  EMC: 'Emenda na Comissão',
  'EMC-A': 'Emenda Adotada pela Comissão',
  EMD: 'Emenda',
  EMEND: 'Emenda',
  EMIMP: 'Emenda Impositiva',
  EMP: 'Emenda de Plenário',
  EMR: 'Emenda de Relator',
  EMS: 'Emenda ou Substitutivo do Senado',
  ESB: 'Emenda ao Substitutivo',
  INC: 'Indicação',
  IND: 'Indicação',
  INS: 'Indicação',
  MOC: 'Moção',
  MP: 'Medida Provisória',
  MPV: 'Medida Provisória',
  MSC: 'Mensagem de Acordo Internacional',
  OF: 'Ofício',
  OFI: 'Ofício',
  PAR: 'Parecer',
  PDL: 'Projeto de Decreto Legislativo',
  PDS: 'Projeto de Decreto Legislativo',
  PEC: 'Proposta de Emenda à Constituição',
  PELOM: 'Proposta de Emenda à Lei Orgânica',
  PEP: 'Parecer às Emendas de Plenário',
  PFC: 'Proposta de Fiscalização e Controle',
  PFS: 'Proposta de Fiscalização e Controle',
  PL: 'Projeto de Lei',
  PLC: 'Projeto de Lei Complementar',
  PLG: 'Projeto de Lei do Executivo',
  PLN: 'Projeto de Lei do Congresso Nacional',
  PLO: 'Projeto de Lei Ordinária',
  PLP: 'Projeto de Lei Complementar',
  PLS: 'Projeto de Lei do Senado',
  PLV: 'Projeto de Lei de Conversão',
  PPP: 'Parecer Proferido em Plenário',
  PPREF: 'Protocolado',
  PR: 'Projeto de Resolução',
  PRC: 'Projeto de Resolução',
  PRE: 'Projeto de Resolução',
  PRL: 'Parecer do Relator',
  PRLE: 'Parecer Preliminar às Emendas de Plenário',
  PRLP: 'Parecer Preliminar de Plenário',
  PRN: 'Projeto de Resolução do Congresso Nacional',
  PROC: 'Processo Interno',
  PRS: 'Projeto de Resolução',
  'R.C': 'Recurso no Congresso Nacional',
  'R.S': 'Recurso no Senado',
  RCP: 'Requerimento de Instituição de CPI',
  RDF: 'Redação Final',
  REC: 'Recurso',
  REL: 'Relatório',
  REM: 'Reclamação',
  REP: 'Representação',
  REQ: 'Requerimento',
  RIC: 'Requerimento de Informação',
  RPD: 'Requerimento de Apreciação Imediata',
  RPDR: 'Requerimento de Alteração da Ordem dos Trabalhos',
  RQN: 'Requerimento do Congresso Nacional',
  RQS: 'Requerimento do Senado',
  SBE: 'Subemenda',
  SBR: 'Subemenda de Relator',
  SBT: 'Substitutivo',
  SIT: 'Solicitação de Informação ao TCU',
  SLD: 'Sugestão de Emenda à LDO',
  SOR: 'Sugestão de Emenda ao Orçamento',
  SPP: 'Sugestão de Emenda ao PPA',
  SUG: 'Sugestão',
  TVR: 'Outorga de Rádio ou TV',
  VET: 'Veto',
  VETO: 'Veto',
  VTS: 'Voto em Separado',
};

const PROPOSICOES_POR_EXTENSO = new Map<string, string>(
  [
    ...Object.values(TIPOS_PROPOSICAO),
    'Projeto de Indicação',
    'Requerimento de Comissão',
    'Requerimento Numerado',
  ].map((nome) => [chaveDeBusca(nome), nome]),
);

/** Traduz siglas conhecidas de tipo de proposição em "SIGLA · Nome", evitando duplicar termos já por extenso. */
export function rotuloTipoProposicao(tipo: string): string {
  const limpo = tipo.trim();
  const chave = limpo.toUpperCase();
  const nome = TIPOS_PROPOSICAO[chave];
  if (!nome) {
    return PROPOSICOES_POR_EXTENSO.get(chaveDeBusca(limpo)) ?? limpo;
  }
  if (chaveDeBusca(nome) === chaveDeBusca(limpo)) {
    return nome;
  }
  return `${limpo} · ${nome}`;
}

/** Âncora correspondente em docs/FONTES.md (#/fontes#<id>) para cada identificador de coleta em Estado. */
export const ANCORAS_FONTES: Record<string, string> = {
  camara: 'doc-camara-api-camara-dos-deputados-api-v2',
  senado: 'doc-senado-api-senado-federal-dados-abertos',
  tse: 'doc-tse-dados-abertos-tse-dados-abertos',
  executivo: 'doc-planalto-legislacao-legislacao-no-planalto',
  sapl: 'doc-campinas-camara-sapl-sapl-da-camara-municipal',
  tcesp: 'doc-tce-sp-transparencia-api-e-tce-sp-conjunto-dados-tribunal-de-contas-do-estado-de-sao-paulo',
  cgu: 'doc-cgu-portal-api-e-cgu-portal-arquivos-portal-da-transparencia',
  agenda: 'doc-planalto-agenda-agenda-do-presidente-da-republica',
  contrib: 'doc-fontes-t1-genericas',
  alesp: 'doc-assembleia-legislativa-do-estado-de-sao-paulo-alesp',
  executivosp: 'doc-governo-do-estado-de-sao-paulo-executivo-estadual',
  executivomg: 'doc-governo-do-estado-de-minas-gerais-executivo-estadual',
  almg: 'doc-assembleia-legislativa-do-estado-de-minas-gerais-almg',
  alep: 'doc-assembleia-legislativa-do-estado-do-parana-alep',
  alba: 'doc-fontes-da-bahia-alba-governo-do-estado',
  alerj: 'doc-fontes-do-rio-de-janeiro-alerj-governo-do-estado',
  alrs: 'doc-fontes-do-rio-grande-do-sul-alrs-governo-do-estado',
  alece: 'doc-fontes-do-ceara-alece-governo-do-estado',
  alesc: 'doc-fontes-de-santa-catarina-alesc-governo-do-estado',
  alego: 'doc-fontes-de-goias-alego-governo-do-estado',
  ales: 'doc-fontes-do-espirito-santo-ales-governo-do-estado',
  alepa: 'doc-fontes-do-para-alepa-governo-do-estado',
  alepe: 'doc-fontes-de-pernambuco-alepe-governo-do-estado',
  cldf: 'doc-fontes-do-distrito-federal-cldf-governo-do-distrito-federal',
  almt: 'doc-fontes-de-mato-grosso-almt-governo-do-estado',
  alems: 'doc-fontes-de-mato-grosso-do-sul-alems-governo-do-estado',
  alpb: 'doc-fontes-da-paraiba-alpb-governo-do-estado',
  aleam: 'doc-fontes-do-amazonas-aleam-governo-do-estado',
  aleal: 'doc-fontes-de-alagoas-aleal-governo-do-estado',
  alero: 'doc-fontes-de-rondonia-alero-governo-do-estado',
  alepi: 'doc-fontes-do-piaui-alepi-governo-do-estado',
  alrn: 'doc-fontes-do-rio-grande-do-norte-alrn-governo-do-estado',
  aleto: 'doc-fontes-do-tocantins-aleto-governo-do-estado',
  alese: 'doc-fontes-de-sergipe-alese-governo-do-estado',
  alema: 'doc-fontes-do-maranhao-alema-governo-do-estado',
  aleac: 'doc-fontes-do-acre-aleac-governo-do-estado',
  alerr: 'doc-fontes-de-roraima-alerr-governo-do-estado',
  alap: 'doc-fontes-do-amapa-alap-governo-do-estado',
  cmsp: 'doc-segundo-municipio-piloto-sao-paulo-cmsp-prefeitura',
  caraguatatuba: 'doc-terceiro-municipio-caraguatatuba-camara-e-prefeitura',
  paulinia: 'doc-quarto-municipio-paulinia-camara-e-prefeitura',
};

export function ancoraFonte(fonte: string): string | undefined {
  return ANCORAS_FONTES[fonte];
}
