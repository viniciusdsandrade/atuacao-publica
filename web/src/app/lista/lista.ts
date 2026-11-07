import { Location } from '@angular/common';
import { Component, Injector, afterNextRender, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, map, of, startWith, switchMap } from 'rxjs';

import { Dados } from '../dados';
import { Icone } from '../icone/icone';
import { EntradaIndice, Indice } from '../modelo';
import {
  Situacao,
  SITUACOES,
  chaveDeBusca,
  data,
  iniciais,
  inteiro,
  nomeDoEnte,
  rotuloCargo,
  rotuloEsfera,
  rotuloLocal,
  situacaoDe,
} from '../rotulos';
import { MemoriaLista } from './memoria-lista';

/** Quantos cartões entram a cada "Mostrar mais". */
const LOTE = 48;
/** Acima disto, o partido fica só na lista de seleção (ADR-0035). */
const MAXIMO_CHIPS_PARTIDO = 12;
const LIMITE_MOSTRAR = 5000;
const ORDEM_ESFERAS = ['federal', 'estadual', 'municipal'];
const ORDEM_CARGOS = [
  'presidente', 'governador', 'prefeito', 'vice_prefeito', 'senador', 'deputado_federal', 'deputado_estadual',
  'deputado_distrital', 'vereador',
];

interface Filtros {
  busca: string;
  situacao: Situacao | '';
  esfera: string;
  cargo: string;
  uf: string;
  partido: string;
  mostrar: number;
}

/** Começa em quem está em exercício. Quem saiu e quem aguarda posse ficam no arquivo, por este filtro. */
const PADRAO: Filtros = { busca: '', situacao: 'exercicio', esfera: '', cargo: '', uf: '', partido: '', mostrar: LOTE };

type Carga = { estado: 'carregando' } | { estado: 'erro' } | { estado: 'pronto'; indice: Indice };

/** A busca vive no endereço: #/?q=ana&situacao=todos&esfera=municipal. Só o que difere do padrão entra. */
function dosParametros(p: ParamMap): Filtros {
  const situacao = p.get('situacao');
  const mostrar = Number(p.get('mostrar'));
  return {
    busca: p.get('q') ?? '',
    situacao: situacao === 'todos' ? '' : situacao === 'posse' || situacao === 'fora' ? situacao : 'exercicio',
    esfera: p.get('esfera') ?? '',
    cargo: p.get('cargo') ?? '',
    uf: p.get('uf') ?? '',
    partido: p.get('partido') ?? '',
    mostrar: Number.isFinite(mostrar) && mostrar > LOTE ? Math.min(Math.ceil(mostrar / LOTE) * LOTE, LIMITE_MOSTRAR) : LOTE,
  };
}

function paraParametros(f: Filtros): Record<string, string> {
  const saida: Record<string, string> = {};
  if (f.busca.trim()) {
    saida['q'] = f.busca;
  }
  if (f.situacao !== PADRAO.situacao) {
    saida['situacao'] = f.situacao || 'todos';
  }
  for (const campo of ['esfera', 'cargo', 'uf', 'partido'] as const) {
    if (f[campo]) {
      saida[campo] = f[campo];
    }
  }
  if (f.mostrar > LOTE) {
    saida['mostrar'] = String(f.mostrar);
  }
  return saida;
}

function iguais(a: Filtros, b: Filtros): boolean {
  return (Object.keys(a) as (keyof Filtros)[]).every((k) => a[k] === b[k]);
}

function ordenar(valores: Iterable<string>, ordem: string[]): string[] {
  return [...valores].sort((a, b) => {
    const ia = ordem.indexOf(a);
    const ib = ordem.indexOf(b);
    if (ia !== -1 || ib !== -1) {
      return (ia !== -1 ? ia : 99) - (ib !== -1 ? ib : 99);
    }
    return a.localeCompare(b, 'pt-BR');
  });
}

@Component({
  selector: 'app-lista',
  imports: [RouterLink, Icone],
  templateUrl: './lista.html',
})
export class Lista {
  private readonly dados = inject(Dados);
  private readonly location = inject(Location);
  private readonly memoria = inject(MemoriaLista);
  private readonly injector = inject(Injector);

  private readonly tentar$ = new BehaviorSubject<void>(undefined);
  protected readonly carga = toSignal(
    this.tentar$.pipe(
      switchMap(() =>
        this.dados.indice$.pipe(
          map((indice): Carga => ({ estado: 'pronto', indice })),
          startWith<Carga>({ estado: 'carregando' }),
          catchError(() => of<Carga>({ estado: 'erro' })),
        ),
      ),
    ),
    { requireSync: true },
  );
  protected readonly execucao = toSignal(this.dados.execucao$.pipe(catchError(() => of(null))), { initialValue: null });

  protected readonly indice = computed(() => {
    const c = this.carga();
    return c.estado === 'pronto' ? c.indice : null;
  });
  protected readonly erro = computed(() => this.carga().estado === 'erro');

  protected readonly filtros = signal<Filtros>({ ...PADRAO });
  protected readonly extrasAbertos = signal(false);
  protected readonly anuncio = signal('');

  protected readonly lote = LOTE;
  protected readonly esqueletos = [1, 2, 3, 4, 5, 6, 7, 8];
  protected readonly inteiro = inteiro;
  protected readonly data = data;
  protected readonly rotuloCargo = rotuloCargo;
  protected readonly rotuloEsfera = rotuloEsfera;

  private readonly politicos = computed(() => this.indice()?.politicos ?? []);

  /** Texto de busca de cada político, sem acento nem caixa, calculado uma vez por índice. */
  private readonly chaves = computed(() =>
    this.politicos().map((p) =>
      chaveDeBusca([p.nome, nomeDoEnte(p.ente), p.partido ?? '', rotuloCargo(p.cargo)].join(' ')),
    ),
  );

  protected readonly esferas = computed(() => ordenar(this.valores((p) => p.esfera), ORDEM_ESFERAS));
  protected readonly cargos = computed(() => ordenar(this.valores((p) => p.cargo), ORDEM_CARGOS));
  protected readonly ufs = computed(() => ordenar(this.valores((p) => p.uf), []));
  protected readonly partidos = computed(() => ordenar(this.valores((p) => p.partido), []));

  /** Cada palavra da busca precisa aparecer no nome, no município, no partido ou no cargo. */
  private filtrar(ignorar: 'situacao' | 'esfera' | 'uf' | 'partido' | null): EntradaIndice[] {
    const f = this.filtros();
    const termos = chaveDeBusca(f.busca).split(/\s+/).filter(Boolean);
    const chaves = this.chaves();
    return this.politicos().filter(
      (p, i) =>
        termos.every((t) => chaves[i].includes(t)) &&
        (ignorar === 'situacao' || !f.situacao || situacaoDe(p) === f.situacao) &&
        (ignorar === 'esfera' || !f.esfera || p.esfera === f.esfera) &&
        (!f.cargo || p.cargo === f.cargo) &&
        (ignorar === 'uf' || !f.uf || p.uf === f.uf) &&
        (ignorar === 'partido' || !f.partido || p.partido === f.partido),
    );
  }

  protected readonly resultado = computed(() => this.filtrar(null));
  private readonly semSituacao = computed(() => this.filtrar('situacao'));
  private readonly semEsfera = computed(() => this.filtrar('esfera'));
  private readonly semUf = computed(() => this.filtrar('uf'));
  private readonly semPartido = computed(() => this.filtrar('partido'));

  /** Contagens que respeitam os outros filtros: o número prevê o resultado de cada clique. */
  protected readonly segmentosSituacao = computed(() => {
    const base = this.semSituacao();
    const contagem: Record<Situacao, number> = { exercicio: 0, posse: 0, fora: 0 };
    for (const p of base) {
      contagem[situacaoDe(p)]++;
    }
    return [
      { valor: 'exercicio' as Situacao | '', rotulo: SITUACOES.exercicio, total: contagem.exercicio },
      { valor: 'posse' as Situacao | '', rotulo: SITUACOES.posse, total: contagem.posse },
      { valor: 'fora' as Situacao | '', rotulo: SITUACOES.fora, total: contagem.fora },
      { valor: '' as Situacao | '', rotulo: 'Todos', total: base.length },
    ];
  });

  protected readonly segmentosEsfera = computed(() => {
    const base = this.semEsfera();
    const contagem = new Map<string, number>();
    for (const p of base) {
      contagem.set(p.esfera, (contagem.get(p.esfera) ?? 0) + 1);
    }
    return [
      { valor: '', rotulo: 'Todas', total: base.length },
      ...this.esferas().map((e) => ({ valor: e, rotulo: rotuloEsfera(e), total: contagem.get(e) ?? 0 })),
    ];
  });

  protected readonly segmentosUf = computed(() => {
    const base = this.semUf();
    const contagem = new Map<string, number>();
    for (const p of base) {
      if (p.uf) {
        contagem.set(p.uf, (contagem.get(p.uf) ?? 0) + 1);
      }
    }
    return [
      { valor: '', rotulo: 'Todas', total: base.length },
      ...this.ufs().map((u) => ({ valor: u, rotulo: u, total: contagem.get(u) ?? 0 })),
    ];
  });

  /**
   * Chips de partido só quando o recorte tem de 2 a 12 partidos, sempre em ordem alfabética (ADR-0035).
   * Botão só para as maiores bancadas seria ranking: acima do limite, o partido fica na lista de seleção.
   */
  protected readonly segmentosPartido = computed(() => {
    const base = this.semPartido();
    const contagem = new Map<string, number>();
    for (const p of base) {
      if (p.partido) {
        contagem.set(p.partido, (contagem.get(p.partido) ?? 0) + 1);
      }
    }
    const selecionado = this.filtros().partido;
    if (selecionado && !contagem.has(selecionado)) {
      contagem.set(selecionado, 0);
    }
    if (contagem.size < 2 || contagem.size > MAXIMO_CHIPS_PARTIDO) {
      return [];
    }
    const siglas = [...contagem.keys()].sort((a, b) => a.localeCompare(b, 'pt-BR'));
    return [
      { valor: '', rotulo: 'Todos os partidos', total: base.length },
      ...siglas.map((sigla) => ({ valor: sigla, rotulo: sigla, total: contagem.get(sigla) ?? 0 })),
    ];
  });

  protected readonly visiveis = computed(() =>
    this.resultado()
      .slice(0, this.filtros().mostrar)
      .map((p) => ({
        p,
        situacao: situacaoDe(p),
        cargo: rotuloCargo(p.cargo),
        local: rotuloLocal(p.esfera, p.ente, p.uf),
        iniciais: iniciais(p.nome),
      })),
  );
  protected readonly restantes = computed(() => Math.max(0, this.resultado().length - this.visiveis().length));

  protected readonly ativos = computed(() => {
    const f = this.filtros();
    const lista: { chave: keyof Filtros; rotulo: string }[] = [];
    if (f.busca.trim()) {
      lista.push({ chave: 'busca', rotulo: `Busca: “${f.busca.trim()}”` });
    }
    if (f.situacao !== PADRAO.situacao) {
      lista.push({ chave: 'situacao', rotulo: f.situacao ? SITUACOES[f.situacao] : 'Todas as situações' });
    }
    if (f.esfera) {
      lista.push({ chave: 'esfera', rotulo: `Esfera: ${rotuloEsfera(f.esfera)}` });
    }
    if (f.cargo) {
      lista.push({ chave: 'cargo', rotulo: `Cargo: ${rotuloCargo(f.cargo)}` });
    }
    if (f.uf) {
      lista.push({ chave: 'uf', rotulo: `UF: ${f.uf}` });
    }
    if (f.partido) {
      lista.push({ chave: 'partido', rotulo: `Partido: ${f.partido}` });
    }
    return lista;
  });

  protected readonly extrasAtivos = computed(() => {
    const f = this.filtros();
    // A esfera fica à vista em todas as larguras; "Mais filtros" conta só o que está dentro dele.
    return [f.cargo, f.uf, f.partido].filter(Boolean).length;
  });

  /** Números do índice para o herói. Contagens simples, iguais para todos. */
  protected readonly numeros = computed(() => {
    const i = this.indice();
    if (!i) {
      return null;
    }
    return {
      total: i.total,
      emExercicio: i.politicos.filter((p) => p.em_exercicio).length,
      fontes: this.execucao()?.fontes.length ?? 0,
    };
  });

  /** Resumo do estado das coletas, com link para a página de detalhe. */
  protected readonly resumoColetas = computed(() => {
    const e = this.execucao();
    if (!e) {
      return null;
    }
    const total = e.fontes.length;
    const comAviso = e.fontes.filter((f) => f.status === 'falha' || f.status === 'parcial').length;
    return { total, semAviso: total - comAviso, comAviso };
  });

  constructor() {
    const rota = inject(ActivatedRoute);
    this.filtros.set(dosParametros(rota.snapshot.queryParamMap));
    this.extrasAbertos.set(this.extrasAtivos() > 0);
    this.memoria.consulta = paraParametros(this.filtros());

    // Mudança de endereço vinda do roteador (link, edição manual). Os ajustes que a própria tela
    // faz no endereço não passam pelo roteador e não voltam por aqui.
    rota.queryParamMap.pipe(takeUntilDestroyed()).subscribe((p) => {
      const novo = dosParametros(p);
      if (!iguais(novo, this.filtros())) {
        this.filtros.set(novo);
      }
    });

    // Espelha os filtros no endereço sem criar entrada no histórico e sem passar pelo roteador,
    // que rolaria a página ao topo. Pequena espera: o Safari limita as trocas de endereço por segundo.
    effect((limpar) => {
      const parametros = paraParametros(this.filtros());
      const espera = setTimeout(() => this.espelhar(parametros), 200);
      limpar(() => clearTimeout(espera));
    });

    // Remove da busca um valor que não existe no índice (endereço digitado à mão ou dado que saiu).
    effect(() => {
      if (!this.indice()) {
        return;
      }
      const f = this.filtros();
      const validos = {
        esfera: this.esferas(),
        cargo: this.cargos(),
        uf: this.ufs(),
        partido: this.partidos(),
      };
      const invalidos = (Object.keys(validos) as (keyof typeof validos)[]).filter((k) => f[k] && !validos[k].includes(f[k]));
      if (invalidos.length) {
        this.filtros.update((atual) => ({ ...atual, ...Object.fromEntries(invalidos.map((k) => [k, ''])) }));
      }
    });

    // Anuncia a contagem para leitor de tela depois que a digitação para, sem repetir a cada tecla.
    effect((limpar) => {
      const indice = this.indice();
      const total = this.resultado().length;
      if (!indice) {
        return;
      }
      const espera = setTimeout(
        () => this.anuncio.set(total === 1 ? '1 político encontrado.' : `${inteiro(total)} políticos encontrados.`),
        600,
      );
      limpar(() => clearTimeout(espera));
    });
  }

  private valores(campo: (p: EntradaIndice) => string | null): Set<string> {
    const valores = new Set<string>();
    for (const p of this.politicos()) {
      const v = campo(p);
      if (v) {
        valores.add(v);
      }
    }
    return valores;
  }

  private espelhar(parametros: Record<string, string>): void {
    this.memoria.consulta = parametros;
    // Mantém o estado do histórico: é por ele que o roteador reencontra a rolagem ao voltar.
    this.location.replaceState('/', new URLSearchParams(parametros).toString(), this.location.getState());
  }

  protected valor(evento: Event): string {
    return (evento.target as HTMLInputElement | HTMLSelectElement).value;
  }

  protected alterar(mudanca: Partial<Filtros>): void {
    this.filtros.update((f) => ({ ...f, mostrar: LOTE, ...mudanca }));
  }

  protected remover(chave: keyof Filtros): void {
    this.alterar({ [chave]: PADRAO[chave] } as Partial<Filtros>);
  }

  protected limpar(): void {
    this.filtros.set({ ...PADRAO });
  }

  /** Ao fim da digitação, grava o endereço na hora (o usuário pode sair da página em seguida). */
  protected gravarAgora(): void {
    this.espelhar(paraParametros(this.filtros()));
  }

  protected mostrarMais(todos: boolean): void {
    const primeiroNovo = this.visiveis().length;
    this.filtros.update((f) => ({
      ...f,
      mostrar: todos ? Math.min(this.resultado().length, LIMITE_MOSTRAR) : f.mostrar + LOTE,
    }));
    // O botão sai do lugar quando entram os cartões novos: leva o foco ao primeiro deles.
    afterNextRender(
      () => document.querySelector<HTMLElement>(`.grade-cartoes li[data-i="${primeiroNovo}"] a`)?.focus(),
      { injector: this.injector },
    );
  }

  protected tentarDeNovo(): void {
    this.tentar$.next();
  }
}
