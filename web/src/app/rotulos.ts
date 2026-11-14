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
  '3550308': 'São Paulo',
};

/** Município do piloto (ADR-0007): o único com atos normativos pelo SAPL. */
const ENTE_DO_PILOTO = '3509502';
/** Caraguatatuba (ADR-0057): a câmara não mantém a situação de tramitação. */
const ENTE_DE_CARAGUATATUBA = '3510500';

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
    'Na Câmara dos Deputados e no piloto municipal, a fonte lista só quem esteve presente. Na ALESC, lista também a ausência justificada em cada sessão ordinária; na ALBA e na Câmara Municipal de Caraguatatuba, a falta justificada e a falta sem justificativa em cada sessão plenária. O site não calcula taxa de presença.',
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
export function notaDoBloco(bloco: string, esfera?: string | null, ente?: string | null): string | null {
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
