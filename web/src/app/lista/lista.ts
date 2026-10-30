import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { Dados } from '../dados';
import { EntradaIndice } from '../modelo';
import { chaveDeBusca, data, rotuloCargo } from '../rotulos';

@Component({
  selector: 'app-lista',
  imports: [RouterLink],
  templateUrl: './lista.html',
})
export class Lista {
  private readonly dados = inject(Dados);
  protected readonly indice = toSignal(this.dados.indice$);
  protected readonly execucao = toSignal(this.dados.execucao$);

  protected readonly busca = signal('');
  protected readonly cargo = signal('');
  protected readonly uf = signal('');
  protected readonly partido = signal('');

  protected readonly rotuloCargo = rotuloCargo;
  protected readonly data = data;

  protected readonly cargos = computed(() => this.opcoes((p) => p.cargo));
  protected readonly ufs = computed(() => this.opcoes((p) => p.uf));
  protected readonly partidos = computed(() => this.opcoes((p) => p.partido));

  protected readonly resultado = computed(() => {
    const termo = chaveDeBusca(this.busca());
    return (this.indice()?.politicos ?? []).filter(
      (p) =>
        (!termo || chaveDeBusca(p.nome).includes(termo)) &&
        (!this.cargo() || p.cargo === this.cargo()) &&
        (!this.uf() || p.uf === this.uf()) &&
        (!this.partido() || p.partido === this.partido()),
    );
  });

  private opcoes(campo: (p: EntradaIndice) => string | null): string[] {
    const valores = new Set<string>();
    for (const p of this.indice()?.politicos ?? []) {
      const v = campo(p);
      if (v) {
        valores.add(v);
      }
    }
    return [...valores].sort((a, b) => a.localeCompare(b, 'pt-BR'));
  }

  protected valor(evento: Event): string {
    return (evento.target as HTMLInputElement | HTMLSelectElement).value;
  }

  protected limpar(): void {
    this.busca.set('');
    this.cargo.set('');
    this.uf.set('');
    this.partido.set('');
  }
}
