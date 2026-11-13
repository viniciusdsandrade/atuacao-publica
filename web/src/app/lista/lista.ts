import { Location, NgTemplateOutlet } from '@angular/common';
import { Component, Injector, afterNextRender, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, map, of, startWith, switchMap } from 'rxjs';

import { Dados } from '../dados';
import { Icone } from '../icone/icone';
import { EntradaIndice, Indice } from '../modelo';
import {
  NOMES_DAS_UFS,
  Situacao,
  SITUACOES,
  chaveDeBusca,
  data,
  iniciais,
  inteiro,
  nomeDoEnte,
  realcar,
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
/** Âncora da área de filtros e resultados: quem escolhe uma porta de entrada chega aqui. */
const ANCORA_DO_RECORTE = 'recorte';
/** Tempo com o ponteiro parado sobre o cartão antes de pedir o perfil: passar por cima não baixa nada. */
const ESPERA_DO_AQUECIMENTO_MS = 120;
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
  /** Lista completa pedida de propósito ("Ver todos"). Sem busca, recorte nem este pedido, a página não lista ninguém. */
  lista: boolean;
  mostrar: number;
}

/** Começa em quem está em exercício. Quem saiu e quem aguarda posse ficam no arquivo, por este filtro. */
const PADRAO: Filtros = { busca: '', situacao: 'exercicio', esfera: '', cargo: '', uf: '', partido: '', lista: false, mostrar: LOTE };

type Carga = { estado: 'carregando' } | { estado: 'erro' } | { estado: 'pronto'; indice: Indice };

/** A busca vive no endereço: #/?q=ana&situacao=todos&esfera=municipal&lista=completa. Só o que difere do padrão entra. */
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
    lista: p.get('lista') === 'completa',
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
  if (f.lista) {
    saida['lista'] = 'completa';
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
  imports: [RouterLink, Icone, NgTemplateOutlet],
  templateUrl: './lista.html',
})
export class Lista {
  private readonly dados = inject(Dados);
  private readonly location = inject(Location);
  private readonly router = inject(Router);
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

  /**
   * Texto de busca de cada político, sem acento nem caixa, calculado uma vez por índice.
   * O estado entra pela sigla e pelo nome: "silva sp" e "deputado bahia" acham o que se espera.
   */
  private readonly chaves = computed(() =>
    this.politicos().map((p) =>
      chaveDeBusca(
        [p.nome, nomeDoEnte(p.ente), p.uf ?? '', NOMES_DAS_UFS[p.uf ?? ''] ?? '', p.partido ?? '', rotuloCargo(p.cargo)].join(' '),
      ),
    ),
  );

  protected readonly esferas = computed(() => ordenar(this.valores((p) => p.esfera), ORDEM_ESFERAS));
  protected readonly cargos = computed(() => ordenar(this.valores((p) => p.cargo), ORDEM_CARGOS));
  protected readonly ufs = computed(() => ordenar(this.valores((p) => p.uf), []));
  protected readonly partidos = computed(() => ordenar(this.valores((p) => p.partido), []));

  /** Palavras da busca, sem acento nem caixa. */
  private readonly termos = computed(() => chaveDeBusca(this.filtros().busca).split(/\s+/).filter(Boolean));

  /** Cada palavra da busca precisa aparecer no nome, no estado, no município, no partido ou no cargo. */
  private filtrar(ignorar: 'situacao' | 'esfera' | 'cargo' | 'uf' | 'partido' | null): EntradaIndice[] {
    const f = this.filtros();
    const termos = this.termos();
    const chaves = this.chaves();
    return this.politicos().filter(
      (p, i) =>
        termos.every((t) => chaves[i].includes(t)) &&
        (ignorar === 'situacao' || !f.situacao || situacaoDe(p) === f.situacao) &&
        (ignorar === 'esfera' || !f.esfera || p.esfera === f.esfera) &&
        (ignorar === 'cargo' || !f.cargo || p.cargo === f.cargo) &&
        (ignorar === 'uf' || !f.uf || p.uf === f.uf) &&
        (ignorar === 'partido' || !f.partido || p.partido === f.partido),
    );
  }

  protected readonly resultado = computed(() => this.filtrar(null));
  private readonly semSituacao = computed(() => this.filtrar('situacao'));
  private readonly semEsfera = computed(() => this.filtrar('esfera'));
  private readonly semCargo = computed(() => this.filtrar('cargo'));
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

  /**
   * Com um estado escolhido, o passo seguinte é o cargo: os cargos do recorte viram chips com contagem,
   * na ordem institucional, no lugar da esfera (ADR-0056). Fora disso, o cargo fica na lista de seleção.
   */
  protected readonly segmentosCargo = computed(() => {
    const base = this.semCargo();
    const contagem = new Map<string, number>();
    for (const p of base) {
      contagem.set(p.cargo, (contagem.get(p.cargo) ?? 0) + 1);
    }
    const selecionado = this.filtros().cargo;
    if (selecionado && !contagem.has(selecionado)) {
      contagem.set(selecionado, 0);
    }
    if (contagem.size < 2) {
      return [];
    }
    return [
      { valor: '', rotulo: 'Todos os cargos', total: base.length },
      ...ordenar(contagem.keys(), ORDEM_CARGOS).map((cargo) => ({ valor: cargo, rotulo: rotuloCargo(cargo), total: contagem.get(cargo) ?? 0 })),
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

  /** Nome, cargo e local vão em trechos: o que casou com a busca aparece marcado no cartão. */
  protected readonly visiveis = computed(() => {
    const termos = this.termos();
    return this.resultado()
      .slice(0, this.filtros().mostrar)
      .map((p) => ({
        p,
        situacao: situacaoDe(p),
        nome: realcar(p.nome, termos),
        cargo: realcar(rotuloCargo(p.cargo), termos),
        local: realcar(rotuloLocal(p.esfera, p.ente, p.uf), termos),
        iniciais: iniciais(p.nome),
      }));
  });
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
    // Só o pedido de lista completa mantém a lista aberta: ele vira o chip que leva de volta ao ponto de partida.
    if (f.lista && lista.length === 0) {
      lista.push({ chave: 'lista', rotulo: 'Lista completa' });
    }
    return lista;
  });

  /**
   * A lista só aparece quando a pessoa pede: busca, algum recorte ou "Ver todos" (ADR-0056).
   * Sem isso, a página mostra o ponto de partida e não lista ninguém.
   */
  protected readonly comRecorte = computed(() => {
    const f = this.filtros();
    return f.lista || f.busca.trim() !== '' || f.situacao !== PADRAO.situacao || !!(f.esfera || f.cargo || f.uf || f.partido);
  });

  /**
   * Portas de entrada do ponto de partida: quem está em exercício, contado por estado e por cargo.
   * Estados em ordem alfabética do nome; esferas e cargos na ordem institucional de sempre.
   */
  protected readonly partida = computed(() => {
    const porUf = new Map<string, number>();
    const porCargo = new Map<string, number>();
    const esferaDoCargo = new Map<string, string>();
    const situacoes: Record<Situacao, number> = { exercicio: 0, posse: 0, fora: 0 };
    for (const p of this.politicos()) {
      const situacao = situacaoDe(p);
      situacoes[situacao]++;
      if (situacao !== 'exercicio') {
        continue;
      }
      if (p.uf) {
        porUf.set(p.uf, (porUf.get(p.uf) ?? 0) + 1);
      }
      porCargo.set(p.cargo, (porCargo.get(p.cargo) ?? 0) + 1);
      esferaDoCargo.set(p.cargo, p.esfera);
    }
    const estados = [...porUf]
      .map(([uf, total]) => ({ uf, nome: NOMES_DAS_UFS[uf] ?? uf, total }))
      .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
    const esferas = ordenar(new Set(esferaDoCargo.values()), ORDEM_ESFERAS).map((esfera) => ({
      esfera,
      rotulo: rotuloEsfera(esfera),
      cargos: ordenar(porCargo.keys(), ORDEM_CARGOS)
        .filter((cargo) => esferaDoCargo.get(cargo) === esfera)
        .map((cargo) => ({ cargo, rotulo: rotuloCargo(cargo), total: porCargo.get(cargo) ?? 0 })),
    }));
    return { estados, esferas, situacoes };
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

  /**
   * Enter, a tecla de busca do celular ou "Ver na lista": a tela vai à contagem, logo acima dos cartões.
   * Tirar o foco do campo fecha o teclado do celular, que cobriria a lista.
   */
  protected irAosResultados(campo: HTMLInputElement): void {
    this.gravarAgora();
    campo.blur();
    const titulo = document.getElementById('titulo-resultados');
    titulo?.scrollIntoView();
    titulo?.focus({ preventScroll: true });
  }

  /** Endereço de um recorte, para o link de cada porta de entrada (abrir em outra aba, copiar). */
  protected endereco(recorte: Partial<Filtros>): string {
    const consulta = new URLSearchParams(paraParametros({ ...PADRAO, ...recorte })).toString();
    return this.location.prepareExternalUrl(consulta ? '/?' + consulta : '/');
  }

  /**
   * Porta de entrada escolhida: a lista abre naquele recorte, e a tela vai aos filtros e à contagem.
   * A troca passa pelo roteador para ganhar entrada própria no histórico: Voltar retorna ao ponto de partida,
   * na rolagem em que ele estava. Clique com tecla modificadora segue o link normal (outra aba ou janela).
   */
  protected entrar(evento: MouseEvent, recorte: Partial<Filtros>): void {
    if (evento.button !== 0 || evento.metaKey || evento.ctrlKey || evento.shiftKey || evento.altKey) {
      return;
    }
    evento.preventDefault();
    this.gravarAgora();
    const novo: Filtros = { ...PADRAO, ...recorte };
    // 'reload': o roteador pode já ter visto este endereço antes de um "Limpar tudo", que não passa por ele.
    void this.router
      .navigate(['/'], { queryParams: paraParametros(novo), fragment: ANCORA_DO_RECORTE, onSameUrlNavigation: 'reload' })
      .then(() => {
        if (!iguais(novo, this.filtros())) {
          this.filtros.set(novo);
        }
        afterNextRender(
          () => {
            document.getElementById(ANCORA_DO_RECORTE)?.scrollIntoView();
            document.getElementById('titulo-resultados')?.focus({ preventScroll: true });
          },
          { injector: this.injector },
        );
      });
  }

  private aquecimento?: ReturnType<typeof setTimeout>;

  /**
   * Ponteiro parado sobre um cartão: o perfil começa a baixar antes do clique.
   * Só com mouse (no toque, rolar a lista dispararia o pedido) e fora do modo de economia de dados.
   */
  protected aquecer(id: string, evento: PointerEvent): void {
    const conexao = (navigator as Navigator & { connection?: { saveData?: boolean } }).connection;
    if (evento.pointerType !== 'mouse' || conexao?.saveData) {
      return;
    }
    clearTimeout(this.aquecimento);
    this.aquecimento = setTimeout(() => this.dados.aquecerPerfil(id), ESPERA_DO_AQUECIMENTO_MS);
  }

  protected cancelarAquecimento(): void {
    clearTimeout(this.aquecimento);
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
