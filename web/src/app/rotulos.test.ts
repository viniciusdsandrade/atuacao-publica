import test from 'node:test';
import assert from 'node:assert/strict';

import {
  CARGOS,
  ESFERAS,
  ENTES_MUNICIPAIS,
  rotuloCargo,
  rotuloEsfera,
  rotuloTipoOrgao,
  ePapelLideranca,
  nomeDoEnte,
  rotuloLocal,
  chaveDeBusca,
  moeda,
  data,
  notaDoBloco,
  situacaoDe,
  tituloDoBloco,
} from './rotulos.ts';

test('rotuloCargo retorna nome legível dos cargos municipais e gerais', () => {
  assert.equal(rotuloCargo('prefeito'), 'Prefeito(a)');
  assert.equal(rotuloCargo('vice_prefeito'), 'Vice-prefeito(a)');
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

test('rotuloEsfera mapeia esferas corretamente', () => {
  assert.equal(rotuloEsfera('federal'), 'Federal');
  assert.equal(rotuloEsfera('estadual'), 'Estadual');
  assert.equal(rotuloEsfera('municipal'), 'Municipal');
});

test('nomeDoEnte resolve código IBGE para nome de município', () => {
  assert.equal(nomeDoEnte('3509502'), 'Campinas');
  assert.equal(nomeDoEnte('BR'), 'BR');
  assert.equal(nomeDoEnte(null), '');
  assert.equal(nomeDoEnte(undefined), '');
});

test('rotuloLocal formata localização por esfera e ente', () => {
  assert.equal(rotuloLocal('municipal', '3509502', 'SP'), 'Campinas (SP)');
  assert.equal(rotuloLocal('municipal', '3509502', null), 'Campinas');
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
  assert.match(notaDoBloco('atos_normativos', 'estadual') ?? '', /São Paulo/);
  assert.match(notaDoBloco('atos_normativos', 'federal') ?? '', /não informa quem assinou/);
  assert.match(notaDoBloco('despesas_executivo', 'estadual') ?? '', /Gabinete do Governador/);
  assert.equal(notaDoBloco('votos', 'federal'), null);
});

test('data formata ISO para dd/mm/aaaa', () => {
  assert.equal(data('2025-01-01'), '01/01/2025');
  assert.equal(data('2024-10-06T15:30:00Z'), '06/10/2024');
  assert.equal(data(null), '-');
});
