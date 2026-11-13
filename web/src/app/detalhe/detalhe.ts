import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, combineLatest, map, of, startWith, switchMap } from 'rxjs';

import { Dados } from '../dados';
import { Icone } from '../icone/icone';
import { MemoriaLista } from '../lista/memoria-lista';
import { Detalhe as DetalheAnual, Registro } from '../modelo';
import { Tabela } from '../perfil/tabela';
import { chaveDeBusca, data, inteiro, notaDoBloco, rotuloCurtoBloco, tituloDoBloco } from '../rotulos';

type Carga = { estado: 'carregando' } | { estado: 'erro' } | { estado: 'pronto'; detalhe: DetalheAnual };

/** Junta o texto que a tela mostra de um registro, sem endereços. Datas entram também como dd/mm/aaaa. */
function coletar(valor: unknown, saida: string[]): void {
  if (valor === null || valor === undefined) {
    return;
  }
  if (typeof valor === 'string') {
    if (/^https?:\/\//.test(valor)) {
      return;
    }
    saida.push(valor);
    const iso = /^(\d{4})-(\d{2})-(\d{2})/.exec(valor);
    if (iso) {
      saida.push(`${iso[3]}/${iso[2]}/${iso[1]}`);
    }
  } else if (typeof valor === 'number') {
    saida.push(String(valor));
  } else if (Array.isArray(valor)) {
    valor.forEach((v) => coletar(v, saida));
  } else if (typeof valor === 'object') {
    for (const [chave, v] of Object.entries(valor)) {
      saida.push(chave);
      coletar(v, saida);
    }
  }
}

/** Todos os registros de um bloco em um ano (politicos/{id}/{bloco}-{ano}.json.gz). */
@Component({
  selector: 'app-detalhe',
  imports: [RouterLink, Tabela, Icone],
  templateUrl: './detalhe.html',
})
export class Detalhe {
  private readonly dados = inject(Dados);
  private readonly rota = inject(ActivatedRoute);
  private readonly tituloDaAba = inject(Title);
  protected readonly consulta = inject(MemoriaLista).consulta;

  protected readonly parametros = toSignal(
    this.rota.paramMap.pipe(map((p) => ({ id: p.get('id') ?? '', bloco: p.get('bloco') ?? '', ano: p.get('ano') ?? '' }))),
    { requireSync: true },
  );

  private readonly tentar$ = new BehaviorSubject<void>(undefined);

  private readonly carga = toSignal(
    combineLatest([this.rota.paramMap, this.tentar$]).pipe(
      switchMap(([p]) =>
        this.dados.detalhe(`politicos/${p.get('id')}/${p.get('bloco')}-${p.get('ano')}.json.gz`).pipe(
          map((detalhe): Carga => ({ estado: 'pronto', detalhe })),
          startWith<Carga>({ estado: 'carregando' }),
          catchError(() => of<Carga>({ estado: 'erro' })),
        ),
      ),
    ),
    { requireSync: true },
  );

  /** O perfil dá o nome, a esfera e os outros anos. Se não vier, a página funciona sem eles. */
  protected readonly perfil = toSignal(
    this.rota.paramMap.pipe(switchMap((p) => this.dados.perfil(p.get('id') ?? '').pipe(catchError(() => of(null))))),
    { initialValue: null },
  );

  protected readonly detalhe = computed(() => {
    const c = this.carga();
    return c.estado === 'pronto' ? c.detalhe : null;
  });
  protected readonly erro = computed(() => this.carga().estado === 'erro');

  protected readonly esfera = computed(() => this.perfil()?.esfera ?? null);
  protected readonly titulo = computed(() => tituloDoBloco(this.parametros().bloco, this.esfera()));
  protected readonly nota = computed(() => notaDoBloco(this.parametros().bloco, this.esfera()));
  protected readonly arquivos = computed(() => this.perfil()?.blocos[this.parametros().bloco]?.arquivos ?? []);

  protected readonly filtro = signal('');
  protected readonly anuncio = signal('');
  private readonly textos = new WeakMap<Registro, string>();

  /** Cada palavra do filtro precisa aparecer em algum campo do registro. */
  protected readonly filtrados = computed(() => {
    const todos = this.detalhe()?.registros ?? [];
    const termos = chaveDeBusca(this.filtro()).split(/\s+/).filter(Boolean);
    if (termos.length === 0) {
      return todos;
    }
    return todos.filter((r) => {
      const texto = this.textoDe(r);
      return termos.every((t) => texto.includes(t));
    });
  });

  protected readonly esqueletos = [1, 2, 3, 4, 5, 6];
  protected readonly data = data;
  protected readonly inteiro = inteiro;
  protected readonly rotuloCurtoBloco = rotuloCurtoBloco;

  constructor() {
    effect(() => {
      const par = this.parametros();
      const p = this.perfil();
      this.tituloDaAba.setTitle(
        `${rotuloCurtoBloco(par.bloco, p?.esfera)} ${par.ano}${p ? ' · ' + p.nome : ''} · Atuação Pública`,
      );
    });

    // Outro ano, outro filtro.
    effect(() => {
      this.parametros();
      untracked(() => this.filtro.set(''));
    });

    // Anuncia a contagem filtrada depois que a digitação para.
    effect((limpar) => {
      const filtrando = this.filtro().trim() !== '';
      const total = this.filtrados().length;
      const espera = setTimeout(
        () => this.anuncio.set(filtrando ? (total === 1 ? '1 registro encontrado.' : `${inteiro(total)} registros encontrados.`) : ''),
        600,
      );
      limpar(() => clearTimeout(espera));
    });
  }

  private textoDe(r: Registro): string {
    let texto = this.textos.get(r);
    if (texto === undefined) {
      const partes: string[] = [];
      coletar(r, partes);
      texto = chaveDeBusca(partes.join(' '));
      this.textos.set(r, texto);
    }
    return texto;
  }

  protected valor(evento: Event): string {
    return (evento.target as HTMLInputElement).value;
  }

  protected tentarDeNovo(): void {
    this.tentar$.next();
  }
}
