import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Fileiras do plenário, de dentro para fora: raio e assentos. Somam 27, um por unidade da federação. */
const FILEIRAS: ReadonlyArray<readonly [raio: number, assentos: number]> = [
  [7.5, 6],
  [12, 9],
  [16.5, 12],
];

/** Centro do hemiciclo no desenho de 48 por 32. É também o lugar da tribuna. */
const CENTRO = { x: 24, y: 23.8 };

interface Assento {
  x: number;
  y: number;
}

/** Assentos em meia-volta, da esquerda para a direita. O passo entre assentos é o mesmo nas três fileiras. */
function montarAssentos(): Assento[] {
  const assentos: Assento[] = [];
  for (const [raio, quantos] of FILEIRAS) {
    for (let i = 0; i < quantos; i++) {
      const angulo = Math.PI * (1 - i / (quantos - 1));
      assentos.push({
        x: Number((CENTRO.x + raio * Math.cos(angulo)).toFixed(2)),
        y: Number((CENTRO.y - raio * Math.sin(angulo)).toFixed(2)),
      });
    }
  }
  return assentos;
}

const ASSENTOS = montarAssentos();

/**
 * Logotipo (ADR-0060): o plenário entre colchetes.
 * Os pontos são iguais e de uma cor só: nenhum identifica partido. Os colchetes são os da citação de fonte.
 * O favicon é a redução do mesmo desenho: colchetes e o ponto da tribuna.
 * Cada assento é um círculo: com traço pontilhado, Chromium e WebKit perdem o último ponto da fileira.
 * Decorativo ao lado do nome do site.
 */
@Component({
  selector: 'app-marca',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg class="marca-logo" viewBox="0 0 48 32" aria-hidden="true" focusable="false">
      <path d="M7 1.5H1.5v29H7M41 1.5h5.5v29H41" />
      @for (assento of assentos; track $index) {
        <circle [attr.cx]="assento.x" [attr.cy]="assento.y" r="1.7" />
      }
      <circle [attr.cx]="centro.x" [attr.cy]="centro.y" r="2.6" />
    </svg>
  `,
})
export class Marca {
  protected readonly assentos = ASSENTOS;
  protected readonly centro = CENTRO;
}
