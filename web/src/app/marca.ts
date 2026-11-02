import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Logotipo: o mesmo desenho do favicon (frontão e colunas). Decorativo ao lado do nome do site. */
@Component({
  selector: 'app-marca',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg class="marca-logo" viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <rect width="32" height="32" rx="6" />
      <path d="M8 23h16M10 21V12m4 9V12m4 9V12m4 9V12M7 10l9-5 9 5z" fill="none" stroke-width="2" stroke-linejoin="round" />
    </svg>
  `,
})
export class Marca {}
