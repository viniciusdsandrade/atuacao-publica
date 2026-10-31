import { Component, input } from '@angular/core';

import { Registro } from '../modelo';
import { data, moeda, nomeDoEnte, rotuloCargo, rotuloLocal, SEM_REGISTRO } from '../rotulos';

/** Tabela de registros de um bloco. Usada no perfil (mais recentes) e no detalhe anual (todos). */
@Component({
  selector: 'app-tabela',
  templateUrl: './tabela.html',
})
export class Tabela {
  readonly bloco = input.required<string>();
  readonly registros = input.required<Registro[]>();
  readonly legenda = input<string>('');

  protected readonly data = data;
  protected readonly moeda = moeda;
  protected readonly rotuloCargo = rotuloCargo;
  protected readonly rotuloLocal = rotuloLocal;
  protected readonly nomeDoEnte = nomeDoEnte;
  protected readonly semRegistro = SEM_REGISTRO;

  protected localCandidatura(r: Registro): string {
    const ente = typeof r['ente'] === 'string' ? r['ente'] : '';
    if (ente === '3509502') {
      return 'Campinas (SP)';
    }
    if (ente === 'BR') {
      return 'Brasil';
    }
    return ente || SEM_REGISTRO;
  }

  protected texto(valor: unknown): string {
    return valor === null || valor === undefined || valor === '' ? SEM_REGISTRO : String(valor);
  }

  protected numero(valor: unknown): number | null {
    return typeof valor === 'number' ? valor : null;
  }

  protected porTipo(valor: unknown): string {
    if (!valor || typeof valor !== 'object') {
      return SEM_REGISTRO;
    }
    return Object.entries(valor as Record<string, number>)
      .map(([tipo, n]) => `${tipo}: ${n}`)
      .join('; ');
  }

  protected lista(valor: unknown): string {
    return Array.isArray(valor) && valor.length ? valor.join('; ') : SEM_REGISTRO;
  }

  protected url(valor: unknown): string | null {
    return typeof valor === 'string' && /^https?:\/\//.test(valor) ? valor : null;
  }
}
