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
}

export type Registro = Proveniencia & Record<string, unknown>;

export interface DespesaAgregada extends Proveniencia {
  ano: number;
  mes: number | null;
  categoria: string | null;
  valor: number;
  quantidade: number;
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
