import { Component, ElementRef, Injector, afterNextRender, inject, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';

import { Marca } from './marca';

/** Caminho da rota sem parâmetros de consulta nem fragmento. */
function caminhoDe(url: string): string {
  return url.split(/[?#]/)[0] || '/';
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Marca],
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly conteudo = viewChild.required<ElementRef<HTMLElement>>('conteudo');
  private caminhoAnterior: string | null = null;

  private readonly caminho = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => caminhoDe(e.urlAfterRedirects)),
      startWith('/'),
    ),
    { requireSync: true },
  );

  protected naLista(): boolean {
    return this.caminho() === '/';
  }

  /** O item "Políticos" também vale para o perfil e o detalhe anual. */
  protected naSecaoPoliticos(): boolean {
    return this.naLista() || this.caminho().startsWith('/politico');
  }

  constructor() {
    // Troca de página move o foco ao conteúdo, como um carregamento novo faria.
    // Mudar só filtro, fragmento ou carga inicial não mexe no foco.
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((e) => {
        const caminho = caminhoDe(e.urlAfterRedirects);
        const trocou = this.caminhoAnterior !== null && caminho !== this.caminhoAnterior;
        this.caminhoAnterior = caminho;
        if (trocou) {
          afterNextRender(() => this.conteudo().nativeElement.focus({ preventScroll: true }), { injector: this.injector });
        }
      });
  }
}
