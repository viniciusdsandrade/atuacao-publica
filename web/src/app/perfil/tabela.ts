import { Component, computed, input, linkedSignal, signal } from '@angular/core';

import { Registro } from '../modelo';
import {
  data,
  ENTES_MUNICIPAIS,
  ePapelLideranca,
  inteiro,
  moeda,
  nomeDoEnte,
  PRESENCAS,
  rotuloCargo,
  rotuloLocal,
  rotuloTipoOrgao,
  SEM_REGISTRO,
} from '../rotulos';

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

  /** Filtro por tipo ou liderança no bloco de órgãos, comissões e frentes. */
  protected readonly filtroOrgao = signal<string>('todos');

  protected readonly contagensOrgao = computed(() => {
    if (this.bloco() !== 'orgaos_e_frentes') {
      return { todos: 0, comissao: 0, frente: 0, orgao: 0, lideranca: 0 };
    }
    const regs = this.registros();
    let comissao = 0;
    let frente = 0;
    let orgao = 0;
    let lideranca = 0;
    for (const r of regs) {
      const tipo = r['tipo'];
      if (tipo === 'comissao') comissao++;
      else if (tipo === 'frente') frente++;
      else if (tipo === 'orgao') orgao++;
      if (ePapelLideranca(this.textoOuNulo(r['papel']))) lideranca++;
    }
    return {
      todos: regs.length,
      comissao,
      frente,
      orgao,
      lideranca,
    };
  });

  protected readonly registrosFiltrados = computed(() => {
    if (this.bloco() !== 'orgaos_e_frentes') {
      return this.registros();
    }
    const f = this.filtroOrgao();
    if (f === 'todos') {
      return this.registros();
    }
    if (f === 'lideranca') {
      return this.registros().filter((r) => ePapelLideranca(this.textoOuNulo(r['papel'])));
    }
    return this.registros().filter((r) => r['tipo'] === f);
  });

  /** Volta ao primeiro lote quando a lista de registros muda (por exemplo, ao filtrar). */
  private readonly mostrando = linkedSignal({
    source: () => ({ limite: this.limite(), registros: this.registrosFiltrados() }),
    computation: ({ limite }) => limite,
  });
  protected readonly visiveis = computed(() => {
    const quantas = this.mostrando();
    const lista = this.registrosFiltrados();
    return quantas > 0 ? lista.slice(0, quantas) : lista;
  });
  protected readonly restantes = computed(() => Math.max(0, this.registrosFiltrados().length - this.visiveis().length));

  protected readonly data = data;
  protected readonly inteiro = inteiro;
  protected readonly moeda = moeda;
  protected readonly rotuloCargo = rotuloCargo;
  protected readonly rotuloLocal = rotuloLocal;
  protected readonly nomeDoEnte = nomeDoEnte;
  protected readonly rotuloTipoOrgao = rotuloTipoOrgao;
  protected readonly ePapelLideranca = ePapelLideranca;
  protected readonly semRegistro = SEM_REGISTRO;

  protected definirFiltroOrgao(filtro: string): void {
    this.filtroOrgao.set(filtro);
  }

  /** Cargo com o local entre parênteses quando há. Sem UF, nada é inventado. */
  protected cargoELocal(r: Registro): string {
    const cargo = rotuloCargo(this.texto(r['cargo']));
    const local = rotuloLocal(this.textoOuNulo(r['esfera']), this.textoOuNulo(r['ente']), this.textoOuNulo(r['uf']));
    return local ? `${cargo} (${local})` : cargo;
  }

  protected textoOuNulo(valor: unknown): string | null {
    return typeof valor === 'string' && valor !== '' ? valor : null;
  }

  protected mostrarMais(todos: boolean): void {
    this.mostrando.update((atual) => (todos ? this.registrosFiltrados().length : atual + this.limite()));
  }

  protected localCandidatura(r: Registro): string {
    const ente = typeof r['ente'] === 'string' ? r['ente'] : '';
    if (ente in ENTES_MUNICIPAIS) {
      // Os municípios catalogados ficam todos em SP. Um município de outra UF pede a UF no catálogo.
      return `${ENTES_MUNICIPAIS[ente]} (SP)`;
    }
    if (ente === 'BR') {
      return 'Brasil';
    }
    return ente || SEM_REGISTRO;
  }

  protected texto(valor: unknown): string {
    return valor === null || valor === undefined || valor === '' ? SEM_REGISTRO : String(valor);
  }

  /** Valor que a fonte não traz. A célula mostra a frase de ausência em tom mais baixo, para o dado presente aparecer primeiro. */
  protected ausente(valor: unknown): boolean {
    return valor === null || valor === undefined || valor === '' || (Array.isArray(valor) && valor.length === 0);
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

  protected presenca(valor: unknown): string {
    return typeof valor === 'string' && valor in PRESENCAS ? PRESENCAS[valor] : SEM_REGISTRO;
  }

  protected lista(valor: unknown): string {
    return Array.isArray(valor) && valor.length ? valor.join('; ') : SEM_REGISTRO;
  }

  protected url(valor: unknown): string | null {
    return typeof valor === 'string' && /^https?:\/\//.test(valor) ? valor : null;
  }
}
