import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { Dados } from '../dados';
import { EntradaIndice } from '../modelo';
import { chaveDeBusca, data, rotuloCargo } from '../rotulos';

type Situacao = 'exercicio' | 'posse' | 'fora';

/** Em exercício, aguardando posse ou fora de exercício (ADR-0006). */
function situacaoDe(p: EntradaIndice): Situacao {
  if (p.em_exercicio) {
    return 'exercicio';
  }
  return p.situacao?.includes('posse prevista') ? 'posse' : 'fora';
}

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
  /** Começa em quem está em exercício. Quem saiu e quem aguarda posse ficam no arquivo, por este filtro. */
  protected readonly situacao = signal<Situacao | ''>('exercicio');
  protected readonly cargo = signal('');
  protected readonly uf = signal('');
  protected readonly partido = signal('');
  protected readonly situacaoDe = situacaoDe;

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
        (!this.situacao() || situacaoDe(p) === this.situacao()) &&
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

  protected escolherSituacao(evento: Event): void {
    this.situacao.set(this.valor(evento) as Situacao | '');
  }

  protected limpar(): void {
    this.busca.set('');
    this.situacao.set('exercicio');
    this.cargo.set('');
    this.uf.set('');
    this.partido.set('');
  }
}
