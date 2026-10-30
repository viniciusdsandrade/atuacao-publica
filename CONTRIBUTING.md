# Como contribuir

Há três caminhos:

- (a) corrigir um dado;
- (b) adicionar um registro manual;
- (c) adicionar ou revalidar uma fonte.

Todo caminho termina em revisão humana antes do merge.
Leia antes a `docs/POLITICA_EDITORIAL.md`.

## Preparar o ambiente (uma vez)

Requisitos: git, Java 25 ou mais recente e Node.js 24 (só para o site).

```bash
git clone <URL deste repositório>
cd <pasta do repositório>
git config core.hooksPath .githooks
cd etl && ./mvnw -q -B package && cd ..
```

O último comando gera `etl/target/etl.jar`.
O hook de pre-commit bloqueia commit com dado pessoal e roda o gitleaks, se instalado.

## (a) Corrigir um dado

1. Abra uma issue com o modelo **Correção de dado**.
2. Preencha: perfil, campo, valor atual, valor correto e URL da fonte oficial.
3. Aguarde a triagem. Há três desfechos:
   - erro do ETL: um mantenedor corrige o código e cita a issue no commit;
   - erro na fonte oficial: a issue registra o problema, e o dado segue a fonte;
   - fato novo de mandato (posse, renúncia, afastamento): siga o caminho (b) com o tipo `mandato` e fonte T0, como o diário oficial.

Dado automático vence registro manual para o mesmo fato. A exceção é correção aprovada com fonte T0.
A assessoria de um político usa o mesmo fluxo.

## (b) Adicionar um registro manual

Tipos aceitos:

| Tipo | Uso | Fonte aceita |
|---|---|---|
| `posicionamento_declarado` | posicionamento, proposta ou fala oficial do político | T1 (site do mandato ou do partido) ou T0 |
| `agenda_oficial` | compromisso da agenda oficial do Executivo | T0 (por exemplo, `planalto-agenda`) |
| `link_oficial` | site do mandato ou página oficial | T0 ou T1 |
| `mandato` | posse, renúncia, afastamento, sucessão | T0 (diário oficial, casa legislativa) |

### Passos

1. Ache o identificador do político na URL do perfil no site (por exemplo, `cam-204554`).
2. Arquive a página da fonte em <https://web.archive.org/save> ou em <https://archive.today>. Copie a URL do snapshot.
3. Crie ou edite o arquivo `contrib/{esfera}/{cargo}/{politico_id}/{tipo}.yaml`.
4. Adicione uma entrada no fim da lista.
5. Valide localmente:

   ```bash
   java -jar etl/target/etl.jar normalize contrib
   java -jar etl/target/etl.jar fetch contrib
   ```

   O primeiro comando confere schema, fonte permitida, tamanho do resumo e ausência de CPF, telefone e e-mail. Ele também avisa sobre palavras de juízo.
   O segundo confere se `url_origem` e `snapshot_url` respondem.
6. Faça commit só do arquivo: `git add contrib/.../{tipo}.yaml`.
7. Abra o pull request e preencha o checklist do modelo.

### Campos de toda entrada

| Campo | Regra |
|---|---|
| `tipo` | igual ao nome do arquivo |
| `data` | data do fato, `AAAA-MM-DD` |
| `texto_oficial` ou `resumo_factual` | `texto_oficial`: trecho literal da fonte. `resumo_factual`: até 300 caracteres, sem adjetivos, verificável na fonte |
| `fonte_id` | um `id` de `contrib/fontes.yaml` |
| `url_origem` | URL `https` da página original |
| `snapshot_url` | URL em `web.archive.org/web/...` ou `archive.today` (ou `archive.ph`) |
| `capturado_em` | instante da captura, UTC, `AAAA-MM-DDTHH:MM:SSZ` |
| `contribuidor` | seu login do GitHub |

Campos extras por tipo:

- `posicionamento_declarado`: `categoria` (`posicionamento`, `proposta` ou `fala_oficial`).
- `agenda_oficial`: `hora` (`HH:MM`, opcional) e `local` (opcional, como na fonte).
- `link_oficial`: `rotulo` (até 80 caracteres).
- `mandato`: `cargo`, `inicio`, `fim` (data ou `null`) e `situacao` (termo da fonte).

### Exemplo completo

Registro real em `contrib/federal/presidente/tse-280001607829/agenda_oficial.yaml`.
A fonte é o arquivo iCalendar (`vcal_view`) que o Planalto publica para cada compromisso da agenda.
Só o login de quem contribuiu muda no seu caso:

```yaml
- tipo: agenda_oficial
  data: 2023-01-18
  hora: null
  texto_oficial: "Ministro das Relações Exteriores, Mauro Vieira"
  local: "Palácio do Planalto"
  fonte_id: planalto-agenda
  url_origem: https://www.gov.br/planalto/pt-br/acompanhe-o-planalto/agenda-do-presidente-da-republica-lula/agenda-do-presidente-da-republica/2023-01-18/ministro-das-relacoes-exteriores-mauro-vieira/vcal_view
  snapshot_url: https://web.archive.org/web/20260404134504/https://www.gov.br/planalto/pt-br/acompanhe-o-planalto/agenda-do-presidente-da-republica-lula/agenda-do-presidente-da-republica/2023-01-18/ministro-das-relacoes-exteriores-mauro-vieira/vcal_view
  capturado_em: 2026-10-30T12:26:48Z
  contribuidor: seu-login
```

Por que cada campo tem esse valor:

- `texto_oficial`: campo `SUMMARY` do iCalendar, literal.
- `local`: campo `LOCATION`.
- `hora: null`: o iCalendar traz o instante em UTC, e o fim registrado é anterior ao início. Converter o horário seria inferência.
- `snapshot_url`: captura já existente no Wayback Machine. Para achar uma, consulte `https://archive.org/wayback/available?url=<url_origem>`.

## (c) Adicionar ou revalidar uma fonte

1. Abra uma issue com o modelo **Nova fonte**.
2. Edite `contrib/fontes.yaml`: adicione a entrada ou atualize `status` e `validado_em`.
3. Atualize a seção correspondente em `docs/FONTES.md`.
4. Rode `java -jar etl/target/etl.jar fetch fontes` para conferir se `url_base` responde.
5. Abra o pull request.

Critério de entrada: fonte oficial do Estado (T0), primeira parte (T1) ou facilitador com metodologia aberta e rastreável a T0 (T2).
Imprensa, blogs, redes sociais de terceiros, Wikipedia e rankings ficam fora.

## Código

- Siga `AGENTS.md`.
- Commits pequenos, um tema por commit, mensagem em português no modo indicativo.
- `git add` com caminhos explícitos.
- Teste só regra de dados (parse, normalização, deduplicação, bloqueio de dado pessoal).
- Boas primeiras tarefas: `docs/TAREFAS.md`.
