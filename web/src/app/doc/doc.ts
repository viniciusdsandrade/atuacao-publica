import {
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { marked } from 'marked';
import { BehaviorSubject, catchError, combineLatest, map, of, startWith, switchMap } from 'rxjs';

import { Dados } from '../dados';
import { Icone } from '../icone/icone';
import { manterVisivel } from '../rolagem';

/** Links entre documentos do repositório viram rotas do site. */
const ROTAS: Record<string, string> = {
  'METODOLOGIA.md': '#/metodologia',
  'FONTES.md': '#/fontes',
  'POLITICA_EDITORIAL.md': '#/politica-editorial',
  'CONTRIBUTING.md': '#/contribuir',
};

type Carga = { estado: 'carregando' } | { estado: 'erro' } | { estado: 'pronto'; html: string };

interface ItemSumario {
  id: string;
  texto: string;
  nivel: 2 | 3;
}

/** Identificador estável de um título: sem acento, minúsculo, separado por hífen. */
function identificador(texto: string): string {
  const base = texto
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return 'doc-' + (base || 'secao');
}

/**
 * Página de documento: renderiza o Markdown do repositório (docs/*.md e CONTRIBUTING.md).
 * O Angular sanitiza o HTML gerado antes de inserir. Depois da inserção, a página dá identificador aos títulos
 * (o sanitizador remove `id`), monta o sumário, abre links externos em outra aba e torna rolável por teclado
 * o que rola de lado (tabelas e blocos de código).
 */
@Component({
  selector: 'app-doc',
  imports: [RouterLink, Icone],
  templateUrl: './doc.html',
})
export class Doc {
  private readonly dados = inject(Dados);
  private readonly injector = inject(Injector);
  private readonly artigo = viewChild<ElementRef<HTMLElement>>('artigo');
  private readonly fragmento = toSignal(inject(ActivatedRoute).fragment, { initialValue: null });
  private observador?: IntersectionObserver;

  private readonly tentar$ = new BehaviorSubject<void>(undefined);

  protected readonly carga = toSignal(
    combineLatest([inject(ActivatedRoute).data, this.tentar$]).pipe(
      switchMap(([d]) =>
        this.dados.documento(d['arquivo'] as string).pipe(
          map((texto): Carga => ({ estado: 'pronto', html: this.renderizar(texto) })),
          startWith<Carga>({ estado: 'carregando' }),
          catchError(() => of<Carga>({ estado: 'erro' })),
        ),
      ),
    ),
    { requireSync: true },
  );

  protected readonly sumario = signal<ItemSumario[]>([]);
  protected readonly ativo = signal<string | null>(null);
  protected readonly sumarioAberto = signal(false);
  protected readonly esqueletos = [1, 2, 3, 4, 5, 6, 7];

  constructor() {
    effect(() => {
      const c = this.carga();
      if (c.estado !== 'pronto') {
        return;
      }
      const fragmento = untracked(() => this.fragmento());
      afterNextRender(
        () => {
          this.preparar();
          // O sumário entra na tela na exibição seguinte; só então a página tem a altura final.
          if (fragmento) {
            afterNextRender(() => document.getElementById(fragmento)?.scrollIntoView(), { injector: this.injector });
          }
        },
        { injector: this.injector },
      );
    });

    effect(() => {
      if (this.ativo()) {
        afterNextRender(
          () => {
            const item = document.querySelector<HTMLElement>('.doc-sumario a[aria-current="location"]');
            const sumario = document.querySelector<HTMLElement>('.doc-sumario');
            if (item && sumario) {
              manterVisivel(item, sumario);
            }
          },
          { injector: this.injector },
        );
      }
    });

    inject(DestroyRef).onDestroy(() => this.observador?.disconnect());
  }

  protected html(): string {
    const c = this.carga();
    return c.estado === 'pronto' ? c.html : '';
  }

  private renderizar(markdown: string): string {
    let html = marked.parse(markdown, { async: false });
    for (const [arquivo, rota] of Object.entries(ROTAS)) {
      html = html.replace(new RegExp(`href="(?:docs/)?${arquivo.replace('.', '\\.')}"`, 'g'), `href="${rota}"`);
    }
    return html;
  }

  private preparar(): void {
    const artigo = this.artigo()?.nativeElement;
    if (!artigo) {
      return;
    }

    const usados = new Map<string, number>();
    const itens: ItemSumario[] = [];
    const titulos = Array.from(artigo.querySelectorAll<HTMLElement>('h2, h3'));
    for (const t of titulos) {
      const texto = (t.textContent ?? '').trim();
      const base = identificador(texto);
      const n = (usados.get(base) ?? 0) + 1;
      usados.set(base, n);
      t.id = n === 1 ? base : `${base}-${n}`;
      t.tabIndex = -1;
      itens.push({ id: t.id, texto, nivel: t.tagName === 'H2' ? 2 : 3 });
    }
    this.sumario.set(itens);

    for (const a of Array.from(artigo.querySelectorAll<HTMLAnchorElement>('a[href^="http"]'))) {
      a.target = '_blank';
      a.rel = 'noopener noreferrer';
    }

    // Região rolável precisa de nome próprio e único: usa o título que vem antes e um número quando se repete.
    const rotulos = new Map<string, number>();
    const rotular = (elemento: Element, tipo: string): string => {
      let anterior: Element | null = elemento;
      while (anterior && !/^H[1-6]$/.test(anterior.tagName)) {
        anterior = anterior.previousElementSibling ?? anterior.parentElement?.previousElementSibling ?? null;
      }
      const base = `${tipo}: ${(anterior?.textContent ?? 'documento').trim()}`;
      const n = (rotulos.get(base) ?? 0) + 1;
      rotulos.set(base, n);
      return n === 1 ? base : `${base} (${n})`;
    };

    for (const pre of Array.from(artigo.querySelectorAll<HTMLElement>('pre'))) {
      pre.tabIndex = 0;
      pre.setAttribute('role', 'region');
      pre.setAttribute('aria-label', rotular(pre, 'Código'));
    }

    for (const tabela of Array.from(artigo.querySelectorAll<HTMLTableElement>('table'))) {
      if (tabela.parentElement?.classList.contains('doc-tabela')) {
        continue;
      }
      const moldura = document.createElement('div');
      moldura.className = 'doc-tabela';
      moldura.tabIndex = 0;
      moldura.setAttribute('role', 'region');
      tabela.replaceWith(moldura);
      moldura.appendChild(tabela);
      moldura.setAttribute('aria-label', rotular(moldura, 'Tabela'));
    }

    this.observar(titulos);
  }

  /** Marca no sumário o primeiro título que cruza a faixa superior da janela. */
  private observar(titulos: HTMLElement[]): void {
    this.observador?.disconnect();
    if (titulos.length === 0 || typeof IntersectionObserver === 'undefined') {
      return;
    }
    const visiveis = new Set<string>();
    this.observador = new IntersectionObserver(
      (entradas) => {
        for (const e of entradas) {
          if (e.isIntersecting) {
            visiveis.add(e.target.id);
          } else {
            visiveis.delete(e.target.id);
          }
        }
        const primeiro = titulos.find((t) => visiveis.has(t.id));
        if (primeiro) {
          this.ativo.set(primeiro.id);
        }
      },
      { rootMargin: '-16px 0px -70% 0px' },
    );
    titulos.forEach((t) => this.observador?.observe(t));
  }

  /** O roteador atualiza o endereço e rola. Aqui só garante a rolagem (mesmo link duas vezes) e leva o foco ao título. */
  protected ir(id: string): void {
    const alvo = document.getElementById(id);
    alvo?.scrollIntoView();
    alvo?.focus({ preventScroll: true });
    this.ativo.set(id);
    this.sumarioAberto.set(false);
  }

  protected tentarDeNovo(): void {
    this.tentar$.next();
  }

  protected voltarAoTopo(): void {
    window.scrollTo({ top: 0 });
    document.getElementById('conteudo')?.focus({ preventScroll: true });
  }
}
