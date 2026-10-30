import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';

import { Dados } from '../dados';
import { Detalhe as DetalheAnual } from '../modelo';
import { Tabela } from '../perfil/tabela';
import { data, rotuloBloco } from '../rotulos';

/** Todos os registros de um bloco em um ano (politicos/{id}/{bloco}-{ano}.json). */
@Component({
  selector: 'app-detalhe',
  imports: [RouterLink, Tabela],
  template: `
    @if (parametros(); as par) {
      <p class="voltar"><a [routerLink]="['/politico', par.id]">Voltar ao perfil</a></p>
      <h1>{{ rotuloBloco(par.bloco) }}: {{ par.ano }}</h1>
      @if (detalhe(); as d) {
        @if (d === 'erro') {
          <p>Arquivo não encontrado.</p>
        } @else {
          <p>{{ d.registros.length }} registros. Arquivo gerado em {{ data(d.gerado_em) }}.</p>
          <app-tabela [bloco]="par.bloco" [registros]="d.registros" [legenda]="rotuloBloco(par.bloco) + ' em ' + par.ano" />
        }
      } @else {
        <p aria-live="polite">Carregando...</p>
      }
    }
  `,
})
export class Detalhe {
  private readonly dados = inject(Dados);
  private readonly rota = inject(ActivatedRoute);

  protected readonly parametros = toSignal(
    this.rota.paramMap.pipe(
      map((p) => ({ id: p.get('id') ?? '', bloco: p.get('bloco') ?? '', ano: p.get('ano') ?? '' })),
    ),
  );
  private readonly carga = toSignal(
    this.rota.paramMap.pipe(
      switchMap((p) => {
        const caminho = `politicos/${p.get('id')}/${p.get('bloco')}-${p.get('ano')}.json`;
        return this.dados.detalhe(caminho).pipe(catchError(() => of('erro' as const)));
      }),
    ),
  );
  protected readonly detalhe = computed((): DetalheAnual | 'erro' | undefined => this.carga());

  protected readonly rotuloBloco = rotuloBloco;
  protected readonly data = data;
}
