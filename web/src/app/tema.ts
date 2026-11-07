/**
 * Preferência de tema do visitante:
 * - 'sistema': segue a preferência do sistema operacional (prefers-color-scheme);
 * - 'claro': força o tema claro;
 * - 'escuro': força o tema escuro.
 * O controle é um interruptor entre claro e escuro. Sem escolha salva, o site segue o aparelho.
 */
export type Tema = 'sistema' | 'claro' | 'escuro';

/** Tema que está na tela. */
export type TemaEfetivo = 'claro' | 'escuro';

export const CHAVE_STORAGE_TEMA = 'atuacao-publica:tema';

/** Nome acessível do interruptor: ligado no tema claro, desligado no escuro. */
export const ROTULO_INTERRUPTOR = 'Tema claro';

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

/** Tema na tela: a escolha salva ou, sem escolha, o do aparelho. */
export function temaEfetivo(tema: Tema, sistemaEscuro: boolean): TemaEfetivo {
  if (tema === 'sistema') {
    return sistemaEscuro ? 'escuro' : 'claro';
  }
  return tema;
}

/**
 * O interruptor troca o tema que está na tela. Quando a troca chega ao tema do aparelho,
 * a escolha salva some e o site volta a seguir o aparelho.
 */
export function temaAoAlternar(efetivo: TemaEfetivo, sistemaEscuro: boolean): Tema {
  const novo: TemaEfetivo = efetivo === 'escuro' ? 'claro' : 'escuro';
  return (novo === 'escuro') === sistemaEscuro ? 'sistema' : novo;
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
