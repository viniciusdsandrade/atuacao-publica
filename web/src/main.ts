import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { aplicarTemaNoDocumento, lerTemaSalvo } from './app/tema';

aplicarTemaNoDocumento(lerTemaSalvo());

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
