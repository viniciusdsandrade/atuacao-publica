import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, defer, from, shareReplay, switchMap, tap } from 'rxjs';

import { Detalhe, Execucao, Fonte, Indice, Perfil } from './modelo';

export interface ItemManifestDump {
  arquivo: string;
  formato: string;
  titulo: string;
  descricao: string;
  total_registros: number;
  tamanho_bytes: number;
}

export interface ManifestDumps {
  gerado_em: string;
  total_arquivos: number;
  arquivos: ItemManifestDump[];
}

/** Na página inicial, public/arranque.js pede o índice antes de o site iniciar e deixa o pedido aqui. */
type JanelaComIndice = Window & { indiceAdiantado?: Promise<Indice> };

/** Lê os JSON publicados pelo ETL. Tudo vem do próprio site: nenhuma chamada a terceiros. */
@Injectable({ providedIn: 'root' })
export class Dados {
  private readonly http = inject(HttpClient);
  private readonly base = 'dados/';

  /** O índice, quando já foi baixado. Serve a quem aproveita o que está em memória sem pedir o arquivo (prévia do perfil). */
  readonly indiceEmMemoria = signal<Indice | null>(null);

  /**
   * O pedido adiantado vale uma vez. Sem ele, ou se ele falhar, o índice vem pelo caminho normal;
   * nova tentativa depois de erro também.
   */
  readonly indice$: Observable<Indice> = defer(() => {
    const pedido$ = this.http.get<Indice>(this.base + 'index.json');
    const janela: JanelaComIndice | null = typeof window === 'undefined' ? null : window;
    const adiantado = janela?.indiceAdiantado;
    if (!janela || !adiantado) {
      return pedido$;
    }
    janela.indiceAdiantado = undefined;
    return from(adiantado).pipe(catchError(() => pedido$));
  }).pipe(
    tap((indice) => this.indiceEmMemoria.set(indice)),
    shareReplay(1),
  );
  readonly execucao$: Observable<Execucao> = this.http.get<Execucao>(this.base + '_meta/execucao.json').pipe(shareReplay(1));
  readonly fontes$: Observable<Fonte[]> = this.http.get<Fonte[]>(this.base + 'fontes.json').pipe(shareReplay(1));
  readonly manifestDumps$: Observable<ManifestDumps> = this.http.get<ManifestDumps>(this.base + 'dumps/manifest.json').pipe(shareReplay(1));

  /** Perfis já abertos ficam em memória (os 6 mais recentes): voltar de um detalhe não baixa o perfil de novo. */
  private readonly perfis = new Map<string, Observable<Perfil>>();

  perfil(id: string): Observable<Perfil> {
    let perfil$ = this.perfis.get(id);
    if (perfil$) {
      this.perfis.delete(id);
    } else {
      perfil$ = this.http.get<Perfil>(this.base + 'politicos/' + encodeURIComponent(id) + '.json').pipe(shareReplay(1));
      if (this.perfis.size >= 6) {
        const maisAntigo = this.perfis.keys().next().value;
        if (maisAntigo !== undefined) {
          this.perfis.delete(maisAntigo);
        }
      }
    }
    this.perfis.set(id, perfil$);
    return perfil$;
  }

  /**
   * Pede o perfil antes do clique (ponteiro parado sobre o cartão). A resposta fica na memória dos perfis.
   * Falha aqui não aparece para ninguém: quem abrir o perfil pede de novo.
   */
  aquecerPerfil(id: string): void {
    this.perfil(id).subscribe({ error: () => undefined });
  }

  /** Endereço do JSON publicado de um perfil, para quem quer os dados abertos. */
  enderecoDoPerfil(id: string): string {
    return this.base + 'politicos/' + encodeURIComponent(id) + '.json';
  }

  /** Detalhe anual vem compactado com gzip (politicos/{id}/{bloco}-{ano}.json.gz). */
  detalhe(caminho: string): Observable<Detalhe> {
    return this.http
      .get(this.base + caminho, { responseType: 'arraybuffer' })
      .pipe(switchMap((bytes) => from(lerJson<Detalhe>(bytes))));
  }

  documento(arquivo: string): Observable<string> {
    return this.http.get('docs/' + arquivo, { responseType: 'text' });
  }
}

/** Descompacta com a API nativa do navegador. Se o servidor já entregou descompactado, lê direto. */
async function lerJson<T>(bytes: ArrayBuffer): Promise<T> {
  const cabecalho = new Uint8Array(bytes, 0, Math.min(2, bytes.byteLength));
  const gzip = cabecalho[0] === 0x1f && cabecalho[1] === 0x8b;
  const fluxo = gzip ? new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip')) : new Blob([bytes]).stream();
  return JSON.parse(await new Response(fluxo).text()) as T;
}
