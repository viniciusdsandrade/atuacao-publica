import { Component, computed, input, linkedSignal } from '@angular/core';

import { Registro } from '../modelo';
import { data, inteiro, moeda, nomeDoEnte, rotuloCargo, rotuloLocal, SEM_REGISTRO } from '../rotulos';

/** Tabela de registros de um bloco. Usada no perfil (mais recentes) e no detalhe anual (todos). */
@Component({
  selector: 'app-tabela',
  templateUrl: './tabela.html',
})
export class Tabela {
  readonly bloco = input.required<string>();
  readonly registros = input.required<Registro[]>();
  readonly legenda = input<string>('');
  /** Quantas linhas aparecem de cada vez. Zero mostra todas. */
  readonly limite = input<number>(0);

  /** Volta ao primeiro lote quando a lista de registros muda (por exemplo, ao filtrar). */
  private readonly mostrando = linkedSignal({
    source: () => ({ limite: this.limite(), registros: this.registros() }),
    computation: ({ limite }) => limite,
  });
  protected readonly visiveis = computed(() => {
    const quantas = this.mostrando();
    return quantas > 0 ? this.registros().slice(0, quantas) : this.registros();
  });
  protected readonly restantes = computed(() => Math.max(0, this.registros().length - this.visiveis().length));

  protected readonly data = data;
  protected readonly inteiro = inteiro;
  protected readonly moeda = moeda;
  protected readonly rotuloCargo = rotuloCargo;
  protected readonly rotuloLocal = rotuloLocal;
  protected readonly nomeDoEnte = nomeDoEnte;
  protected readonly semRegistro = SEM_REGISTRO;

  /** Cargo com o local entre parênteses quando há. Sem UF, nada é inventado. */
  protected cargoELocal(r: Registro): string {
    const cargo = rotuloCargo(this.texto(r['cargo']));
    const local = rotuloLocal(this.textoOuNulo(r['esfera']), this.textoOuNulo(r['ente']), this.textoOuNulo(r['uf']));
    return local ? `${cargo} (${local})` : cargo;
  }

  private textoOuNulo(valor: unknown): string | null {
    return typeof valor === 'string' && valor !== '' ? valor : null;
  }

  protected mostrarMais(todos: boolean): void {
    this.mostrando.update((atual) => (todos ? this.registros().length : atual + this.limite()));
  }

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
