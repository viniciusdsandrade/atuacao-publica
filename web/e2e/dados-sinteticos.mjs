// Dados sintéticos dos testes E2E, montados em memória a partir dos exemplos de schema.
// Os exemplos são validados pelo ETL em cada build, então têm a forma real dos JSON publicados.
// Nomes, partidos e números são fictícios. Nenhum dado real entra aqui.
import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';

const GERADO_EM = '2026-11-06T03:10:00Z';

/** Proveniência fictícia das três fontes da Câmara Municipal de Caraguatatuba (ADR-0057). */
const SAGL = { fonte_id: 'caraguatatuba-camara-sagl', url_origem: 'https://sistema.camaracaragua.sp.gov.br/consultas/materia/materia_pesquisar_proc?hdn_cod_autor=95&txt_ano=2026', capturado_em: GERADO_EM };
const DIARIAS = { fonte_id: 'caraguatatuba-transparencia', url_origem: 'https://portaldatransparencia.caraguatatuba.sp.gov.br/api/execucao/diarias/diarias?id_entidade=1&cp_ano=2026-01-01', capturado_em: GERADO_EM };

/** Blocos do vereador de Caraguatatuba: diárias como despesa e proposição sem situação, que a fonte não mantém. */
const BLOCOS_DE_CARAGUATATUBA = {
  despesas: {
    fontes: [DIARIAS],
    atualizado_em: GERADO_EM,
    desatualizado: false,
    total: 2,
    valor_total: 1100,
    arquivos: [{ ano: 2026, caminho: 'politicos/sagl-3510500-950016/despesas-2026.json.gz', total: 2 }],
    por_mes: [{ ano: 2026, mes: 3, categoria: null, valor: 1100, quantidade: 2, ...DIARIAS }],
    por_categoria: [{ ano: 2026, mes: null, categoria: 'Diária', valor: 1100, quantidade: 2, ...DIARIAS }],
  },
  proposicoes: {
    fontes: [SAGL],
    atualizado_em: GERADO_EM,
    desatualizado: false,
    total: 1,
    por_tipo: { Indicação: 1 },
    arquivos: [],
    recentes: [
      {
        id_fonte: '9001',
        tipo: 'Indicação',
        numero: 7,
        ano: 2026,
        identificacao: 'Indicação nº 7/2026',
        ementa: 'Solicita a limpeza de um canal fictício.',
        situacao: null,
        temas: null,
        data_apresentacao: '2026-03-02',
        ordem_assinatura: null,
        proponente: null,
        url_texto: null,
        ...SAGL,
        url_origem: 'https://sistema.camaracaragua.sp.gov.br/consultas/materia/materia_mostrar_proc?cod_materia=OTAwMQ==',
      },
    ],
  },
};

/** O molde é o exemplo de schema que serve de base ao perfil de cada pessoa. */
const PESSOAS = [
  { id: 'cam-900001', nome: 'Ana Exemplo', cargo: 'deputado_federal', esfera: 'federal', ente: 'BR', uf: 'SP', partido: 'PL', externos: { camara: '900001' }, molde: 'deputado' },
  { id: 'cam-900002', nome: 'Bruno Exemplo', cargo: 'deputado_federal', esfera: 'federal', ente: 'BR', uf: 'SP', partido: 'PT', externos: { camara: '900002' }, molde: 'deputado' },
  { id: 'cam-900003', nome: 'Carla Exemplo', cargo: 'deputado_federal', esfera: 'federal', ente: 'BR', uf: 'RJ', partido: 'PL', externos: { camara: '900003' }, molde: 'deputado' },
  { id: 'cam-900004', nome: 'Diego Exemplo', cargo: 'deputado_federal', esfera: 'federal', ente: 'BR', uf: 'MG', partido: 'MDB', externos: { camara: '900004' }, molde: 'deputado' },
  { id: 'cam-900005', nome: 'Elisa Exemplo', cargo: 'deputado_federal', esfera: 'federal', ente: 'BR', uf: 'PR', partido: 'PT', externos: { camara: '900005' }, molde: 'deputado', foraDeExercicio: true },
  { id: 'sen-90006', nome: 'Fabio Exemplo', cargo: 'senador', esfera: 'federal', ente: 'BR', uf: 'SP', partido: 'PSD', externos: { senado: '90006' }, molde: 'deputado' },
  { id: 'alesp-900007', nome: 'Gabriela Exemplo', cargo: 'deputado_estadual', esfera: 'estadual', ente: 'SP', uf: 'SP', partido: 'PL', externos: { alesp: '900007' }, molde: 'estadual' },
  { id: 'alesp-900008', nome: 'Heitor Exemplo', cargo: 'deputado_estadual', esfera: 'estadual', ente: 'SP', uf: 'SP', partido: 'PT', externos: { alesp: '900008' }, molde: 'estadual' },
  { id: 'alesc-irene-exemplo', nome: 'Irene Exemplo', cargo: 'deputado_estadual', esfera: 'estadual', ente: 'SC', uf: 'SC', partido: 'MDB', externos: { alesc: 'irene-exemplo' }, molde: 'estadual' },
  { id: 'tse-990000000010', nome: 'Jorge Exemplo', cargo: 'governador', esfera: 'estadual', ente: 'SP', uf: 'SP', partido: 'PSD', externos: { tse: { 2022: '990000000010' } }, molde: 'governador', situacao: 'Mandato vigente (eleito em 2022), até 2027-01-06' },
  { id: 'tse-990000000011', nome: 'Karina Exemplo', cargo: 'prefeito', esfera: 'municipal', ente: '3550308', uf: 'SP', partido: 'MDB', externos: { tse: { 2024: '990000000011' } }, molde: 'governador', situacao: 'Mandato vigente (eleito em 2024), até 2029-01-01' },
  { id: 'cmsp-940012', nome: 'Leandro Exemplo', cargo: 'vereador', esfera: 'municipal', ente: '3550308', uf: 'SP', partido: 'PT', externos: { cmsp: '940012' }, molde: 'vereador' },
  { id: 'cmsp-940013', nome: 'Marta Exemplo', cargo: 'vereador', esfera: 'municipal', ente: '3550308', uf: 'SP', partido: 'PL', externos: { cmsp: '940013' }, molde: 'vereador' },
  { id: 'sapl-3509502-930014', nome: 'Nelson Exemplo', cargo: 'vereador', esfera: 'municipal', ente: '3509502', uf: 'SP', partido: 'PSD', externos: { sapl: '930014' }, molde: 'vereador' },
  { id: 'sapl-3509502-930015', nome: 'Olga Exemplo', cargo: 'vereador', esfera: 'municipal', ente: '3509502', uf: 'SP', partido: 'PL', externos: { sapl: '930015' }, molde: 'vereador', foraDeExercicio: true },
  { id: 'sagl-3510500-950016', nome: 'Paula Exemplo', cargo: 'vereador', esfera: 'municipal', ente: '3510500', uf: 'SP', partido: 'PV', externos: { caraguatatuba: '950016' }, molde: 'vereador', blocos: BLOCOS_DE_CARAGUATATUBA },
];

const MOLDES = {
  deputado: 'politico/deputado.json',
  estadual: 'politico/deputado_estadual.json',
  governador: 'politico/governador.json',
  vereador: 'politico/vereador.json',
};

const FONTES = [
  { id: 'camara-api', nome: 'Câmara dos Deputados, dados abertos', orgao: 'Câmara dos Deputados', esfera: 'federal', ente: 'BR', categoria: 'dados', nivel_confianca: 'T0', url_base: 'https://dadosabertos.camara.leg.br/api/v2/', formato: 'JSON', autenticacao: 'nenhuma', licenca: null, limitacoes: 'Fonte fictícia dos testes.', status: 'validado', validado_em: '2026-11-06' },
  { id: 'caraguatatuba-camara-sagl', nome: 'Câmara Municipal de Caraguatatuba, sistema legislativo (SAGL)', orgao: 'Câmara Municipal de Caraguatatuba', esfera: 'municipal', ente: '3510500', categoria: 'dados', nivel_confianca: 'T0', url_base: 'https://sistema.camaracaragua.sp.gov.br/', formato: 'HTML', autenticacao: 'nenhuma', licenca: null, limitacoes: null, status: 'validado com limitação', validado_em: '2026-11-13' },
  { id: 'caraguatatuba-transparencia', nome: 'Município de Caraguatatuba, Portal da Transparência', orgao: 'Prefeitura Municipal de Caraguatatuba', esfera: 'municipal', ente: '3510500', categoria: 'transparencia', nivel_confianca: 'T0', url_base: 'https://portaldatransparencia.caraguatatuba.sp.gov.br/', formato: 'JSON', autenticacao: 'nenhuma', licenca: null, limitacoes: null, status: 'validado com limitação', validado_em: '2026-11-13' },
  { id: 'cmsp-splegisws', nome: 'Câmara Municipal de São Paulo, SPLegisWS', orgao: 'Câmara Municipal de São Paulo', esfera: 'municipal', ente: '3550308', categoria: 'dados', nivel_confianca: 'T0', url_base: 'https://splegisws.saopaulo.sp.leg.br/ws/ws2.asmx', formato: 'JSON', autenticacao: 'nenhuma', licenca: null, limitacoes: 'Fonte fictícia dos testes.', status: 'validado com limitação', validado_em: '2026-11-06' },
];

async function lerJson(raizSchemas, relativo) {
  return JSON.parse(await readFile(join(raizSchemas, 'exemplos', 'validos', relativo), 'utf-8'));
}

function perfilDe(molde, pessoa) {
  const base = structuredClone(molde);
  const idDoMolde = base.id;
  // O id do molde aparece em caminhos de arquivos de detalhe e em links. A troca textual mantém tudo coerente.
  const perfil = JSON.parse(JSON.stringify(base).replaceAll(idDoMolde, pessoa.id));
  perfil.nome = pessoa.nome;
  perfil.cargo_atual = pessoa.cargo;
  perfil.esfera = pessoa.esfera;
  perfil.ente = pessoa.ente;
  perfil.uf = pessoa.uf;
  perfil.partido_atual = pessoa.partido;
  perfil.em_exercicio = !pessoa.foraDeExercicio;
  perfil.situacao = pessoa.situacao ?? (pessoa.foraDeExercicio ? 'Fora de exercício' : 'Exercício');
  perfil.foto_url = null;
  perfil.ids_externos = pessoa.externos;
  perfil.atualizado_em = GERADO_EM;
  // Blocos que o molde não tem: entram como a fonte real os publica.
  Object.assign(perfil.blocos, structuredClone(pessoa.blocos ?? {}));
  return perfil;
}

function entradaDoIndice(perfil) {
  return {
    id: perfil.id,
    nome: perfil.nome,
    cargo: perfil.cargo_atual,
    esfera: perfil.esfera,
    ente: perfil.ente,
    uf: perfil.uf,
    partido: perfil.partido_atual,
    situacao: perfil.situacao,
    em_exercicio: perfil.em_exercicio,
    foto_url: null,
    ids_externos: perfil.ids_externos,
  };
}

/**
 * Tudo o que o site lê em /dados, por caminho relativo. O valor é um objeto JSON, um texto ou um Buffer.
 * @param {string} raizWeb pasta web/ do repositório
 */
export async function montarDados(raizWeb) {
  const raizSchemas = join(raizWeb, '..', 'schemas');
  const moldes = {};
  for (const [nome, relativo] of Object.entries(MOLDES)) {
    moldes[nome] = await lerJson(raizSchemas, relativo);
  }
  const execucao = await lerJson(raizSchemas, 'execucao/execucao.json');
  const detalheDespesas = await lerJson(raizSchemas, 'detalhe/despesas.json');
  const detalheEmendas = await lerJson(raizSchemas, 'detalhe/emendas.json');

  const dados = new Map();
  const perfis = PESSOAS.map((pessoa) => perfilDe(moldes[pessoa.molde], pessoa));
  for (const perfil of perfis) {
    dados.set(`politicos/${perfil.id}.json`, perfil);
    for (const bloco of ['votos', 'despesas']) {
      for (const arquivo of perfil.blocos[bloco]?.arquivos ?? []) {
        const detalhe = { ...detalheDespesas, politico_id: perfil.id, tipo: bloco, ano: arquivo.ano, registros: bloco === 'despesas' ? detalheDespesas.registros : [] };
        dados.set(arquivo.caminho, gzipSync(JSON.stringify(detalhe)));
      }
    }
    for (const arquivo of perfil.blocos.emendas?.arquivos ?? []) {
      const registros = detalheEmendas.registros.map((r) => ({ ...r, ano: arquivo.ano }));
      const detalhe = { ...detalheEmendas, politico_id: perfil.id, ano: arquivo.ano, registros };
      dados.set(arquivo.caminho, gzipSync(JSON.stringify(detalhe)));
    }
  }
  dados.set('index.json', { gerado_em: GERADO_EM, total: perfis.length, politicos: perfis.map(entradaDoIndice), aliases: {} });
  dados.set('fontes.json', FONTES);
  dados.set('_meta/execucao.json', execucao);

  const csv = 'id,nome,cargo\ncam-900001,Ana Exemplo,deputado_federal\n';
  const arquivos = [
    { arquivo: 'politicos.csv', formato: 'CSV', titulo: 'Todos os políticos catalogados', descricao: 'Consolidado nacional de todos os parlamentares e governantes', total_registros: perfis.length, tamanho_bytes: csv.length },
    { arquivo: 'politicos-federal.csv', formato: 'CSV', titulo: 'Esfera Federal', descricao: 'Deputados federais, senadores e Presidência da República', total_registros: 6, tamanho_bytes: csv.length },
    { arquivo: 'politicos-SP.csv', formato: 'CSV', titulo: 'São Paulo', descricao: 'Políticos de São Paulo', total_registros: 9, tamanho_bytes: csv.length },
  ];
  dados.set('dumps/manifest.json', { gerado_em: GERADO_EM, total_arquivos: arquivos.length, arquivos });
  for (const { arquivo } of arquivos) {
    dados.set(`dumps/${arquivo}`, csv);
  }
  return dados;
}
