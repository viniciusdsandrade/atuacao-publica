/**
 * Preferência de tema do visitante:
 * - 'sistema': segue a preferência do sistema operacional (prefers-color-scheme);
 * - 'claro': força o tema claro;
 * - 'escuro': força o tema escuro.
 */
export type Tema = 'sistema' | 'claro' | 'escuro';

export const CHAVE_STORAGE_TEMA = 'atuacao-publica:tema';

export function lerTemaSalvo(armazenamento: Storage | null = obterStorage()): Tema {
  try {
    const salvo = armazenamento?.getItem(CHAVE_STORAGE_TEMA);
    if (salvo === 'claro' || salvo === 'escuro' || salvo === 'sistema') {
      return salvo;
    }
  } catch {
    // Ambiente sem localStorage ou permissão negada.
  }
  return 'sistema';
}

export function salvarTema(tema: Tema, armazenamento: Storage | null = obterStorage()): void {
  try {
    if (!armazenamento) {
      return;
    }
    if (tema === 'sistema') {
      armazenamento.removeItem(CHAVE_STORAGE_TEMA);
    } else {
      armazenamento.setItem(CHAVE_STORAGE_TEMA, tema);
    }
  } catch {
    // Ambiente sem localStorage ou permissão negada.
  }
}

export function aplicarTemaNoDocumento(tema: Tema, raiz: HTMLElement | null = obterRaiz()): void {
  if (!raiz) {
    return;
  }
  if (tema === 'sistema') {
    raiz.removeAttribute('data-tema');
  } else {
    raiz.setAttribute('data-tema', tema);
  }
}

export function proximoTema(atual: Tema): Tema {
  switch (atual) {
    case 'sistema':
      return 'claro';
    case 'claro':
      return 'escuro';
    case 'escuro':
      return 'sistema';
  }
}

export function rotuloCurtoTema(tema: Tema): string {
  switch (tema) {
    case 'sistema':
      return 'Auto';
    case 'claro':
      return 'Claro';
    case 'escuro':
      return 'Escuro';
  }
}

export function rotuloTema(tema: Tema): string {
  switch (tema) {
    case 'sistema':
      // O nome acessível contém o texto visível do botão ("Auto", "Claro", "Escuro"): WCAG 2.5.3, rótulo no nome.
      return 'Tema do site: Auto, segue o sistema. Clique para mudar para tema claro.';
    case 'claro':
      return 'Tema do site: Claro. Clique para mudar para tema escuro.';
    case 'escuro':
      return 'Tema do site: Escuro. Clique para mudar para Auto, que segue o sistema.';
  }
}

export function iconeTema(tema: Tema): 'sistema' | 'sol' | 'lua' {
  switch (tema) {
    case 'sistema':
      return 'sistema';
    case 'claro':
      return 'sol';
    case 'escuro':
      return 'lua';
  }
}

function obterStorage(): Storage | null {
  if (typeof window !== 'undefined' && window.localStorage) {
    return window.localStorage;
  }
  return null;
}

function obterRaiz(): HTMLElement | null {
  if (typeof document !== 'undefined' && document.documentElement) {
    return document.documentElement;
  }
  return null;
}
