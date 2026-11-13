import { ApplicationConfig, Injectable, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withFetch } from '@angular/common/http';
import {
  PreloadingStrategy,
  Route,
  provideRouter,
  withHashLocation,
  withInMemoryScrolling,
  withPreloading,
} from '@angular/router';
import { EMPTY, Observable, switchMap, timer } from 'rxjs';

import { routes } from './app.routes';

/** Espera antes de baixar as páginas secundárias: a primeira tela já chegou e o aparelho está livre. */
const ESPERA_DA_PRECARGA_MS = 4000;

/**
 * Baixa os arquivos das páginas secundárias em segundo plano, depois da primeira tela.
 * Baixar logo no início disputa rede e processador com a lista ou o perfil que a pessoa veio ver.
 * No modo de economia de dados, cada página só baixa quando é aberta.
 */
@Injectable({ providedIn: 'root' })
export class PrecargaTardia implements PreloadingStrategy {
  preload(_rota: Route, carregar: () => Observable<unknown>): Observable<unknown> {
    const conexao = typeof navigator === 'undefined' ? undefined : (navigator as Navigator & { connection?: { saveData?: boolean } }).connection;
    if (conexao?.saveData) {
      return EMPTY;
    }
    return timer(ESPERA_DA_PRECARGA_MS).pipe(switchMap(() => carregar()));
  }
}

// Rotas com hash: o GitHub Pages serve arquivos estáticos e não reescreve URLs.
// Voltar restaura a rolagem; página nova começa no topo; fragmento rola até o bloco.
// As páginas fora da lista e do perfil vêm em arquivos separados, baixados em segundo plano (PrecargaTardia).
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withFetch()),
    provideRouter(
      routes,
      withHashLocation(),
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' }),
      withPreloading(PrecargaTardia),
    ),
  ],
};
