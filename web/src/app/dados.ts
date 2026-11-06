import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, from, shareReplay, switchMap } from 'rxjs';

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

/** Lê os JSON publicados pelo ETL. Tudo vem do próprio site: nenhuma chamada a terceiros. */
@Injectable({ providedIn: 'root' })
export class Dados {
  private readonly http = inject(HttpClient);
  private readonly base = 'dados/';

  readonly indice$: Observable<Indice> = this.http.get<Indice>(this.base + 'index.json').pipe(shareReplay(1));
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
