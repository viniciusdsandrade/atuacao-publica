import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { Dados } from '../dados';
import { Icone } from '../icone/icone';
import { Execucao } from '../modelo';
import { data, inteiro } from '../rotulos';

/** O que cada passo de coleta traz. Os identificadores são os da CLI do ETL (`fetch {fonte}`); os nomes seguem docs/FONTES.md. */
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
  alesp: 'Assembleia Legislativa do Estado de São Paulo (ALESP)',
  executivosp: 'Governo do Estado de São Paulo (agenda, despesas e atos)',
  almg: 'Assembleia Legislativa do Estado de Minas Gerais (ALMG)',
  alep: 'Assembleia Legislativa do Estado do Paraná (ALEP)',
  alba: 'Assembleia Legislativa do Estado da Bahia (ALBA)',
  alerj: 'Assembleia Legislativa do Estado do Rio de Janeiro (ALERJ)',
  alrs: 'Assembleia Legislativa do Estado do Rio Grande do Sul (ALRS)',
  alece: 'Assembleia Legislativa do Estado do Ceará (ALECE)',
  alesc: 'Assembleia Legislativa do Estado de Santa Catarina (ALESC)',
  alego: 'Assembleia Legislativa do Estado de Goiás (ALEGO)',
  ales: 'Assembleia Legislativa do Estado do Espírito Santo (ALES)',
  alepa: 'Assembleia Legislativa do Estado do Pará (ALEPA)',
  alepe: 'Assembleia Legislativa do Estado de Pernambuco (ALEPE)',
  cldf: 'Câmara Legislativa do Distrito Federal (CLDF)',
  almt: 'Assembleia Legislativa do Estado de Mato Grosso (ALMT)',
  alems: 'Assembleia Legislativa do Estado de Mato Grosso do Sul (ALEMS)',
  alpb: 'Assembleia Legislativa do Estado da Paraíba (ALPB)',
  aleam: 'Assembleia Legislativa do Estado do Amazonas (ALEAM)',
  aleal: 'Assembleia Legislativa do Estado de Alagoas (ALEAL)',
  alero: 'Assembleia Legislativa do Estado de Rondônia (ALERO)',
  alepi: 'Assembleia Legislativa do Estado do Piauí (ALEPI)',
  alrn: 'Assembleia Legislativa do Estado do Rio Grande do Norte (ALRN)',
  aleto: 'Assembleia Legislativa do Estado do Tocantins (ALETO)',
  alese: 'Assembleia Legislativa do Estado de Sergipe (ALESE)',
  cmsp: 'Câmara Municipal de São Paulo (CMSP)',
};

/** "Das 24 coletas, 17 concluídas, 2 incompletas e 5 com falha." Só entram os estados que aparecem. */
function resumoDasColetas(fontes: Execucao['fontes']): string {
  const conta = (status: string) => fontes.filter((f) => f.status === status).length;
  const partes: string[] = [];
  const ok = conta('ok');
  const parcial = conta('parcial');
  const falha = conta('falha');
  const semColeta = conta('nao_executada');
  if (ok) {
    partes.push(`${ok} ${ok === 1 ? 'concluída' : 'concluídas'}`);
  }
  if (parcial) {
    partes.push(`${parcial} ${parcial === 1 ? 'incompleta' : 'incompletas'}`);
  }
  if (falha) {
    partes.push(`${falha} com falha`);
  }
  if (semColeta) {
    partes.push(`${semColeta} sem coleta`);
  }
  const lista = partes.length > 1 ? `${partes.slice(0, -1).join(', ')} e ${partes[partes.length - 1]}` : (partes[0] ?? '');
  return `Das ${fontes.length} coletas, ${lista}.`;
}

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
      Cada fonte é coletada numa etapa própria. Se uma coleta falha ou fica incompleta, o site mantém os dados da última
      coleta válida e avisa nos blocos que dependem dela.
    </p>

    @if (resultado(); as r) {
      @if (r.execucao; as e) {
        <p class="meta-pagina">
          Última execução em {{ data(e.terminado_em) }}. {{ inteiro(e.publicados) }} perfis publicados.
          {{ resumo(e.fontes) }}
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
                  <span class="selo selo--concluida"><app-icone nome="verificado" [pequeno]="true" /> Coleta concluída</span>
                }
                @case ('parcial') {
                  <span class="selo selo--aviso selo--incompleta">Coleta incompleta</span>
                }
                @case ('falha') {
                  <span class="selo selo--aviso selo--falha"><app-icone nome="alerta" [pequeno]="true" /> Coleta com falha</span>
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
  protected readonly resumo = resumoDasColetas;

  protected nome(fonte: string): string {
    return NOMES[fonte] ?? fonte;
  }
}
