import { Component, input } from '@angular/core';

import { Icone } from '../icone/icone';
import { Bloco } from '../modelo';
import { data, inteiro } from '../rotulos';

/** Proveniência de um bloco: fontes usadas, data de captura e aviso de fonte desatualizada. */
@Component({
  selector: 'app-proveniencia',
  imports: [Icone],
  template: `
    @if (bloco().desatualizado) {
      <p class="callout callout--aviso" role="note">
        <app-icone nome="alerta" />
        <span>
          <strong>Fonte desatualizada:</strong> a última coleta falhou ou ficou incompleta; parte dos dados pode vir de coleta
          anterior.
        </span>
      </p>
    }
    <details class="proveniencia sanfona">
      <summary>
        <app-icone nome="documento" [pequeno]="true" />
        <span>
          Fonte e atualização
          @if (bloco().atualizado_em) {
            <span class="proveniencia-meta">(captura em {{ data(bloco().atualizado_em) }})</span>
          }
        </span>
      </summary>
      @if (bloco().fontes.length) {
        <p class="proveniencia-total">
          {{ inteiro(bloco().fontes.length) }} {{ bloco().fontes.length === 1 ? 'fonte' : 'fontes' }} neste bloco.
        </p>
        <ul class="proveniencia-lista">
          @for (f of bloco().fontes; track f.url_origem) {
            <li>
              <code>{{ f.fonte_id }}</code>:
              <a [href]="f.url_origem" rel="noopener noreferrer" target="_blank">{{ f.url_origem }}</a>
              (capturado em {{ data(f.capturado_em) }})
            </li>
          }
        </ul>
      } @else {
        <p class="proveniencia-total">Registros manuais: cada item traz a própria fonte e o snapshot.</p>
      }
    </details>
  `,
})
export class Proveniencia {
  readonly bloco = input.required<Bloco>();
  protected readonly data = data;
  protected readonly inteiro = inteiro;
}
