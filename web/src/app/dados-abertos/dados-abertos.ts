import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';

import { Dados, ItemManifestDump } from '../dados';
import { Icone } from '../icone/icone';
import { chaveDeBusca, data, inteiro } from '../rotulos';

@Component({
  selector: 'app-dados-abertos',
  imports: [RouterLink, Icone],
  templateUrl: './dados-abertos.html',
})
export class DadosAbertos {
  private readonly dados = inject(Dados);
  private readonly titulo = inject(Title);

  protected readonly busca = signal('');
  protected readonly copiado = signal(false);

  private readonly manifesto = toSignal(this.dados.manifestDumps$.pipe(catchError(() => of(null))));

  protected readonly dataGeracao = computed(() => {
    const m = this.manifesto();
    return m ? data(m.gerado_em) : '-';
  });

  protected readonly dumpConsolidado = computed(() => {
    return this.manifesto()?.arquivos.find((a) => a.arquivo === 'politicos.csv') ?? null;
  });

  protected readonly dumpFederal = computed(() => {
    return this.manifesto()?.arquivos.find((a) => a.arquivo === 'politicos-federal.csv') ?? null;
  });

  protected readonly dumpsUf = computed(() => {
    const arquivos = this.manifesto()?.arquivos ?? [];
    const q = chaveDeBusca(this.busca());
    return arquivos
      .filter((a) => a.arquivo.startsWith('politicos-') && a.arquivo !== 'politicos-federal.csv')
      .filter((a) => !q || chaveDeBusca(a.titulo).includes(q) || chaveDeBusca(a.arquivo).includes(q));
  });

  protected readonly inteiro = inteiro;

  constructor() {
    this.titulo.setTitle('Dados Abertos e Downloads · Atuação Pública');
  }

  protected tamanhoLegivel(bytes: number): string {
    if (bytes < 1024) {
      return bytes + ' B';
    }
    const kb = bytes / 1024;
    if (kb < 1024) {
      return kb.toFixed(1).replace('.', ',') + ' KB';
    }
    const mb = kb / 1024;
    return mb.toFixed(1).replace('.', ',') + ' MB';
  }

  protected enderecoDump(arquivo: string): string {
    return 'dados/dumps/' + arquivo;
  }

  protected async copiarCitacao(): Promise<void> {
    const texto =
      'Atuação Pública. Base de Dados Abertos Parlamentares e Governamentais do Brasil (CC BY 4.0). Disponível em: https://atuacao-publica.org.';
    try {
      await navigator.clipboard.writeText(texto);
      this.copiado.set(true);
      setTimeout(() => this.copiado.set(false), 2500);
    } catch {
      // Ignora falha de clipboard no ambiente restrito
    }
  }
}
