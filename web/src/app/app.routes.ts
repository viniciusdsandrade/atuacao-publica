import { Routes } from '@angular/router';

import { Lista } from './lista/lista';
import { Perfil } from './perfil/perfil';

// A lista e o perfil entram no arquivo inicial: são as duas portas de entrada do site.
// As outras páginas vêm em arquivos separados, baixados em segundo plano alguns segundos depois da primeira tela (app.config.ts).
export const routes: Routes = [
  { path: '', component: Lista, title: 'Políticos · Atuação Pública' },
  { path: 'politico/:id', component: Perfil, title: 'Perfil · Atuação Pública' },
  {
    path: 'politico/:id/:bloco/:ano',
    loadComponent: () => import('./detalhe/detalhe').then((m) => m.Detalhe),
    title: 'Registros do ano · Atuação Pública',
  },
  {
    path: 'comparar',
    loadComponent: () => import('./comparar/comparar').then((m) => m.Comparar),
    title: 'Lado a lado · Atuação Pública',
  },
  {
    path: 'dados-abertos',
    loadComponent: () => import('./dados-abertos/dados-abertos').then((m) => m.DadosAbertos),
    title: 'Dados abertos · Atuação Pública',
  },
  {
    path: 'estado',
    loadComponent: () => import('./estado/estado').then((m) => m.Estado),
    title: 'Estado das coletas · Atuação Pública',
  },
  {
    path: 'metodologia',
    loadComponent: () => import('./doc/doc').then((m) => m.Doc),
    data: { arquivo: 'METODOLOGIA.md' },
    title: 'Metodologia · Atuação Pública',
  },
  {
    path: 'fontes',
    loadComponent: () => import('./doc/doc').then((m) => m.Doc),
    data: { arquivo: 'FONTES.md' },
    title: 'Fontes · Atuação Pública',
  },
  {
    path: 'politica-editorial',
    loadComponent: () => import('./doc/doc').then((m) => m.Doc),
    data: { arquivo: 'POLITICA_EDITORIAL.md' },
    title: 'Política editorial · Atuação Pública',
  },
  {
    path: 'contribuir',
    loadComponent: () => import('./doc/doc').then((m) => m.Doc),
    data: { arquivo: 'CONTRIBUTING.md' },
    title: 'Contribuir · Atuação Pública',
  },
  { path: '**', redirectTo: '' },
];
