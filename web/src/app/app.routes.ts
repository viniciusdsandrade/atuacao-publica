import { Routes } from '@angular/router';

import { Comparar } from './comparar/comparar';
import { Detalhe } from './detalhe/detalhe';
import { Doc } from './doc/doc';
import { Estado } from './estado/estado';
import { Lista } from './lista/lista';
import { Perfil } from './perfil/perfil';

export const routes: Routes = [
  { path: '', component: Lista, title: 'Políticos · Atuação Pública' },
  { path: 'politico/:id', component: Perfil, title: 'Perfil · Atuação Pública' },
  { path: 'politico/:id/:bloco/:ano', component: Detalhe, title: 'Registros do ano · Atuação Pública' },
  { path: 'comparar', component: Comparar, title: 'Comparação Parlamentar · Atuação Pública' },
  { path: 'estado', component: Estado, title: 'Estado das coletas · Atuação Pública' },
  { path: 'metodologia', component: Doc, data: { arquivo: 'METODOLOGIA.md' }, title: 'Metodologia · Atuação Pública' },
  { path: 'fontes', component: Doc, data: { arquivo: 'FONTES.md' }, title: 'Fontes · Atuação Pública' },
  { path: 'politica-editorial', component: Doc, data: { arquivo: 'POLITICA_EDITORIAL.md' }, title: 'Política editorial · Atuação Pública' },
  { path: 'contribuir', component: Doc, data: { arquivo: 'CONTRIBUTING.md' }, title: 'Contribuir · Atuação Pública' },
  { path: '**', redirectTo: '' },
];
