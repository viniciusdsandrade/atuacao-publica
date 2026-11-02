import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { Dados } from '../dados';
import { Icone } from '../icone/icone';
import { data, inteiro } from '../rotulos';

/** O que cada passo de coleta traz. Os identificadores são os da CLI do ETL (`fetch {fonte}`). */
const NOMES: Record<string, string> = {
  camara: 'Câmara dos Deputados',
  senado: 'Senado Federal',
  tse: 'Tribunal Superior Eleitoral (TSE)',
  executivo: 'Atos do Executivo federal (API do Senado)',
  sapl: 'Câmara municipal do piloto (SAPL)',
  tcesp: 'Tribunal de Contas do Estado de São Paulo (TCE-SP)',
  cgu: 'Portal da Transparência (cartão e viagens da Presidência)',
  agenda: 'Agenda do Presidente da República (Planalto)',
  contrib: 'Registros manuais revisados (contribuições)',
};

/** Estado da última coleta de cada fonte, como o ETL registrou em `_meta/execucao.json`. */
@Component({
  selector: 'app-estado',
  imports: [RouterLink, Icone],
  template: `
    <nav class="migalhas" aria-label="Você está em">
      <ol>
        <li><a routerLink="/">Políticos</a></li>
        <li aria-current="page">Estado das coletas</li>
      </ol>
    </nav>
    <h1 class="titulo-pagina">Estado das coletas</h1>
    <p class="lead">
      Cada fonte é coletada em um passo próprio do pipeline de dados. Se uma fonte falha ou fica incompleta, o site mantém
      os dados da última coleta válida e marca os blocos dela como desatualizados.
    </p>

    @if (resultado(); as r) {
      @if (r.execucao; as e) {
        <p class="meta-pagina">
          Última execução em {{ data(e.terminado_em) }}. {{ inteiro(e.publicados) }} perfis publicados.
        </p>
        <ul class="estado-lista">
          @for (f of e.fontes; track f.fonte) {
            <li class="painel estado-item">
              <div>
                <h2>{{ nome(f.fonte) }}</h2>
                <p class="estado-id"><code>{{ f.fonte }}</code></p>
              </div>
              @switch (f.status) {
                @case ('ok') {
                  <span class="selo selo--ativo">Coleta concluída</span>
                }
                @case ('parcial') {
                  <span class="selo selo--aviso">Coleta incompleta</span>
                }
                @case ('falha') {
                  <span class="selo selo--aviso">Fonte desatualizada</span>
                }
                @case ('nao_executada') {
                  <span class="selo selo--fora">Sem coleta</span>
                }
                @default {
                  <span class="selo">{{ f.status }}</span>
                }
              }
              <p>
                @switch (f.status) {
                  @case ('ok') {
                    Última coleta em {{ data(f.ultima_coleta_ok) }}.
                  }
                  @case ('parcial') {
                    Coleta incompleta em {{ data(f.ultima_coleta_ok) }}: parte dos dados vem de coleta anterior.
                  }
                  @case ('falha') {
                    A última coleta falhou. Última coleta válida em {{ data(f.ultima_coleta_ok) }}.
                  }
                  @case ('nao_executada') {
                    Esta fonte ainda não foi coletada.
                  }
                  @default {
                    Última coleta válida em {{ data(f.ultima_coleta_ok) }}.
                  }
                }
                @if (f.mensagem) {
                  <span class="estado-mensagem">{{ f.mensagem }}</span>
                }
              </p>
            </li>
          }
        </ul>
        <p class="nota-pagina">
          <app-icone nome="info" [pequeno]="true" />
          <span>
            Limites conhecidos de cada fonte estão em <a routerLink="/fontes">Fontes</a> e em
            <a routerLink="/metodologia">Metodologia</a>.
          </span>
        </p>
      } @else {
        <div class="caixa-vazia" role="alert">
          <app-icone nome="alerta" />
          <h2>Não foi possível carregar o relatório de coletas</h2>
          <p>O arquivo do relatório não respondeu. Tente de novo mais tarde.</p>
        </div>
      }
    } @else {
      <p class="sr-only" role="status">Carregando o relatório de coletas.</p>
      <ul class="estado-lista" aria-hidden="true">
        @for (n of esqueletos; track n) {
          <li class="painel estado-item"><span class="esqueleto esqueleto--texto"></span></li>
        }
      </ul>
    }
  `,
})
export class Estado {
  private readonly dados = inject(Dados);

  protected readonly resultado = toSignal(
    this.dados.execucao$.pipe(
      map((execucao) => ({ execucao })),
      catchError(() => of({ execucao: null })),
    ),
  );

  protected readonly esqueletos = [1, 2, 3, 4, 5];
  protected readonly data = data;
  protected readonly inteiro = inteiro;

  protected nome(fonte: string): string {
    return NOMES[fonte] ?? fonte;
  }
}
