import test from 'node:test';
import assert from 'node:assert/strict';

import {
  CHAVE_STORAGE_TEMA,
  aplicarTemaNoDocumento,
  lerTemaSalvo,
  salvarTema,
  temaAoAlternar,
  temaEfetivo,
} from './tema.ts';

test('temaEfetivo usa a escolha salva ou, sem escolha, o tema do aparelho', () => {
  assert.equal(temaEfetivo('sistema', true), 'escuro');
  assert.equal(temaEfetivo('sistema', false), 'claro');
  assert.equal(temaEfetivo('claro', true), 'claro');
  assert.equal(temaEfetivo('escuro', false), 'escuro');
});

test('temaAoAlternar troca o tema da tela e volta a seguir o aparelho quando chega nele', () => {
  // Aparelho escuro: sair do escuro salva "claro"; voltar ao escuro apaga a escolha.
  assert.equal(temaAoAlternar('escuro', true), 'claro');
  assert.equal(temaAoAlternar('claro', true), 'sistema');
  // Aparelho claro: o inverso.
  assert.equal(temaAoAlternar('claro', false), 'escuro');
  assert.equal(temaAoAlternar('escuro', false), 'sistema');
});

test('cada clique do interruptor mostra o outro tema na tela', () => {
  for (const sistemaEscuro of [true, false]) {
    for (const efetivo of ['claro', 'escuro'] as const) {
      const depois = temaEfetivo(temaAoAlternar(efetivo, sistemaEscuro), sistemaEscuro);
      assert.notEqual(depois, efetivo);
    }
  }
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

test('o script de arranque usa a mesma chave de storage do tema', async () => {
  // public/arranque.js aplica o tema antes de o site iniciar; a chave precisa ser a mesma daqui.
  const { readFile } = await import('node:fs/promises');
  const arranque = await readFile(new URL('../../public/arranque.js', import.meta.url), 'utf8');
  assert.ok(arranque.includes(`'${CHAVE_STORAGE_TEMA}'`));
});
