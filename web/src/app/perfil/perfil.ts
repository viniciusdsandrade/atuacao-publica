import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, Injector, afterNextRender, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { BehaviorSubject, Observable, catchError, combineLatest, map, of, startWith, switchMap } from 'rxjs';

import { Dados } from '../dados';
import { Icone } from '../icone/icone';
import { MemoriaLista } from '../lista/memoria-lista';
import { Bloco, ContasDeCampanha, DespesaAgregada, EmendaPorAno, Perfil as PerfilPublicado, RedeDeclarada, SomaDeCampanha } from '../modelo';

interface CampanhaDoAno {
  eleicao: number;
  cargo: string;
  receitas: ContasDeCampanha | null;
  despesas: ContasDeCampanha | null;
}
import {
  casaLegislativa,
  data,
  ePapelLideranca,
  iniciais,
  inteiro,
  moeda,
  notaDoBloco,
  rotuloCargo,
  rotuloCurtoBloco,
  rotuloLocal,
  SEM_REGISTRO,
  Situacao,
  SITUACOES,
  situacaoDe,
  tituloDoBloco,
} from '../rotulos';
import { manterVisivel } from '../rolagem';
import { Proveniencia } from './proveniencia';
import { Tabela } from './tabela';

/** 'inexistente': não há arquivo com este identificador. 'rede': o arquivo não respondeu, e vale tentar de novo. */
type Falha = 'inexistente' | 'rede';
type Estado = { perfil: PerfilPublicado | null; falha: Falha | null };

const CARREGANDO: Estado = { perfil: null, falha: null };

/** O que o cabeçalho mostra. Vem do perfil ou, antes de ele chegar, da entrada do índice que a lista já baixou. */
interface Identidade {
  nome: string;
  cargo: string;
  esfera: string;
  ente: string;
  uf: string | null;
  partido: string | null;
  situacao: Situacao;
}

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
  private readonly rota = inject(ActivatedRoute);
  private readonly location = inject(Location);
  private readonly tituloDaAba = inject(Title);
  private readonly injector = inject(Injector);
  protected readonly consulta = inject(MemoriaLista).consulta;

  private readonly tentar$ = new BehaviorSubject<void>(undefined);
  private readonly id = toSignal(this.rota.paramMap.pipe(map((p) => p.get('id') ?? '')), { requireSync: true });

  /** O arquivo do perfil é pedido na hora, sem esperar o índice: quem chega por link direto não baixa a lista. */
  private readonly estado = toSignal(
    combineLatest([this.rota.paramMap, this.tentar$]).pipe(
      switchMap(([parametros]) => {
        const id = parametros.get('id') ?? '';
        return this.dados.perfil(id).pipe(
          map((perfil): Estado => ({ perfil, falha: null })),
          catchError((erro: unknown) => this.aoFalhar(id, erro)),
          startWith(CARREGANDO),
        );
      }),
    ),
    { initialValue: CARREGANDO },
  );

  protected readonly perfil = computed(() => this.estado().perfil);
  protected readonly falha = computed(() => this.estado().falha);
  protected readonly blocos = computed(() => Object.keys(this.perfil()?.blocos ?? {}));

  /**
   * Enquanto o arquivo do perfil não chega, o cabeçalho usa a entrada do índice que já está em memória
   * (quem veio da lista). Nome, cargo, partido e situação aparecem na hora; os blocos, quando o arquivo chegar.
   */
  protected readonly identidade = computed((): Identidade | null => {
    const p = this.perfil();
    if (p) {
      return { nome: p.nome, cargo: p.cargo_atual, esfera: p.esfera, ente: p.ente, uf: p.uf, partido: p.partido_atual, situacao: situacaoDe(p) };
    }
    if (this.falha()) {
      return null;
    }
    const id = this.id();
    const e = this.dados.indiceEmMemoria()?.politicos.find((entrada) => entrada.id === id);
    return e ? { nome: e.nome, cargo: e.cargo, esfera: e.esfera, ente: e.ente, uf: e.uf, partido: e.partido, situacao: situacaoDe(e) } : null;
  });
  /** O atalho para o lado a lado só vale para parlamentares (ADR-0036). */
  protected readonly eParlamentar = computed(() => {
    const p = this.perfil();
    return !!p && casaLegislativa(p.cargo_atual, p.uf, p.ente) !== null;
  });
  /** Cargos de comando em órgãos, comissões ou frentes atualmente em andamento. */
  protected readonly liderancasAtivas = computed(() => {
    const p = this.perfil();
    if (!p) {
      return [];
    }
    const bloco = p.blocos?.['orgaos_e_frentes'];
    const regs = (bloco?.registros ?? bloco?.recentes ?? []) as Array<Record<string, unknown>>;
    if (regs.length === 0) {
      return [];
    }
    const hoje = new Date().toISOString().substring(0, 10);
    return regs
      .filter((r) => {
        const fim = typeof r['fim'] === 'string' ? r['fim'] : null;
        const papel = typeof r['papel'] === 'string' ? r['papel'] : null;
        return (!fim || fim >= hoje) && ePapelLideranca(papel);
      })
      .slice(0, 4)
      .map((r) => ({
        papel: String(r['papel']),
        nome: String(r['nome']),
        sigla: typeof r['sigla'] === 'string' ? r['sigla'] : null,
      }));
  });

  /** Redes e sites da candidatura mais recente que declarou algum ao TSE (ADR-0031). */
  protected readonly redesDeclaradas = computed(() => {
    const candidaturas = this.perfil()?.blocos?.['candidaturas']?.registros ?? [];
    let escolhida: { eleicao: number; redes: RedeDeclarada[] } | null = null;
    for (const c of candidaturas) {
      const redes = Array.isArray(c['redes_declaradas']) ? (c['redes_declaradas'] as RedeDeclarada[]) : [];
      const eleicao = typeof c['eleicao'] === 'number' ? c['eleicao'] : 0;
      if (redes.length > 0 && (!escolhida || eleicao > escolhida.eleicao)) {
        escolhida = { eleicao, redes };
      }
    }
    return escolhida;
  });

  /** Bloco em leitura, para destacar no sumário. */
  protected readonly ativo = signal<string | null>(null);
  private readonly fragmento = toSignal(inject(ActivatedRoute).fragment, { initialValue: null });
  private observador?: IntersectionObserver;

  /** Retorno do botão "Compartilhar" quando o endereço vai para a área de transferência. */
  protected readonly copia = signal<'feita' | 'falhou' | null>(null);
  private avisoDaCopia?: ReturnType<typeof setTimeout>;

  protected readonly SITUACOES = SITUACOES;
  protected readonly esqueletos = [1, 2, 3];
  protected readonly iniciais = iniciais;
  protected readonly inteiro = inteiro;
  protected readonly rotuloCargo = rotuloCargo;
  protected readonly rotuloCurtoBloco = rotuloCurtoBloco;
  protected readonly rotuloLocal = rotuloLocal;
  protected readonly data = data;
  protected readonly moeda = moeda;
  protected readonly semRegistro = SEM_REGISTRO;

  constructor() {
    // O título da aba leva o nome de quem está na tela.
    effect(() => {
      const identidade = this.identidade();
      if (identidade) {
        this.tituloDaAba.setTitle(`${identidade.nome} · Atuação Pública`);
      } else if (this.falha()) {
        this.tituloDaAba.setTitle(
          this.falha() === 'rede' ? 'Perfil indisponível · Atuação Pública' : 'Perfil não encontrado · Atuação Pública',
        );
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

    // Mantém o item ativo do sumário à vista (faixa que rola de lado no celular, coluna no computador).
    effect(() => {
      if (this.ativo()) {
        afterNextRender(
          () => {
            const item = document.querySelector<HTMLElement>('.perfil-nav a[aria-current="location"]');
            const nav = document.querySelector<HTMLElement>('.perfil-nav');
            const lista = nav?.querySelector<HTMLElement>('ul');
            if (item && nav && lista) {
              manterVisivel(item, lista);
              manterVisivel(item, nav);
            }
          },
          { injector: this.injector },
        );
      }
    });

    inject(DestroyRef).onDestroy(() => {
      this.observador?.disconnect();
      clearTimeout(this.avisoDaCopia);
    });
  }

  /**
   * Sem resposta do servidor (status 0), o problema é de rede: a tela oferece tentar de novo.
   * Arquivo ausente pode ser identificador antigo (fusão de perfis): o índice diz qual é o vigente.
   */
  private aoFalhar(id: string, erro: unknown): Observable<Estado> {
    const status = erro instanceof HttpErrorResponse ? erro.status : 0;
    if (status === 0) {
      return of<Estado>({ perfil: null, falha: 'rede' });
    }
    return this.dados.indice$.pipe(
      catchError(() => of(null)),
      map((indice): Estado => {
        const vigente = indice?.aliases[id];
        if (vigente) {
          this.router.navigate(['/politico', vigente], { replaceUrl: true });
          return CARREGANDO;
        }
        return { perfil: null, falha: status === 404 ? 'inexistente' : 'rede' };
      }),
    );
  }

  protected tentarDeNovo(): void {
    this.tentar$.next();
  }

  /**
   * No celular e no tablet, abre o compartilhamento do aparelho. No computador, copia o endereço do perfil.
   * O endereço vai sem o fragmento do bloco: quem recebe começa pelo topo do perfil.
   */
  protected async compartilhar(p: PerfilPublicado): Promise<void> {
    const endereco = new URL(this.location.prepareExternalUrl('/politico/' + p.id), document.baseURI).href;
    const toque = typeof matchMedia === 'function' && matchMedia('(pointer: coarse)').matches;
    if (toque && typeof navigator.share === 'function') {
      try {
        await navigator.share({ title: `${p.nome} · Atuação Pública`, url: endereco });
        return;
      } catch (erro) {
        if (erro instanceof DOMException && erro.name === 'AbortError') {
          return; // A pessoa fechou a janela de compartilhar.
        }
      }
    }
    try {
      await navigator.clipboard.writeText(endereco);
      this.copia.set('feita');
    } catch {
      this.copia.set('falhou');
    }
    clearTimeout(this.avisoDaCopia);
    this.avisoDaCopia = setTimeout(() => this.copia.set(null), 4000);
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

  protected titulo(bloco: string): string {
    return tituloDoBloco(bloco, this.perfil()?.esfera);
  }

  protected nota(bloco: string): string | null {
    return notaDoBloco(bloco, this.perfil()?.esfera, this.perfil()?.ente);
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

  /** Anos do orçamento com emenda, do mais recente ao mais antigo, com as somas publicadas. */
  protected emendasPorAno(bloco: Bloco): EmendaPorAno[] {
    return [...(bloco.por_ano ?? [])].sort((a, b) => b.ano - a.ano);
  }

  /** Soma de um valor em todos os anos. Cada campo da fonte é somado só com ele mesmo. */
  protected somaDasEmendas(bloco: Bloco, campo: 'valor_empenhado' | 'valor_pago' | 'restos_pagos'): number {
    return (bloco.por_ano ?? []).reduce((soma, a) => soma + a[campo], 0);
  }

  /** Receitas e despesas de cada eleição, da mais recente para a mais antiga (ADR-0032). */
  protected campanhas(bloco: Bloco): CampanhaDoAno[] {
    const porEleicao = new Map<number, CampanhaDoAno>();
    for (const r of (bloco.registros ?? []) as unknown as ContasDeCampanha[]) {
      const campanha = porEleicao.get(r.eleicao) ?? { eleicao: r.eleicao, cargo: r.cargo, receitas: null, despesas: null };
      if (r.tipo === 'receitas') {
        campanha.receitas = r;
      } else {
        campanha.despesas = r;
      }
      porEleicao.set(r.eleicao, campanha);
    }
    return [...porEleicao.values()].sort((a, b) => b.eleicao - a.eleicao);
  }

  /** As somas por categoria de uma eleição, uma tabela por recorte. */
  protected tabelasDaCampanha(c: CampanhaDoAno): { titulo: string; coluna: string; linhas: SomaDeCampanha[] }[] {
    return [
      { titulo: 'Receitas por fonte', coluna: 'Fonte', linhas: c.receitas?.por_fonte ?? [] },
      { titulo: 'Receitas por origem', coluna: 'Origem', linhas: c.receitas?.por_origem ?? [] },
      { titulo: 'Despesas contratadas por tipo', coluna: 'Tipo', linhas: c.despesas?.por_tipo ?? [] },
    ];
  }

  /** O nome da rede, ou o domínio quando é um site: vários sites não ficam com o mesmo texto de link. */
  protected rotuloDaRede(rede: RedeDeclarada): string {
    if (rede.rede !== 'Site') {
      return rede.rede;
    }
    try {
      return new URL(rede.url).hostname.replace(/^www\./, '');
    } catch {
      return rede.rede;
    }
  }

  protected enderecoJson(id: string): string {
    return this.dados.enderecoDoPerfil(id);
  }

  /** A folha de impressão (impressao.css) já tira menus e mostra o endereço de cada fonte. */
  protected imprimir(): void {
    window.print();
  }
}
