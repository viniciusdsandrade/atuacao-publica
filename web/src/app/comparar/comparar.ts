import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, combineLatest, map, of, switchMap } from 'rxjs';

import { Dados } from '../dados';
import { Icone } from '../icone/icone';
import { EntradaIndice, Perfil } from '../modelo';
import {
  chaveDeBusca,
  data,
  ePapelLideranca,
  iniciais,
  inteiro,
  moeda,
  rotuloCargo,
  rotuloLocal,
  rotuloTipoOrgao,
  SEM_REGISTRO,
  SITUACOES,
  situacaoDe,
} from '../rotulos';

interface EstadoComparacao {
  p1: Perfil | null;
  p2: Perfil | null;
  erro1: boolean;
  erro2: boolean;
}

@Component({
  selector: 'app-comparar',
  imports: [RouterLink, Icone],
  templateUrl: './comparar.html',
})
export class Comparar {
  private readonly dados = inject(Dados);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly titulo = inject(Title);

  protected readonly busca1 = signal('');
  protected readonly busca2 = signal('');

  private readonly indice = toSignal(this.dados.indice$.pipe(catchError(() => of(null))));

  private readonly parametros = toSignal(this.route.queryParamMap);
  protected readonly id1 = computed(() => this.parametros()?.get('p1') ?? '');
  protected readonly id2 = computed(() => this.parametros()?.get('p2') ?? '');

  protected readonly estado = toSignal(
    combineLatest([this.route.queryParamMap, this.dados.indice$.pipe(catchError(() => of(null)))]).pipe(
      switchMap(([params, ind]) => {
        const rawId1 = params.get('p1') ?? '';
        const rawId2 = params.get('p2') ?? '';
        const id1 = ind?.aliases[rawId1] ?? rawId1;
        const id2 = ind?.aliases[rawId2] ?? rawId2;

        const obs1$ = id1
          ? this.dados.perfil(id1).pipe(
              map((p) => ({ p, erro: false })),
              catchError(() => of({ p: null, erro: true })),
            )
          : of({ p: null, erro: false });

        const obs2$ = id2
          ? this.dados.perfil(id2).pipe(
              map((p) => ({ p, erro: false })),
              catchError(() => of({ p: null, erro: true })),
            )
          : of({ p: null, erro: false });

        return combineLatest([obs1$, obs2$]).pipe(
          map(([r1, r2]): EstadoComparacao => ({
            p1: r1.p,
            p2: r2.p,
            erro1: r1.erro,
            erro2: r2.erro,
          })),
        );
      }),
    ),
  );

  protected readonly p1 = computed(() => this.estado()?.p1 ?? null);
  protected readonly p2 = computed(() => this.estado()?.p2 ?? null);
  protected readonly erro1 = computed(() => this.estado()?.erro1 ?? false);
  protected readonly erro2 = computed(() => this.estado()?.erro2 ?? false);

  protected readonly resultadosBusca1 = computed(() => {
    const q = chaveDeBusca(this.busca1());
    const ind = this.indice();
    if (!q || !ind) {
      return [];
    }
    return ind.politicos
      .filter((p) => p.id !== this.id2() && (chaveDeBusca(p.nome).includes(q) || chaveDeBusca(p.partido ?? '').includes(q)))
      .slice(0, 6);
  });

  protected readonly resultadosBusca2 = computed(() => {
    const q = chaveDeBusca(this.busca2());
    const ind = this.indice();
    if (!ind) {
      return [];
    }
    if (q) {
      return ind.politicos
        .filter((p) => p.id !== this.id1() && (chaveDeBusca(p.nome).includes(q) || chaveDeBusca(p.partido ?? '').includes(q)))
        .slice(0, 6);
    }
    // Sugestões automáticas quando p1 está preenchido: mesma UF e cargo
    const perfil1 = this.p1();
    if (perfil1) {
      return ind.politicos
        .filter((p) => p.id !== perfil1.id && p.cargo === perfil1.cargo_atual && p.uf === perfil1.uf)
        .slice(0, 5);
    }
    return [];
  });

  protected readonly SITUACOES = SITUACOES;
  protected readonly situacaoDe = situacaoDe;
  protected readonly iniciais = iniciais;
  protected readonly inteiro = inteiro;
  protected readonly moeda = moeda;
  protected readonly rotuloCargo = rotuloCargo;
  protected readonly rotuloLocal = rotuloLocal;
  protected readonly rotuloTipoOrgao = rotuloTipoOrgao;
  protected readonly data = data;
  protected readonly semRegistro = SEM_REGISTRO;

  constructor() {
    this.titulo.setTitle('Comparação Parlamentar · Atuação Pública');
  }

  protected selecionar(lado: 1 | 2, item: EntradaIndice): void {
    const p1 = lado === 1 ? item.id : this.id1();
    const p2 = lado === 2 ? item.id : this.id2();
    if (lado === 1) {
      this.busca1.set('');
    } else {
      this.busca2.set('');
    }
    this.router.navigate([], {
      queryParams: {
        p1: p1 || null,
        p2: p2 || null,
      },
      queryParamsHandling: 'merge',
    });
  }

  protected remover(lado: 1 | 2): void {
    const p1 = lado === 1 ? null : this.id1();
    const p2 = lado === 2 ? null : this.id2();
    this.router.navigate([], {
      queryParams: {
        p1: p1 || null,
        p2: p2 || null,
      },
    });
  }

  protected inverter(): void {
    this.router.navigate([], {
      queryParams: {
        p1: this.id2() || null,
        p2: this.id1() || null,
      },
    });
  }

  protected totalVotacoes(p: Perfil | null): number {
    return p?.blocos?.['participacao_votacoes']?.total ?? p?.blocos?.['votos']?.total ?? 0;
  }

  protected votosPorTipo(p: Perfil | null): [string, number][] {
    const bloco = p?.blocos?.['participacao_votacoes'] ?? p?.blocos?.['votos'];
    return Object.entries(bloco?.por_tipo ?? {}).sort((a, b) => b[1] - a[1]);
  }

  protected totalProposicoes(p: Perfil | null): number {
    return p?.blocos?.['proposicoes']?.total ?? 0;
  }

  protected proposicoesPorTipo(p: Perfil | null): [string, number][] {
    return Object.entries(p?.blocos?.['proposicoes']?.por_tipo ?? {}).sort((a, b) => b[1] - a[1]);
  }

  protected totalColegiados(p: Perfil | null): number {
    return p?.blocos?.['orgaos_e_frentes']?.total ?? 0;
  }

  protected liderancas(p: Perfil | null): Array<{ papel: string; nome: string; sigla: string | null }> {
    if (!p) {
      return [];
    }
    const bloco = p.blocos?.['orgaos_e_frentes'];
    const regs = (bloco?.registros ?? bloco?.recentes ?? []) as Array<Record<string, unknown>>;
    const hoje = new Date().toISOString().substring(0, 10);
    return regs
      .filter((r) => {
        const fim = typeof r['fim'] === 'string' ? r['fim'] : null;
        const papel = typeof r['papel'] === 'string' ? r['papel'] : null;
        return (!fim || fim >= hoje) && ePapelLideranca(papel);
      })
      .map((r) => ({
        papel: String(r['papel']),
        nome: String(r['nome']),
        sigla: typeof r['sigla'] === 'string' ? r['sigla'] : null,
      }));
  }

  protected totalDespesas(p: Perfil | null): number {
    return p?.blocos?.['despesas']?.valor_total ?? 0;
  }

  protected qtdDespesas(p: Perfil | null): number {
    return p?.blocos?.['despesas']?.total ?? 0;
  }

  protected totalEventos(p: Perfil | null): number {
    return p?.blocos?.['eventos']?.total ?? 0;
  }

  protected totalDiscursos(p: Perfil | null): number {
    return p?.blocos?.['discursos']?.total ?? 0;
  }
}
