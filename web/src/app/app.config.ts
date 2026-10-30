import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideRouter, withHashLocation } from '@angular/router';

import { routes } from './app.routes';

// Rotas com hash: o GitHub Pages serve arquivos estáticos e não reescreve URLs.
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withFetch()),
    provideRouter(routes, withHashLocation()),
  ],
};
