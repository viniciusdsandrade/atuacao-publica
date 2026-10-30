import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

import { Detalhe, Execucao, Fonte, Indice, Perfil } from './modelo';

/** Lê os JSON publicados pelo ETL. Tudo vem do próprio site: nenhuma chamada a terceiros. */
@Injectable({ providedIn: 'root' })
export class Dados {
  private readonly http = inject(HttpClient);
  private readonly base = 'dados/';

  readonly indice$: Observable<Indice> = this.http.get<Indice>(this.base + 'index.json').pipe(shareReplay(1));
  readonly execucao$: Observable<Execucao> = this.http.get<Execucao>(this.base + '_meta/execucao.json').pipe(shareReplay(1));
  readonly fontes$: Observable<Fonte[]> = this.http.get<Fonte[]>(this.base + 'fontes.json').pipe(shareReplay(1));

  perfil(id: string): Observable<Perfil> {
    return this.http.get<Perfil>(this.base + 'politicos/' + encodeURIComponent(id) + '.json');
  }

  detalhe(caminho: string): Observable<Detalhe> {
    return this.http.get<Detalhe>(this.base + caminho);
  }

  documento(arquivo: string): Observable<string> {
    return this.http.get('docs/' + arquivo, { responseType: 'text' });
  }
}
