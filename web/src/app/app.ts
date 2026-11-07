import { ViewportScroller } from '@angular/common';
import { Component, ElementRef, Injector, afterNextRender, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, filter, map, of, startWith } from 'rxjs';

import { Dados } from './dados';
import { Icone } from './icone/icone';
import { Marca } from './marca';
import { data } from './rotulos';
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

/** Espaço livre no alto da janela, como o CSS declara em `scroll-padding-top` (faixas fixas do perfil e dos documentos). */
function deslocamentoDoTopo(): number {
  const valor = parseFloat(getComputedStyle(document.documentElement).scrollPaddingTop);
  return Number.isFinite(valor) ? valor : 0;
}

/** A barra do navegador acompanha o tema escolhido. No automático, cada meta volta à cor do seu esquema. */
function atualizarCorDoNavegador(tema: Tema): void {
  const cor = getComputedStyle(document.documentElement).getPropertyValue('--superficie').trim();
  for (const meta of Array.from(document.querySelectorAll<HTMLMetaElement>('meta[name="theme-color"]'))) {
    const original = (meta.dataset['original'] ??= meta.content);
    meta.content = tema === 'sistema' || !cor ? original : cor;
  }
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
  protected readonly temas: readonly Tema[] = ['sistema', 'claro', 'escuro'];
  protected readonly rotuloDoTema = rotuloCurtoTema;
  protected readonly menuAberto = signal(false);
  protected readonly data = data;

  /** Última execução do ETL, para a data no rodapé. Sem o relatório, o rodapé fica sem a data. */
  protected readonly execucao = toSignal(inject(Dados).execucao$.pipe(catchError(() => of(null))), { initialValue: null });

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

  /** O item "Políticos" também vale para o perfil, o detalhe anual e o lado a lado. */
  protected naSecaoPoliticos(): boolean {
    const caminho = this.caminho();
    return this.naLista() || caminho.startsWith('/politico') || caminho === '/comparar';
  }

  protected definirTema(tema: Tema): void {
    this.tema.set(tema);
    salvarTema(tema);
    aplicarTemaNoDocumento(tema);
    atualizarCorDoNavegador(tema);
  }

  protected alternarTema(): void {
    this.definirTema(proximoTema(this.tema()));
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

  /** Esc fecha o painel do menu e devolve o foco ao botão que o abriu. */
  protected fecharMenu(botao: HTMLElement): void {
    if (this.menuAberto()) {
      this.menuAberto.set(false);
      botao.focus();
    }
  }

  /** A busca das páginas internas leva à lista, com a busca no endereço (#/?q=...). */
  protected buscar(evento: Event, campo: HTMLInputElement): void {
    evento.preventDefault();
    const busca = campo.value.trim();
    campo.value = '';
    this.router.navigate(['/'], { queryParams: busca ? { q: busca } : {} });
  }

  constructor() {
    aplicarTemaNoDocumento(this.tema());
    atualizarCorDoNavegador(this.tema());

    // O roteador rola até o fragmento com window.scrollTo, que ignora scroll-padding. Sem isso, o bloco
    // ou o título escolhido fica embaixo da faixa fixa do celular.
    inject(ViewportScroller).setOffset(() => [0, deslocamentoDoTopo()]);

    // Troca de página move o foco ao conteúdo, como um carregamento novo faria.
    // Mudar só filtro, fragmento ou carga inicial não mexe no foco.
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((e) => {
        this.menuAberto.set(false);
        const caminho = caminhoDe(e.urlAfterRedirects);
        const trocou = this.caminhoAnterior !== null && caminho !== this.caminhoAnterior;
        this.caminhoAnterior = caminho;
        if (trocou) {
          afterNextRender(() => this.conteudo().nativeElement.focus({ preventScroll: true }), { injector: this.injector });
        }
      });
  }
}
