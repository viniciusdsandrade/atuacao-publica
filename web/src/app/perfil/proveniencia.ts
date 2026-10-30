import { Component, input } from '@angular/core';

import { Bloco } from '../modelo';
import { data } from '../rotulos';

/** Proveniência de um bloco: fontes usadas, data de captura e aviso de fonte desatualizada. */
@Component({
  selector: 'app-proveniencia',
  template: `
    <details class="proveniencia">
      <summary>
        Fonte e atualização
        @if (bloco().atualizado_em) {
          (captura em {{ data(bloco().atualizado_em) }})
        }
        @if (bloco().desatualizado) {
          <span class="aviso">fonte desatualizada: a última coleta falhou ou ficou incompleta; parte dos dados pode vir de coleta anterior</span>
        }
      </summary>
      @if (bloco().fontes.length) {
        <ul>
          @for (f of bloco().fontes; track f.url_origem) {
            <li>
              <code>{{ f.fonte_id }}</code>:
              <a [href]="f.url_origem" rel="noopener noreferrer" target="_blank">{{ f.url_origem }}</a>
              (capturado em {{ data(f.capturado_em) }})
            </li>
          }
        </ul>
      } @else {
        <p>Registros manuais: cada item traz a própria fonte e o snapshot.</p>
      }
    </details>
  `,
})
export class Proveniencia {
  readonly bloco = input.required<Bloco>();
  protected readonly data = data;
}
