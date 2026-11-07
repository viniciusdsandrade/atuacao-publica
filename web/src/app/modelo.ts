// Tipos dos arquivos publicados pelo ETL. Fonte da verdade: schemas/*.schema.json.

export interface FonteUsada {
  fonte_id: string;
  url_origem: string;
  capturado_em: string;
}

export interface Proveniencia extends FonteUsada {
  snapshot_url?: string;
  contribuidor?: string;
}

export interface EntradaIndice {
  id: string;
  nome: string;
  cargo: string;
  esfera: string;
  ente: string;
  uf: string | null;
  partido: string | null;
  situacao: string | null;
  em_exercicio: boolean;
  foto_url: string | null;
}

export interface Indice {
  gerado_em: string;
  total: number;
  politicos: EntradaIndice[];
  aliases: Record<string, string>;
}

export interface ArquivoAnual {
  ano: number;
  caminho: string;
  total: number;
}

export interface Bloco<T = Registro> {
  fontes: FonteUsada[];
  atualizado_em: string | null;
  desatualizado: boolean;
  total: number;
  por_tipo?: Record<string, number>;
  arquivos?: ArquivoAnual[];
  registros?: T[];
  recentes?: T[];
  valor_total?: number;
  por_mes?: DespesaAgregada[];
  por_categoria?: DespesaAgregada[];
  por_ano?: EmendaPorAno[];
}

/** Soma dos valores das emendas de um ano do orçamento (ADR-0030). */
export interface EmendaPorAno extends Proveniencia {
  ano: number;
  quantidade: number;
  valor_empenhado: number;
  valor_liquidado: number;
  valor_pago: number;
  restos_inscritos: number;
  restos_cancelados: number;
  restos_pagos: number;
}

export type Registro = Proveniencia & Record<string, unknown>;

export interface DespesaAgregada extends Proveniencia {
  ano: number;
  mes: number | null;
  categoria: string | null;
  valor: number;
  quantidade: number;
}

/** Soma de uma categoria da prestação de contas (ADR-0032). */
export interface SomaDeCampanha {
  categoria: string;
  valor: number;
  quantidade: number;
}

export interface FornecedorDeCampanha {
  nome: string | null;
  cnpj: string;
  valor: number;
  quantidade: number;
}

/** Receitas ou despesas contratadas de uma candidatura, como publicadas no bloco financiamento_campanha. */
export interface ContasDeCampanha extends Proveniencia {
  eleicao: number;
  cargo: string;
  tipo: 'receitas' | 'despesas contratadas';
  total: number;
  quantidade: number;
  por_fonte?: SomaDeCampanha[];
  por_origem?: SomaDeCampanha[];
  por_tipo?: SomaDeCampanha[];
  maiores_fornecedores?: FornecedorDeCampanha[];
}

/** Rede social ou site declarado ao TSE no registro da candidatura (ADR-0031). */
export interface RedeDeclarada extends Proveniencia {
  rede: string;
  url: string;
}

export interface LinkOficial extends Proveniencia {
  rotulo: string;
  url: string;
}

export interface Perfil {
  id: string;
  nome: string;
  cargo_atual: string;
  esfera: string;
  ente: string;
  uf: string | null;
  partido_atual: string | null;
  situacao: string | null;
  em_exercicio: boolean;
  foto_url: string | null;
  ids_externos: Record<string, unknown>;
  links_oficiais: LinkOficial[];
  atualizado_em: string;
  blocos: Record<string, Bloco>;
}

export interface Detalhe {
  politico_id: string;
  tipo: string;
  ano: number;
  gerado_em: string;
  registros: Registro[];
}

export interface Fonte {
  id: string;
  nome: string;
  orgao: string;
  nivel_confianca: string;
  url_base: string;
  status: string;
  validado_em: string | null;
}

export interface Execucao {
  projeto: { nome: string; versao: string; repositorio_url: string | null };
  iniciado_em: string;
  terminado_em: string;
  fontes: { fonte: string; status: string; ultima_coleta_ok: string | null; mensagem: string | null }[];
  publicados: number;
}
