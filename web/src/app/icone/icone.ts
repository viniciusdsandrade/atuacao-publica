import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type NomeIcone =
  | 'busca'
  | 'fechar'
  | 'externo'
  | 'info'
  | 'alerta'
  | 'documento'
  | 'seta-direita'
  | 'seta-esquerda'
  | 'filtros'
  | 'verificado'
  | 'link'
  | 'igual'
  | 'lacuna'
  | 'topo'
  | 'comparar'
  | 'menu'
  | 'imprimir';

/** Ícones em SVG embutido, 24 px, traço em currentColor. Decorativos: o texto vizinho diz o que o ícone diz. */
@Component({
  selector: 'app-icone',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg class="icone" [class.icone--pequeno]="pequeno()" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      @switch (nome()) {
        @case ('busca') {
          <circle cx="11" cy="11" r="7" />
          <path d="m20 20-3.5-3.5" />
        }
        @case ('fechar') {
          <path d="M6 6l12 12M18 6 6 18" />
        }
        @case ('externo') {
          <path d="M14 4h6v6" />
          <path d="M20 4 11 13" />
          <path d="M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5" />
        }
        @case ('info') {
          <circle cx="12" cy="12" r="9" />
          <path d="M12 11v5" />
          <path d="M12 8h.01" />
        }
        @case ('alerta') {
          <path d="M12 3.5 21.5 20h-19z" />
          <path d="M12 10v4.5" />
          <path d="M12 17.5h.01" />
        }
        @case ('documento') {
          <path d="M7 3h7l5 5v13H7z" />
          <path d="M14 3v5h5" />
        }
        @case ('seta-direita') {
          <path d="m9 6 6 6-6 6" />
        }
        @case ('seta-esquerda') {
          <path d="M19 12H5" />
          <path d="m11 6-6 6 6 6" />
        }
        @case ('filtros') {
          <path d="M4 7h9" />
          <path d="M19 7h1" />
          <circle cx="16" cy="7" r="2.5" />
          <path d="M4 17h1" />
          <path d="M11 17h9" />
          <circle cx="8" cy="17" r="2.5" />
        }
        @case ('verificado') {
          <circle cx="12" cy="12" r="9" />
          <path d="m8.5 12.5 2.5 2.5 4.5-5" />
        }
        @case ('link') {
          <path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1" />
          <path d="M14 10a4 4 0 0 0-5.7 0l-3 3A4 4 0 0 0 11 18.7l1-1" />
        }
        @case ('igual') {
          <path d="M5 9h14" />
          <path d="M5 15h14" />
        }
        @case ('lacuna') {
          <circle cx="12" cy="12" r="9" stroke-dasharray="3.2 3" />
          <path d="M8.5 12h7" />
        }
        @case ('topo') {
          <path d="M12 19V5" />
          <path d="m6 11 6-6 6 6" />
        }

        @case ('comparar') {
          <rect x="3" y="4" width="7" height="16" rx="1" />
          <rect x="14" y="4" width="7" height="16" rx="1" />
        }
        @case ('menu') {
          <path d="M4 7h16M4 12h16M4 17h16" />
        }
        @case ('imprimir') {
          <path d="M7 9V3h10v6" />
          <rect x="3" y="9" width="18" height="8" rx="1.5" />
          <path d="M7 14h10v7H7z" />
        }
      }
    </svg>
  `,
})
export class Icone {
  readonly nome = input.required<NomeIcone>();
  readonly pequeno = input(false);
}
