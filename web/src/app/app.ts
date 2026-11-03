import { Component, ElementRef, Injector, afterNextRender, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';

import { Icone } from './icone/icone';
import { Marca } from './marca';
import {
  Tema,
  aplicarTemaNoDocumento,
  iconeTema,
  lerTemaSalvo,
  proximoTema,
  rotuloCurtoTema,
  rotuloTema,
  salvarTema,
} from './tema';

/** Caminho da rota sem parâmetros de consulta nem fragmento. */
function caminhoDe(url: string): string {
  return url.split(/[?#]/)[0] || '/';
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Marca, Icone],
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly conteudo = viewChild.required<ElementRef<HTMLElement>>('conteudo');
  private caminhoAnterior: string | null = null;

  protected readonly tema = signal<Tema>(lerTemaSalvo());

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

  protected alternarTema(): void {
    const proximo = proximoTema(this.tema());
    this.tema.set(proximo);
    salvarTema(proximo);
    aplicarTemaNoDocumento(proximo);
  }

  protected iconeTema(): 'sistema' | 'sol' | 'lua' {
    return iconeTema(this.tema());
  }

  protected rotuloCurtoTema(): string {
    return rotuloCurtoTema(this.tema());
  }

  protected rotuloTema(): string {
    return rotuloTema(this.tema());
  }

  constructor() {
    aplicarTemaNoDocumento(this.tema());

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
