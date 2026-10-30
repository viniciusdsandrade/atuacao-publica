import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { marked } from 'marked';
import { catchError, map, of, switchMap } from 'rxjs';

import { Dados } from '../dados';

/** Links entre documentos do repositório viram rotas do site. */
const ROTAS: Record<string, string> = {
  'METODOLOGIA.md': '#/metodologia',
  'FONTES.md': '#/fontes',
  'POLITICA_EDITORIAL.md': '#/politica-editorial',
  'CONTRIBUTING.md': '#/contribuir',
};

/**
 * Página de documento: renderiza o Markdown do repositório (docs/*.md e CONTRIBUTING.md).
 * O Angular sanitiza o HTML gerado antes de inserir.
 */
@Component({
  selector: 'app-doc',
  template: `
    @if (html(); as conteudo) {
      <article class="documento" [innerHTML]="conteudo"></article>
    } @else {
      <p aria-live="polite">Carregando...</p>
    }
  `,
})
export class Doc {
  private readonly dados = inject(Dados);

  protected readonly html = toSignal(
    inject(ActivatedRoute).data.pipe(
      switchMap((d) =>
        this.dados.documento(d['arquivo'] as string).pipe(
          map((texto) => this.renderizar(texto)),
          catchError(() => of('<p>Documento indisponível.</p>')),
        ),
      ),
    ),
  );

  private renderizar(markdown: string): string {
    let html = marked.parse(markdown, { async: false });
    for (const [arquivo, rota] of Object.entries(ROTAS)) {
      html = html.replace(new RegExp(`href="(?:docs/)?${arquivo.replace('.', '\\.')}"`, 'g'), `href="${rota}"`);
    }
    return html;
  }
}
