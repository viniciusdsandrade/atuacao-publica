import { Component, DestroyRef, Injector, afterNextRender, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, combineLatest, map, of, switchMap } from 'rxjs';

import { Dados } from '../dados';
import { Icone } from '../icone/icone';
import { MemoriaLista } from '../lista/memoria-lista';
import { Bloco, DespesaAgregada, Perfil as PerfilPublicado } from '../modelo';
import {
  data,
  iniciais,
  inteiro,
  moeda,
  NOTAS,
  rotuloBloco,
  rotuloCargo,
  rotuloCurtoBloco,
  rotuloLocal,
  SEM_REGISTRO,
  SITUACOES,
  situacaoDe,
} from '../rotulos';
import { Proveniencia } from './proveniencia';
import { Tabela } from './tabela';

type Estado = { perfil: PerfilPublicado | null; erro: boolean };

interface DespesaDoAno {
  ano: number;
  valor: number;
  quantidade: number;
  /** Proporção do maior ano do mesmo perfil, para a barra. Nunca compara políticos. */
  pct: number;
}

@Component({
  selector: 'app-perfil',
  imports: [RouterLink, Proveniencia, Tabela, Icone],
  templateUrl: './perfil.html',
})
export class Perfil {
  private readonly dados = inject(Dados);
  private readonly router = inject(Router);
  private readonly tituloDaAba = inject(Title);
  private readonly injector = inject(Injector);
  protected readonly consulta = inject(MemoriaLista).consulta;

  /** Identificador antigo (fusão de perfis) redireciona para o vigente. */
  private readonly estado = toSignal(
    combineLatest([
      inject(ActivatedRoute).paramMap,
      this.dados.indice$.pipe(catchError(() => of(null))),
    ]).pipe(
      switchMap(([parametros, indice]) => {
        const id = parametros.get('id') ?? '';
        const vigente = indice?.aliases[id];
        if (vigente) {
          this.router.navigate(['/politico', vigente], { replaceUrl: true });
        }
        return this.dados.perfil(vigente ?? id).pipe(
          map((perfil): Estado => ({ perfil, erro: false })),
          catchError(() => of<Estado>({ perfil: null, erro: true })),
        );
      }),
    ),
  );

  protected readonly perfil = computed(() => this.estado()?.perfil ?? null);
  protected readonly erro = computed(() => this.estado()?.erro ?? false);
  protected readonly blocos = computed(() => Object.keys(this.perfil()?.blocos ?? {}));
  protected readonly situacao = computed(() => {
    const p = this.perfil();
    return p ? situacaoDe(p) : 'exercicio';
  });

  /** Bloco em leitura, para destacar no sumário. */
  protected readonly ativo = signal<string | null>(null);
  private readonly fragmento = toSignal(inject(ActivatedRoute).fragment, { initialValue: null });
  private observador?: IntersectionObserver;

  protected readonly SITUACOES = SITUACOES;
  protected readonly esqueletos = [1, 2, 3];
  protected readonly iniciais = iniciais;
  protected readonly inteiro = inteiro;
  protected readonly rotuloCargo = rotuloCargo;
  protected readonly rotuloBloco = rotuloBloco;
  protected readonly rotuloCurtoBloco = rotuloCurtoBloco;
  protected readonly rotuloLocal = rotuloLocal;
  protected readonly data = data;
  protected readonly moeda = moeda;
  protected readonly semRegistro = SEM_REGISTRO;

  constructor() {
    // O título da aba leva o nome de quem está na tela.
    effect(() => {
      const p = this.perfil();
      if (p) {
        this.tituloDaAba.setTitle(`${p.nome} · Atuação Pública`);
      } else if (this.erro()) {
        this.tituloDaAba.setTitle('Perfil não encontrado · Atuação Pública');
      }
    });

    effect(() => {
      if (!this.perfil()) {
        return;
      }
      // Endereço com fragmento (#/politico/{id}#bloco-votos): o bloco só existe depois da carga.
      const fragmento = untracked(() => this.fragmento());
      afterNextRender(
        () => {
          this.observar();
          if (fragmento) {
            document.getElementById(fragmento)?.scrollIntoView();
          }
        },
        { injector: this.injector },
      );
    });

    // Mantém o item ativo do sumário à vista quando ele é uma faixa que rola de lado.
    effect(() => {
      if (this.ativo()) {
        afterNextRender(
          () => document.querySelector('.perfil-nav a[aria-current="location"]')?.scrollIntoView({ block: 'nearest', inline: 'center' }),
          { injector: this.injector },
        );
      }
    });

    inject(DestroyRef).onDestroy(() => this.observador?.disconnect());
  }

  /** Marca como ativo o primeiro bloco que cruza a faixa superior da janela. */
  private observar(): void {
    this.observador?.disconnect();
    const secoes = Array.from(document.querySelectorAll<HTMLElement>('.perfil-blocos > .bloco'));
    if (secoes.length === 0 || typeof IntersectionObserver === 'undefined') {
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
        const primeiro = secoes.find((s) => visiveis.has(s.id));
        if (primeiro) {
          this.ativo.set(primeiro.id.replace('bloco-', ''));
        }
      },
      { rootMargin: '-96px 0px -55% 0px' },
    );
    secoes.forEach((s) => this.observador?.observe(s));
  }

  /** O roteador atualiza o endereço e rola. Aqui só garante a rolagem (mesmo link duas vezes) e leva o foco ao bloco. */
  protected ir(bloco: string): void {
    const alvo = document.getElementById('bloco-' + bloco);
    alvo?.scrollIntoView();
    alvo?.focus({ preventScroll: true });
    this.ativo.set(bloco);
  }

  /** O bloco de atos tem rótulo e nota próprios no município: a fonte é a câmara, não o Senado. */
  protected titulo(bloco: string): string {
    const esfera = this.perfil()?.esfera;
    if (bloco === 'atos_normativos' && esfera === 'municipal') {
      return 'Atos normativos do município no período do mandato';
    }
    if (bloco === 'despesas_executivo' && esfera === 'federal') {
      return 'Cartão de pagamento e viagens da Presidência da República no período do mandato';
    }
    return rotuloBloco(bloco);
  }

  protected nota(bloco: string): string | null {
    const esfera = this.perfil()?.esfera;
    if (bloco === 'atos_normativos' && esfera === 'municipal') {
      return 'Leis, leis complementares e decretos registrados pela câmara municipal no SAPL. A fonte não informa quem assinou cada ato.';
    }
    if (bloco === 'despesas_executivo' && esfera === 'federal') {
      return 'Cartão de pagamento do Governo Federal e viagens realizadas do órgão Presidência da República, somados por mês a partir dos arquivos do Portal da Transparência. Transações sob sigilo entram pelo valor, sem detalhe. A fonte não informa quem autorizou cada gasto.';
    }
    return NOTAS[bloco] ?? null;
  }

  protected porTipo(bloco: Bloco): [string, number][] {
    return Object.entries(bloco.por_tipo ?? {}).sort((a, b) => b[1] - a[1]);
  }

  protected unidade(bloco: string): string {
    return bloco === 'despesas_executivo' ? 'pagamentos' : 'lançamentos';
  }

  /** Total por ano a partir das somas mensais publicadas. A barra compara só os anos deste perfil. */
  protected despesasPorAno(bloco: Bloco): DespesaDoAno[] {
    const anos = new Map<number, { ano: number; valor: number; quantidade: number }>();
    for (const m of bloco.por_mes ?? []) {
      const a = anos.get(m.ano) ?? { ano: m.ano, valor: 0, quantidade: 0 };
      a.valor += m.valor;
      a.quantidade += m.quantidade;
      anos.set(m.ano, a);
    }
    const lista = [...anos.values()].sort((a, b) => b.ano - a.ano);
    const maior = Math.max(0, ...lista.map((a) => a.valor));
    return lista.map((a) => ({ ...a, pct: maior > 0 ? Math.max(0, Math.round((a.valor / maior) * 100)) : 0 }));
  }

  /** Primeiro e último ano com despesa, como na fonte. */
  protected periodo(bloco: Bloco): string {
    const anos = (bloco.por_mes ?? []).map((m) => m.ano);
    if (anos.length === 0) {
      return '';
    }
    const [de, ate] = [Math.min(...anos), Math.max(...anos)];
    return de === ate ? String(de) : `${de} a ${ate}`;
  }

  protected categorias(bloco: Bloco, ano: number): DespesaAgregada[] {
    return (bloco.por_categoria ?? []).filter((c) => c.ano === ano).sort((a, b) => b.valor - a.valor);
  }

  protected enderecoJson(id: string): string {
    return this.dados.enderecoDoPerfil(id);
  }
}
