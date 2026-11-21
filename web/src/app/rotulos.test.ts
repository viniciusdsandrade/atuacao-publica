import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

import {
  ANCORAS_FONTES,
  CARGOS,
  ESFERAS,
  ancoraFonte,
  casaLegislativa,
  periodoDosAnos,
  ENTES_MUNICIPAIS,
  rotuloCargo,
  rotuloEsfera,
  rotuloTipoOrgao,
  rotuloTipoProposicao,
  ePapelLideranca,
  nomeDoEnte,
  nomeDoPartido,
  partidoComNome,
  rotuloAcessivelPartido,
  rotuloLocal,
  chaveDeBusca,
  moeda,
  data,
  notaDoBloco,
  unidadeDoBloco,
  rotuloCurtoBloco,
  valorLongo,
  situacaoDe,
  tituloDoBloco,
  realcar,
  NAO_COLETADO,
  SEM_REGISTRO,
  textoDoBlocoVazio,
} from './rotulos.ts';

test('rotuloCargo retorna nome legível dos cargos municipais e gerais', () => {
  assert.equal(rotuloCargo('prefeito'), 'Prefeito(a)');
  assert.equal(rotuloCargo('vice_prefeito'), 'Vice-prefeito(a)');
  assert.equal(rotuloCargo('vice_governador'), 'Vice-governador(a)');
  assert.equal(rotuloCargo('vice_presidente'), 'Vice-presidente da República');
  assert.equal(rotuloCargo('vereador'), 'Vereador(a)');
  assert.equal(rotuloCargo('deputado_federal'), 'Deputado(a) federal');
  assert.equal(rotuloCargo('deputado_estadual'), 'Deputado(a) estadual');
  assert.equal(rotuloCargo('deputado_distrital'), 'Deputado(a) distrital');
  assert.equal(rotuloCargo('senador'), 'Senador(a)');
  assert.equal(rotuloCargo('governador'), 'Governador(a)');
  assert.equal(rotuloCargo('presidente'), 'Presidente da República');
  assert.equal(rotuloCargo('desconhecido'), 'desconhecido');
});

test('rotuloTipoOrgao retorna rótulo descritivo', () => {
  assert.equal(rotuloTipoOrgao('comissao'), 'Comissão');
  assert.equal(rotuloTipoOrgao('frente'), 'Frente parlamentar');
  assert.equal(rotuloTipoOrgao('orgao'), 'Órgão / Conselho');
  assert.equal(rotuloTipoOrgao(null), 'Sem registro na fonte.');
  assert.equal(rotuloTipoOrgao('outro'), 'outro');
});

test('ePapelLideranca identifica cargos de comando em colegiados', () => {
  assert.equal(ePapelLideranca('Presidente'), true);
  assert.equal(ePapelLideranca('1º Vice-Presidente'), true);
  assert.equal(ePapelLideranca('Vice-Presidenta'), true);
  assert.equal(ePapelLideranca('Coordenador-Geral'), true);
  assert.equal(ePapelLideranca('Relator'), true);
  assert.equal(ePapelLideranca('Secretário de Transparência'), true);
  assert.equal(ePapelLideranca('Titular'), false);
  assert.equal(ePapelLideranca('Suplente'), false);
  assert.equal(ePapelLideranca('Membro'), false);
  assert.equal(ePapelLideranca(null), false);
});

test('casaLegislativa junta só parlamentares da mesma casa', () => {
  assert.equal(casaLegislativa('deputado_federal', 'SP', 'BR'), casaLegislativa('deputado_federal', 'RJ', 'BR'));
  assert.notEqual(casaLegislativa('deputado_federal', 'SP', 'BR'), casaLegislativa('senador', 'SP', 'BR'));
  assert.equal(casaLegislativa('deputado_estadual', 'SP', 'SP'), casaLegislativa('deputado_estadual', 'SP', 'SP'));
  assert.notEqual(casaLegislativa('deputado_estadual', 'SP', 'SP'), casaLegislativa('deputado_estadual', 'SC', 'SC'));
  assert.notEqual(casaLegislativa('vereador', 'SP', '3550308'), casaLegislativa('vereador', 'SP', '3509502'));
  assert.equal(casaLegislativa('deputado_distrital', 'DF', 'DF'), 'assembleia-DF');
  assert.equal(casaLegislativa('governador', 'SP', 'SP'), null);
  assert.equal(casaLegislativa('presidente', null, 'BR'), null);
  assert.equal(casaLegislativa('prefeito', 'SP', '3550308'), null);
  assert.equal(casaLegislativa('deputado_estadual', null, null), null);
});

test('periodoDosAnos mostra o primeiro e o último ano', () => {
  assert.equal(periodoDosAnos([2024, 2022, 2026]), '2022 a 2026');
  assert.equal(periodoDosAnos([2025]), '2025');
  assert.equal(periodoDosAnos([]), '');
});

test('rotuloEsfera mapeia esferas corretamente', () => {
  assert.equal(rotuloEsfera('federal'), 'Federal');
  assert.equal(rotuloEsfera('estadual'), 'Estadual');
  assert.equal(rotuloEsfera('municipal'), 'Municipal');
});

test('nomeDoEnte resolve código IBGE para nome de município', () => {
  assert.equal(nomeDoEnte('3509502'), 'Campinas');
  assert.equal(nomeDoEnte('3550308'), 'São Paulo');
  assert.equal(nomeDoEnte('BR'), 'BR');
  assert.equal(nomeDoEnte(null), '');
  assert.equal(nomeDoEnte(undefined), '');
});

test('rotuloLocal formata localização por esfera e ente', () => {
  assert.equal(rotuloLocal('municipal', '3509502', 'SP'), 'Campinas (SP)');
  assert.equal(rotuloLocal('municipal', '3509502', null), 'Campinas');
  assert.equal(rotuloLocal('municipal', '3550308', 'SP'), 'São Paulo (SP)');
  assert.equal(rotuloLocal('municipal', '3510500', 'SP'), 'Caraguatatuba (SP)');
  assert.equal(rotuloLocal('municipal', '3536505', 'SP'), 'Paulínia (SP)');
  assert.equal(rotuloLocal('estadual', 'SP', 'SP'), 'SP');
  assert.equal(rotuloLocal('estadual', 'ES', 'ES'), 'ES');
  assert.equal(rotuloLocal('estadual', 'GO', 'GO'), 'GO');
  assert.equal(rotuloLocal('federal', 'BR', 'RJ'), 'RJ');
  assert.equal(rotuloLocal('federal', 'BR', null), '');
});

test('chaveDeBusca normaliza acentuação e caixa', () => {
  assert.equal(chaveDeBusca('DÁRIO SAADI'), 'dario saadi');
  assert.equal(chaveDeBusca('Cabo Müller'), 'cabo muller');
  assert.equal(chaveDeBusca('São Paulo'), 'sao paulo');
});

test('moeda formata valores em BRL', () => {
  assert.equal(moeda(null), 'Sem registro na fonte.');
  assert.match(moeda(1250.5), /1\.250,50/);
});

test('situacaoDe separa exercício, posse prevista e fora de exercício (ADR-0006)', () => {
  assert.equal(situacaoDe({ em_exercicio: true, situacao: 'Exercício' }), 'exercicio');
  assert.equal(situacaoDe({ em_exercicio: true, situacao: null }), 'exercicio');
  assert.equal(situacaoDe({ em_exercicio: false, situacao: 'Eleito em 2026; posse prevista em 2027-02-01' }), 'posse');
  assert.equal(situacaoDe({ em_exercicio: false, situacao: 'Mandato encerrado em 2023-01-31' }), 'fora');
  assert.equal(situacaoDe({ em_exercicio: false, situacao: null }), 'fora');
});

test('título e nota dos atos e das despesas do Executivo dependem da esfera', () => {
  assert.match(tituloDoBloco('atos_normativos', 'municipal'), /município/);
  assert.match(tituloDoBloco('atos_normativos', 'estadual'), /Estado/);
  assert.match(tituloDoBloco('atos_normativos', 'federal'), /Executivo federal/);
  assert.match(tituloDoBloco('despesas_executivo', 'federal'), /Presidência/);
  assert.match(tituloDoBloco('despesas_executivo', 'estadual'), /estadual/);
  assert.match(tituloDoBloco('despesas_executivo', 'municipal'), /município/);
  assert.match(notaDoBloco('atos_normativos', 'municipal') ?? '', /SAPL/);
  assert.match(notaDoBloco('atos_normativos', 'estadual', 'SP') ?? '', /São Paulo/);
  assert.match(notaDoBloco('atos_normativos', 'estadual', 'MG') ?? '', /Minas Gerais/);
  assert.match(notaDoBloco('agenda_oficial', 'estadual', 'MG') ?? '', /Agência Minas/);
  assert.match(notaDoBloco('agenda_oficial', 'estadual', 'SP') ?? '', /página de cada dia/);
  // Sem fonte validada no estado, o bloco vazio diz isso, e não cita a fonte de outro estado.
  assert.match(notaDoBloco('atos_normativos', 'estadual', 'RJ') ?? '', /ainda não tem fonte validada/);
  assert.match(notaDoBloco('despesas_executivo', 'estadual', 'MG') ?? '', /ainda não tem fonte validada/);
  assert.match(notaDoBloco('agenda_oficial', 'estadual', 'RJ') ?? '', /ainda não tem fonte validada/);
  assert.match(notaDoBloco('atos_normativos', 'federal') ?? '', /não informa quem assinou/);
  assert.match(notaDoBloco('despesas_executivo', 'estadual', 'SP') ?? '', /Gabinete do Governador/);
  assert.match(notaDoBloco('despesas_executivo', 'estadual', 'GO') ?? '', /linhas da fonte/);
  assert.equal(unidadeDoBloco('despesas_executivo', 'estadual', 'GO'), 'lançamentos');
  assert.equal(unidadeDoBloco('despesas_executivo', 'estadual', 'SP'), 'pagamentos');
  assert.equal(unidadeDoBloco('despesas_executivo', 'municipal', null), 'pagamentos');
  assert.equal(unidadeDoBloco('despesas', 'federal', null), 'lançamentos');
  assert.equal(notaDoBloco('votos', 'federal'), null);
});

test('na esfera municipal, despesas, atos e proposições dizem o que cada câmara publica (ADR-0057)', () => {
  assert.equal(tituloDoBloco('despesas', 'municipal'), 'Despesas informadas pela câmara municipal');
  assert.equal(tituloDoBloco('despesas', 'federal'), 'Despesas da cota parlamentar');
  assert.equal(rotuloCurtoBloco('despesas', 'municipal'), 'Despesas');
  assert.equal(rotuloCurtoBloco('despesas', 'estadual'), 'Despesas da cota');
  assert.match(notaDoBloco('despesas', 'municipal', '3510500') ?? '', /diárias, pela data de concessão/);
  assert.match(notaDoBloco('despesas', 'federal') ?? '', /Valores líquidos/);
  // Só o município do piloto tem atos pelo SAPL. Nos outros, a nota diz que não há fonte validada.
  assert.match(notaDoBloco('atos_normativos', 'municipal', '3509502') ?? '', /SAPL/);
  assert.match(notaDoBloco('atos_normativos', 'municipal', '3510500') ?? '', /ainda não tem fonte validada/);
  assert.match(notaDoBloco('atos_normativos', 'municipal', '3550308') ?? '', /ainda não tem fonte validada/);
  // A situação de tramitação fica vazia nos vereadores de Caraguatatuba: a nota diz por quê.
  assert.match(notaDoBloco('proposicoes', 'municipal', '3510500') ?? '', /não mantém a situação de tramitação/);
  assert.equal(notaDoBloco('proposicoes', 'municipal', '3509502'), null);
  assert.equal(notaDoBloco('proposicoes', 'federal'), null);
  assert.match(notaDoBloco('eventos', 'municipal', '3510500') ?? '', /Câmara Municipal de Caraguatatuba/);
});

test('vereador de Paulínia: o que fica no sistema legislativo sai como não coletado, e não como ausente na fonte', () => {
  for (const bloco of ['proposicoes', 'votos', 'participacao_votacoes', 'eventos']) {
    assert.equal(textoDoBlocoVazio(bloco, 'municipal', '3536505', 'vereador'), NAO_COLETADO);
    assert.match(notaDoBloco(bloco, 'municipal', '3536505', 'vereador') ?? '', /não permite coleta automática/);
  }
  // Os demais blocos, o prefeito e os outros municípios seguem com "sem registro na fonte".
  assert.equal(textoDoBlocoVazio('discursos', 'municipal', '3536505', 'vereador'), SEM_REGISTRO);
  assert.equal(textoDoBlocoVazio('votos', 'municipal', '3536505', 'prefeito'), SEM_REGISTRO);
  assert.equal(textoDoBlocoVazio('votos', 'municipal', '3510500', 'vereador'), SEM_REGISTRO);
  assert.equal(textoDoBlocoVazio('votos', 'federal', 'BR', 'deputado_federal'), SEM_REGISTRO);
  // A câmara declara no portal que não paga cota nem verba indenizatória: a nota repete a declaração, com a data.
  assert.match(notaDoBloco('despesas', 'municipal', '3536505', 'vereador') ?? '', /não há cota parlamentar nem verba indenizatória/);
  assert.match(notaDoBloco('atos_normativos', 'municipal', '3536505', 'prefeito') ?? '', /ainda não tem fonte validada/);
});

test('valor de cem milhões para cima usa letra menor no cartão de total', () => {
  assert.equal(valorLongo(99_999_999.99), false);
  assert.equal(valorLongo(100_000_000), true);
  assert.equal(valorLongo(1_920_647_454.69), true);
  assert.equal(valorLongo(-250_000_000), true);
  assert.equal(valorLongo(0), false);
  assert.equal(valorLongo(null), false);
  assert.equal(valorLongo(undefined), false);
});

test('data formata ISO para dd/mm/aaaa', () => {
  assert.equal(data('2025-01-01'), '01/01/2025');
  assert.equal(data('2024-10-06T15:30:00Z'), '06/10/2024');
  assert.equal(data(null), '-');
});

test('realcar marca o termo sem acento nem caixa e devolve o texto original inteiro', () => {
  assert.deepEqual(realcar('João Tábata', ['tab']), [
    { t: 'João ', m: false },
    { t: 'Táb', m: true },
    { t: 'ata', m: false },
  ]);
  assert.deepEqual(realcar('São Paulo', ['sao', 'pa']), [
    { t: 'São', m: true },
    { t: ' ', m: false },
    { t: 'Pa', m: true },
    { t: 'ulo', m: false },
  ]);
  // Acento escrito como caractere solto depois da letra entra no trecho marcado.
  assert.deepEqual(realcar('Jose\u0301 Silva', ['jose']), [
    { t: 'Jose\u0301', m: true },
    { t: ' Silva', m: false },
  ]);
  for (const [texto, termos] of [
    ['Ana Exemplo', ['ana', 'exe']],
    ['Luiz Philippe de Orleans e Bragança', ['e', 'ca']],
    ['Deputado(a) federal', ['zzz']],
  ] as [string, string[]][]) {
    assert.equal(realcar(texto, termos).map((trecho) => trecho.t).join(''), texto);
  }
});

test('realcar sem termo ou sem texto devolve um trecho só, sem marca', () => {
  assert.deepEqual(realcar('Ana Exemplo', []), [{ t: 'Ana Exemplo', m: false }]);
  assert.deepEqual(realcar('', ['ana']), [{ t: '', m: false }]);
  assert.deepEqual(realcar('Ana Exemplo', ['bruno']), [{ t: 'Ana Exemplo', m: false }]);
});

test('partidos resolvem nome completo sem repetir sigla idêntica (ADR-0064)', () => {
  assert.equal(nomeDoPartido('PT'), 'Partido dos Trabalhadores');
  assert.equal(nomeDoPartido('UNIÃO'), 'União Brasil');
  assert.equal(nomeDoPartido('PC do B'), 'Partido Comunista do Brasil');
  assert.equal(nomeDoPartido('PCdoB'), 'Partido Comunista do Brasil');
  assert.equal(nomeDoPartido('MISSÃO'), 'Partido Missão');
  assert.equal(nomeDoPartido('Sem partido'), null);
  assert.equal(partidoComNome('PT'), 'PT · Partido dos Trabalhadores');
  assert.equal(partidoComNome('NOVO'), 'NOVO · Partido Novo');
  assert.equal(partidoComNome('AVANTE'), 'AVANTE');
  assert.equal(partidoComNome('REPUBLICANOS'), 'REPUBLICANOS');
  assert.equal(partidoComNome('Sem partido'), 'Sem partido');
  assert.equal(rotuloAcessivelPartido('MDB'), 'MDB, Movimento Democrático Brasileiro');
  assert.equal(rotuloAcessivelPartido('SOLIDARIEDADE'), null);
});

test('rotuloTipoProposicao traduz siglas oficiais sem duplicar textos por extenso (ADR-0064)', () => {
  assert.equal(rotuloTipoProposicao('PL'), 'PL · Projeto de Lei');
  assert.equal(rotuloTipoProposicao('PLP'), 'PLP · Projeto de Lei Complementar');
  assert.equal(rotuloTipoProposicao('RIC'), 'RIC · Requerimento de Informação');
  assert.equal(rotuloTipoProposicao('RQS'), 'RQS · Requerimento do Senado');
  assert.equal(rotuloTipoProposicao('Projeto de Lei'), 'Projeto de Lei');
  assert.equal(rotuloTipoProposicao('INDICAÇÃO'), 'Indicação');
  assert.equal(rotuloTipoProposicao('DESCONHECIDA'), 'DESCONHECIDA');
});

test('nota de eventos cita as assembleias SAPL adicionadas (ADR-0063 e ADR-0064)', () => {
  const nota = notaDoBloco('eventos', 'estadual') ?? '';
  for (const uf of ['ALPB', 'ALEAM', 'ALERO', 'ALEPI', 'ALEAC']) {
    assert.match(nota, new RegExp(uf));
  }
});

test('todas as âncoras de fonte mapeadas em Estado existem nos títulos de docs/FONTES.md', () => {
  const md = fs.readFileSync(path.resolve(import.meta.dirname, '../../../docs/FONTES.md'), 'utf8');
  const estadoTs = fs.readFileSync(path.resolve(import.meta.dirname, './estado/estado.ts'), 'utf8');
  const ids = new Set<string>();
  for (const linha of md.split(/\r?\n/)) {
    const m = /^#{2,3}\s+(.+)$/.exec(linha);
    if (!m) continue;
    const texto = m[1].replace(/`/g, '');
    const slug = texto
      .normalize('NFD')
      .replace(/\p{M}/gu, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
    ids.add('doc-' + (slug || 'secao'));
  }

  for (const [fonte, id] of Object.entries(ANCORAS_FONTES)) {
    assert.ok(ids.has(id), `Âncora ausente em docs/FONTES.md para ${fonte}: ${id}`);
    assert.equal(ancoraFonte(fonte), id);
  }

  // Garante também que toda chave do mapa NOMES em estado.ts possui uma âncora mapeada.
  const blocoNomes = /const NOMES:[^=]+=\s*\{([\s\S]*?)\};/.exec(estadoTs);
  assert.ok(blocoNomes, 'bloco NOMES não encontrado em estado.ts');
  for (const m of blocoNomes[1].matchAll(/^\s*([a-z0-9]+):/gm)) {
    assert.ok(m[1] in ANCORAS_FONTES, `Fonte ${m[1]} de estado.ts sem âncora em ANCORAS_FONTES`);
  }
});
