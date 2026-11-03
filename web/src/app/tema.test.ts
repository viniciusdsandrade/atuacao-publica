import test from 'node:test';
import assert from 'node:assert/strict';

import {
  CHAVE_STORAGE_TEMA,
  aplicarTemaNoDocumento,
  iconeTema,
  lerTemaSalvo,
  proximoTema,
  rotuloCurtoTema,
  rotuloTema,
  salvarTema,
} from './tema.ts';

test('proximoTema cicla entre sistema, claro e escuro', () => {
  assert.equal(proximoTema('sistema'), 'claro');
  assert.equal(proximoTema('claro'), 'escuro');
  assert.equal(proximoTema('escuro'), 'sistema');
});

test('rotuloCurtoTema retorna rótulo resumido de cada estado', () => {
  assert.equal(rotuloCurtoTema('sistema'), 'Auto');
  assert.equal(rotuloCurtoTema('claro'), 'Claro');
  assert.equal(rotuloCurtoTema('escuro'), 'Escuro');
});

test('iconeTema retorna ícone adequado ao tema', () => {
  assert.equal(iconeTema('sistema'), 'sistema');
  assert.equal(iconeTema('claro'), 'sol');
  assert.equal(iconeTema('escuro'), 'lua');
});

test('rotuloTema descreve o tema atual e a próxima ação para acessibilidade', () => {
  assert.match(rotuloTema('sistema'), /automático/);
  assert.match(rotuloTema('claro'), /claro/);
  assert.match(rotuloTema('escuro'), /escuro/);
});

test('salvarTema e lerTemaSalvo persistem no storage', () => {
  const dados = new Map<string, string>();
  const mockStorage = {
    getItem: (k: string) => dados.get(k) ?? null,
    setItem: (k: string, v: string) => dados.set(k, v),
    removeItem: (k: string) => dados.delete(k),
  } as unknown as Storage;

  assert.equal(lerTemaSalvo(mockStorage), 'sistema');

  salvarTema('claro', mockStorage);
  assert.equal(lerTemaSalvo(mockStorage), 'claro');
  assert.equal(dados.get(CHAVE_STORAGE_TEMA), 'claro');

  salvarTema('escuro', mockStorage);
  assert.equal(lerTemaSalvo(mockStorage), 'escuro');

  salvarTema('sistema', mockStorage);
  assert.equal(lerTemaSalvo(mockStorage), 'sistema');
  assert.equal(dados.has(CHAVE_STORAGE_TEMA), false);
});

test('aplicarTemaNoDocumento manipula atributo data-tema na raiz', () => {
  const atributos = new Map<string, string>();
  const mockRaiz = {
    setAttribute: (k: string, v: string) => atributos.set(k, v),
    removeAttribute: (k: string) => atributos.delete(k),
  } as unknown as HTMLElement;

  aplicarTemaNoDocumento('escuro', mockRaiz);
  assert.equal(atributos.get('data-tema'), 'escuro');

  aplicarTemaNoDocumento('claro', mockRaiz);
  assert.equal(atributos.get('data-tema'), 'claro');

  aplicarTemaNoDocumento('sistema', mockRaiz);
  assert.equal(atributos.has('data-tema'), false);
});
