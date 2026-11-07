import { Component, Injector, afterNextRender, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, catchError, combineLatest, map, of, startWith, switchMap } from 'rxjs';

import { Dados } from '../dados';
import { Icone } from '../icone/icone';
import { MemoriaLista } from '../lista/memoria-lista';
import { Bloco, EntradaIndice, Perfil } from '../modelo';
import {
  SEM_REGISTRO,
  SITUACOES,
  Situacao,
  casaLegislativa,
  chaveDeBusca,
  ePapelLideranca,
  iniciais,
  inteiro,
  moeda,
  notaDoBloco,
  periodoDosAnos,
  rotuloCargo,
  rotuloLocal,
  situacaoDe,
  tituloDoBloco,
} from '../rotulos';

/** Cada lado: vazio, carregando, identificador sem perfil ou perfil pronto. */
type Lado = { estado: 'vazio' } | { estado: 'carregando' } | { estado: 'erro'; id: string } | { estado: 'pronto'; perfil: Perfil };
type NumeroDoLado = 1 | 2;

interface Lideranca {
  papel: string;
  nome: string;
  sigla: string | null;
}

/** O que cada coluna mostra. Bloco ausente no perfil fica null e aparece como "sem registro na fonte". */
interface Resumo {
  perfil: Perfil;
  situacao: Situacao;
  local: string;
  votacoes: { votos: number; nominais: number | null; periodo: string; porValor: [string, number][] } | null;
  proposicoes: { total: number; periodo: string; porTipo: [string, number][] } | null;
  colegiados: { total: number; liderancas: Lideranca[] } | null;
  despesas: { valor: number | null; lancamentos: number; periodo: string } | null;
}

/** Até quantos nomes a busca de cada lado mostra. */
const RESULTADOS = 6;
/** Até quantas sugestões aparecem antes de qualquer busca. */
const SUGESTOES = 5;

/** Contagem por tipo em ordem alfabética: as duas colunas ficam alinhadas e nenhuma ordem sugere ranking. */
function emOrdemAlfabetica(contagem: Record<string, number>): [string, number][] {
  return Object.entries(contagem).sort((a, b) => a[0].localeCompare(b[0], 'pt-BR'));
}

function anosDosArquivos(bloco: Bloco | undefined): number[] {
  return (bloco?.arquivos ?? []).map((a) => a.ano);
}

function casaDoPerfil(p: Perfil | null): string | null {
  return p ? casaLegislativa(p.cargo_atual, p.uf, p.ente) : null;
}

/** Votos registrados e votações nominais somados ano a ano, com o período e os votos por valor da fonte. */
function votacoesDe(p: Perfil): Resumo['votacoes'] {
  const participacao = (p.blocos['participacao_votacoes']?.registros ?? []) as Array<Record<string, unknown>>;
  const votos = p.blocos['votos'];
  let somaVotos = 0;
  let somaNominais = 0;
  let temNominais = false;
  const anos: number[] = [];
  const porValor: Record<string, number> = {};
  for (const r of participacao) {
    const ano = Number(r['periodo']);
    if (Number.isFinite(ano)) {
      anos.push(ano);
    }
    if (typeof r['votos_registrados'] === 'number') {
      somaVotos += r['votos_registrados'];
    }
    if (typeof r['votacoes_nominais'] === 'number') {
      somaNominais += r['votacoes_nominais'];
      temNominais = true;
    }
    const tipos = r['por_tipo'];
    if (tipos && typeof tipos === 'object') {
      for (const [valor, quantidade] of Object.entries(tipos as Record<string, number>)) {
        porValor[valor] = (porValor[valor] ?? 0) + quantidade;
      }
    }
  }
  if (participacao.length > 0) {
    return { votos: somaVotos, nominais: temNominais ? somaNominais : null, periodo: periodoDosAnos(anos), porValor: emOrdemAlfabetica(porValor) };
  }
  if (votos) {
    return { votos: votos.total, nominais: null, periodo: periodoDosAnos(anosDosArquivos(votos)), porValor: emOrdemAlfabetica(votos.por_tipo ?? {}) };
  }
  return null;
}

function liderancasDe(bloco: Bloco | undefined): Lideranca[] {
  const registros = (bloco?.registros ?? bloco?.recentes ?? []) as Array<Record<string, unknown>>;
  const hoje = new Date().toISOString().substring(0, 10);
  return registros
    .filter((r) => {
      const fim = typeof r['fim'] === 'string' ? r['fim'] : null;
      const papel = typeof r['papel'] === 'string' ? r['papel'] : null;
      return (!fim || fim >= hoje) && ePapelLideranca(papel);
    })
    .map((r) => ({ papel: String(r['papel']), nome: String(r['nome']), sigla: typeof r['sigla'] === 'string' ? r['sigla'] : null }));
}

function resumir(p: Perfil | null): Resumo | null {
  if (!p) {
    return null;
  }
  const proposicoes = p.blocos['proposicoes'];
  const colegiados = p.blocos['orgaos_e_frentes'];
  const despesas = p.blocos['despesas'];
  return {
    perfil: p,
    situacao: situacaoDe(p),
    local: rotuloLocal(p.esfera, p.ente, p.uf),
    votacoes: votacoesDe(p),
    proposicoes: proposicoes
      ? { total: proposicoes.total, periodo: periodoDosAnos(anosDosArquivos(proposicoes)), porTipo: emOrdemAlfabetica(proposicoes.por_tipo ?? {}) }
      : null,
    colegiados: colegiados ? { total: colegiados.total, liderancas: liderancasDe(colegiados) } : null,
    despesas: despesas
      ? {
          valor: despesas.valor_total ?? null,
          lancamentos: despesas.total,
          periodo: periodoDosAnos((despesas.por_mes ?? []).map((m) => m.ano)),
        }
      : null,
  };
}

/** Lado a lado de dois parlamentares da mesma casa (ADR-0036). A rota e os parâmetros p1 e p2 são os de antes. */
@Component({
  selector: 'app-comparar',
  imports: [RouterLink, Icone],
  templateUrl: './comparar.html',
})
export class Comparar {
  private readonly dados = inject(Dados);
  private readonly router = inject(Router);
  private readonly rota = inject(ActivatedRoute);
  private readonly injector = inject(Injector);
  protected readonly consulta = inject(MemoriaLista).consulta;

  protected readonly LADOS: readonly NumeroDoLado[] = [1, 2];
  private readonly buscas = { 1: signal(''), 2: signal('') };
  private readonly indice = toSignal(this.dados.indice$.pipe(catchError(() => of(null))));
  private readonly parametros = toSignal(this.rota.queryParamMap);

  /** Identificador antigo (fusão de perfis) vira o vigente pelo índice. */
  private readonly lados = toSignal(
    combineLatest([this.rota.queryParamMap, this.dados.indice$.pipe(catchError(() => of(null)))]).pipe(
      switchMap(([parametros, indice]) => {
        const vigente = (id: string) => indice?.aliases[id] ?? id;
        return combineLatest([this.lado(vigente(parametros.get('p1') ?? '')), this.lado(vigente(parametros.get('p2') ?? ''))]);
      }),
    ),
    { initialValue: [this.inicial('p1'), this.inicial('p2')] as [Lado, Lado] },
  );

  protected readonly p1 = computed(() => this.perfilDoLado(1));
  protected readonly p2 = computed(() => this.perfilDoLado(2));

  /** Motivo para não montar o lado a lado com os dois perfis escolhidos. */
  protected readonly impedimento = computed((): string | null => {
    const p1 = this.p1();
    const p2 = this.p2();
    if (!p1 || !p2) {
      return null;
    }
    for (const p of [p1, p2]) {
      if (!casaDoPerfil(p)) {
        return `${p.nome} tem cargo de ${rotuloCargo(p.cargo_atual)}. O lado a lado vale só para parlamentares.`;
      }
    }
    if (casaDoPerfil(p1) !== casaDoPerfil(p2)) {
      return 'Os dois perfis são de casas legislativas diferentes. O lado a lado só põe lado a lado parlamentares da mesma casa.';
    }
    return null;
  });

  protected readonly resumos = computed((): [Resumo, Resumo] | null => {
    const r1 = resumir(this.p1());
    const r2 = resumir(this.p2());
    return r1 && r2 && !this.impedimento() ? [r1, r2] : null;
  });

  protected readonly SITUACOES = SITUACOES;
  protected readonly semRegistro = SEM_REGISTRO;
  protected readonly iniciais = iniciais;
  protected readonly inteiro = inteiro;
  protected readonly moeda = moeda;
  protected readonly rotuloCargo = rotuloCargo;
  protected readonly rotuloLocal = rotuloLocal;
  protected readonly notaDoBloco = notaDoBloco;
  protected readonly tituloDoBloco = tituloDoBloco;

  constructor() {
    const titulo = inject(Title);
    effect(() => {
      const p1 = this.p1();
      const p2 = this.p2();
      titulo.setTitle(p1 && p2 ? `${p1.nome} e ${p2.nome} lado a lado · Atuação Pública` : 'Lado a lado · Atuação Pública');
    });
  }

  private inicial(chave: string): Lado {
    return this.rota.snapshot.queryParamMap.get(chave) ? { estado: 'carregando' } : { estado: 'vazio' };
  }

  private lado(id: string): Observable<Lado> {
    if (!id) {
      return of<Lado>({ estado: 'vazio' });
    }
    return this.dados.perfil(id).pipe(
      map((perfil): Lado => ({ estado: 'pronto', perfil })),
      catchError(() => of<Lado>({ estado: 'erro', id })),
      startWith<Lado>({ estado: 'carregando' }),
    );
  }

  private estadoDoLado(lado: NumeroDoLado): Lado {
    return this.lados()[lado - 1];
  }

  protected perfilDoLado(lado: NumeroDoLado): Perfil | null {
    const l = this.estadoDoLado(lado);
    return l.estado === 'pronto' ? l.perfil : null;
  }

  protected carregando(lado: NumeroDoLado): boolean {
    return this.estadoDoLado(lado).estado === 'carregando';
  }

  protected idSemPerfil(lado: NumeroDoLado): string | null {
    const l = this.estadoDoLado(lado);
    return l.estado === 'erro' ? l.id : null;
  }

  protected ordinal(lado: NumeroDoLado): string {
    return lado === 1 ? 'primeiro' : 'segundo';
  }

  protected busca(lado: NumeroDoLado): string {
    return this.buscas[lado]();
  }

  protected digitar(lado: NumeroDoLado, evento: Event): void {
    this.buscas[lado].set((evento.target as HTMLInputElement).value);
  }

  private outro(lado: NumeroDoLado): NumeroDoLado {
    return lado === 1 ? 2 : 1;
  }

  private idDoParametro(lado: NumeroDoLado): string {
    return this.parametros()?.get(lado === 1 ? 'p1' : 'p2') ?? '';
  }

  /**
   * Com busca: parlamentares cujo nome ou partido tem todas as palavras, na casa do outro lado quando ele já existe.
   * Sem busca e com o outro lado escolhido: parlamentares em exercício da mesma casa e da mesma UF.
   * Sempre na ordem do índice, que é alfabética.
   */
  protected opcoes(lado: NumeroDoLado): EntradaIndice[] {
    const indice = this.indice();
    if (!indice) {
      return [];
    }
    const outroPerfil = this.perfilDoLado(this.outro(lado));
    const outroId = this.idDoParametro(this.outro(lado));
    const casa = casaDoPerfil(outroPerfil);
    const termos = chaveDeBusca(this.busca(lado)).split(/\s+/).filter(Boolean);
    const daCasa = (p: EntradaIndice) => {
      const casaDeP = casaLegislativa(p.cargo, p.uf, p.ente);
      return casaDeP !== null && p.id !== outroId && (!casa || casaDeP === casa);
    };
    if (termos.length > 0) {
      return indice.politicos
        .filter((p) => daCasa(p) && termos.every((t) => chaveDeBusca(`${p.nome} ${p.partido ?? ''}`).includes(t)))
        .slice(0, RESULTADOS);
    }
    if (!outroPerfil || !casa) {
      return [];
    }
    return indice.politicos.filter((p) => daCasa(p) && p.em_exercicio && p.uf === outroPerfil.uf).slice(0, SUGESTOES);
  }

  /** Texto para leitor de tela depois de cada busca. */
  protected anuncio(lado: NumeroDoLado): string {
    if (!this.busca(lado).trim()) {
      return '';
    }
    const n = this.opcoes(lado).length;
    return n === 0 ? 'Nenhum parlamentar encontrado.' : n === 1 ? '1 parlamentar encontrado.' : `${n} parlamentares encontrados.`;
  }

  protected selecionar(lado: NumeroDoLado, item: EntradaIndice): void {
    this.buscas[lado].set('');
    const ids = { 1: this.idDoParametro(1), 2: this.idDoParametro(2), [lado]: item.id };
    this.navegar(ids[1], ids[2]);
    // O campo some quando o perfil entra: o foco vai ao campo do outro lado, se ainda vazio, ou ao lado a lado.
    afterNextRender(
      () => {
        const proximo = document.getElementById(`busca-pol-${this.outro(lado)}`) ?? document.getElementById('lado-a-lado');
        proximo?.focus();
      },
      { injector: this.injector },
    );
  }

  protected remover(lado: NumeroDoLado): void {
    this.navegar(lado === 1 ? '' : this.idDoParametro(1), lado === 2 ? '' : this.idDoParametro(2));
    afterNextRender(() => document.getElementById(`busca-pol-${lado}`)?.focus(), { injector: this.injector });
  }

  protected inverter(): void {
    this.navegar(this.idDoParametro(2), this.idDoParametro(1));
  }

  private navegar(p1: string, p2: string): void {
    this.router.navigate([], { queryParams: { p1: p1 || null, p2: p2 || null } });
  }
}
