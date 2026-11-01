import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, combineLatest, map, of, switchMap } from 'rxjs';

import { Dados } from '../dados';
import { Bloco, DespesaAgregada, Perfil as PerfilPublicado } from '../modelo';
import { data, moeda, NOTAS, rotuloBloco, rotuloCargo, rotuloLocal, SEM_REGISTRO } from '../rotulos';
import { Proveniencia } from './proveniencia';
import { Tabela } from './tabela';

type Estado = { perfil: PerfilPublicado | null; erro: boolean };

@Component({
  selector: 'app-perfil',
  imports: [RouterLink, Proveniencia, Tabela],
  templateUrl: './perfil.html',
})
export class Perfil {
  private readonly dados = inject(Dados);
  private readonly router = inject(Router);

  /** Identificador antigo (fusão de perfis) redireciona para o vigente. */
  private readonly estado = toSignal(
    combineLatest([inject(ActivatedRoute).paramMap, this.dados.indice$]).pipe(
      switchMap(([parametros, indice]) => {
        const id = parametros.get('id') ?? '';
        const vigente = indice.aliases[id];
        if (vigente) {
          this.router.navigate(['/politico', vigente], { replaceUrl: true });
        }
        return this.dados.perfil(vigente ?? id).pipe(
          map((perfil): Estado => ({ perfil, erro: false })),
          catchError(() => of<Estado>({ perfil: null, erro: true })),
        );
      }),
    ),
  );

  protected readonly perfil = computed(() => this.estado()?.perfil ?? null);
  protected readonly erro = computed(() => this.estado()?.erro ?? false);
  protected readonly blocos = computed(() => Object.keys(this.perfil()?.blocos ?? {}));

  protected readonly rotuloCargo = rotuloCargo;
  protected readonly rotuloBloco = rotuloBloco;
  protected readonly rotuloLocal = rotuloLocal;
  protected readonly data = data;
  protected readonly moeda = moeda;
  protected readonly semRegistro = SEM_REGISTRO;

  /** As rotas usam hash; o sumário rola até o bloco e move o foco para ele. */
  protected ir(bloco: string): void {
    const alvo = document.getElementById(bloco);
    alvo?.scrollIntoView();
    alvo?.focus({ preventScroll: true });
  }

  protected nota(bloco: string): string | null {
    return NOTAS[bloco] ?? null;
  }

  protected porTipo(bloco: Bloco): [string, number][] {
    return Object.entries(bloco.por_tipo ?? {}).sort((a, b) => b[1] - a[1]);
  }

  /** Total por ano a partir das somas mensais publicadas. */
  protected unidade(bloco: string): string {
    return bloco === 'despesas_executivo' ? 'pagamentos' : 'lançamentos';
  }

  protected despesasPorAno(bloco: Bloco): { ano: number; valor: number; quantidade: number }[] {
    const anos = new Map<number, { ano: number; valor: number; quantidade: number }>();
    for (const m of bloco.por_mes ?? []) {
      const a = anos.get(m.ano) ?? { ano: m.ano, valor: 0, quantidade: 0 };
      a.valor += m.valor;
      a.quantidade += m.quantidade;
      anos.set(m.ano, a);
    }
    return [...anos.values()].sort((a, b) => b.ano - a.ano);
  }

  protected categorias(bloco: Bloco, ano: number): DespesaAgregada[] {
    return (bloco.por_categoria ?? []).filter((c) => c.ano === ano).sort((a, b) => b.valor - a.valor);
  }
}
