import { Injectable } from '@angular/core';

/** Guarda a última busca da lista, para o "Voltar à lista" do perfil devolver os mesmos filtros. */
@Injectable({ providedIn: 'root' })
export class MemoriaLista {
  consulta: Record<string, string> = {};
}
