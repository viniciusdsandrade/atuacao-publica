import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith } from 'rxjs';

import { Dados, ManifestDumps } from '../dados';
import { Icone } from '../icone/icone';
import { NOMES_DAS_UFS, chaveDeBusca, data, inteiro } from '../rotulos';

type Carga = { estado: 'carregando' } | { estado: 'erro' } | { estado: 'pronto'; manifesto: ManifestDumps };

interface ArquivoDaUf {
  uf: string;
  nome: string;
  arquivo: string;
  total: number;
  tamanho: number;
}

/** Arquivos de dados abertos listados no manifesto que o ETL publica (dados/dumps/manifest.json). */
@Component({
  selector: 'app-dados-abertos',
  imports: [RouterLink, Icone],
  templateUrl: './dados-abertos.html',
})
export class DadosAbertos {
  private readonly dados = inject(Dados);

  protected readonly busca = signal('');
  protected readonly copia = signal<'feita' | 'falhou' | null>(null);

  protected readonly carga = toSignal(
    this.dados.manifestDumps$.pipe(
      map((manifesto): Carga => ({ estado: 'pronto', manifesto })),
      catchError(() => of<Carga>({ estado: 'erro' })),
      startWith<Carga>({ estado: 'carregando' }),
    ),
    { requireSync: true },
  );

  private readonly arquivos = computed(() => {
    const c = this.carga();
    return c.estado === 'pronto' ? c.manifesto.arquivos : [];
  });

  protected readonly geradoEm = computed(() => {
    const c = this.carga();
    return c.estado === 'pronto' ? data(c.manifesto.gerado_em) : '';
  });

  protected readonly consolidado = computed(() => this.arquivos().find((a) => a.arquivo === 'politicos.csv') ?? null);
  protected readonly federal = computed(() => this.arquivos().find((a) => a.arquivo === 'politicos-federal.csv') ?? null);

  /** Um CSV por UF (politicos-SP.csv), em ordem alfabética do nome do estado. */
  protected readonly porUf = computed((): ArquivoDaUf[] =>
    this.arquivos()
      .filter((a) => /^politicos-[a-z]{2}\.csv$/i.test(a.arquivo))
      .map((a) => {
        const uf = a.arquivo.slice('politicos-'.length, 'politicos-'.length + 2).toUpperCase();
        return { uf, nome: NOMES_DAS_UFS[uf] ?? uf, arquivo: a.arquivo, total: a.total_registros, tamanho: a.tamanho_bytes };
      })
      .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')),
  );

  protected readonly ufsFiltradas = computed(() => {
    const termo = chaveDeBusca(this.busca());
    return termo ? this.porUf().filter((u) => chaveDeBusca(`${u.nome} ${u.uf}`).includes(termo)) : this.porUf();
  });

  protected readonly anuncioUf = computed(() => {
    if (!this.busca().trim()) {
      return '';
    }
    const n = this.ufsFiltradas().length;
    return n === 0 ? 'Nenhum estado encontrado.' : n === 1 ? '1 estado encontrado.' : `${n} estados encontrados.`;
  });

  /** O endereço da citação é o que o visitante está usando: o código não fixa domínio. */
  protected readonly citacao = computed(() => {
    const endereco = typeof document === 'undefined' ? '' : document.baseURI;
    const hoje = new Intl.DateTimeFormat('pt-BR').format(new Date());
    return `Atuação Pública. Dados abertos sobre a atividade de políticos brasileiros. Licença CC BY 4.0. Disponível em: ${endereco}. Acesso em: ${hoje}.`;
  });

  protected readonly inteiro = inteiro;

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

  protected valor(evento: Event): string {
    return (evento.target as HTMLInputElement).value;
  }

  protected async copiarCitacao(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.citacao());
      this.copia.set('feita');
    } catch {
      // Navegador sem permissão de área de transferência: o texto fica à vista para copiar à mão.
      this.copia.set('falhou');
    }
  }
}
