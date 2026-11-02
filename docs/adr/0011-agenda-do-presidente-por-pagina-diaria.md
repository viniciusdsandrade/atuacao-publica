# ADR-0011: Agenda do Presidente da República pela página de cada dia

- Status: aceita
- Data: 2026-11-02

## Contexto

A agenda oficial do Presidente existe só em HTML no portal do Planalto, uma página por dia, com compromissos em uma lista fixa de classes (`item-compromisso`, `compromisso-inicio`, `compromisso-titulo`, `compromisso-participantes`, `compromisso-local`).
A decisão original (`docs/FONTES.md`, `planalto-agenda`) foi não raspar: a agenda entraria por contribuição manual com snapshot. Em um ano, entrou um compromisso.
Em 2026-11-02 a validação mostrou que a página do dia tem endereço estável (`{base}/{AAAA-MM-DD}`), responde 404 nos dias sem agenda publicada, e que a mesma estrutura vale desde 2022.
As páginas do mandato anterior saíram do ar com a troca de governo, mas o Internet Archive guarda capturas de cada dia, com o mesmo HTML.

## Decisão

1. A agenda passa a ser coletada pela página de cada dia do mandato, a partir do início da cobertura. Dia com 404 fica registrado como sem agenda publicada e não conta como falha.
2. O endereço base da agenda de cada mandato é dado, não código: campos `agenda` e `agenda_arquivo` na regra de `contrib/mandatos.yaml`. Com `agenda_arquivo`, a coleta pede a captura do Internet Archive mais próxima do meio-dia do próprio dia (`web.archive.org/web/{AAAAMMDD}120000id_/`), a 4 segundos por chamada.
3. Cada compromisso publica data, hora, título literal (`texto_oficial`), local e participantes, como na fonte. O registro automático tem `url_origem` na página do dia e, quando vem do arquivo, `snapshot_url` na captura.
4. O registro manual de agenda continua aceito, com snapshot e contribuidor. Compromisso manual igual a um coletado (data, hora e título) não é repetido: dado automático vence registro manual.
5. Os últimos 14 dias são coletados a cada execução; os demais vêm do cache.
6. Adaptador `Agenda.java`, fonte `agenda` na CLI.

## Consequências

- O Presidente passa a ter a agenda completa desde 2022, com proveniência por dia.
- A primeira carga faz cerca de 1.800 chamadas, 365 delas ao Internet Archive; no CI, pode levar duas execuções.
- Mudança no HTML do Planalto quebra o parser. O teste `AgendaTest` guarda a estrutura esperada; o cursor marca a fonte como parcial quando a página muda.
- O schema de `agenda_oficial` deixa de exigir snapshot e contribuidor: o registro manual continua exigindo os dois pelo schema de contribuição.

## Alternativas consideradas

- Manter só a contribuição manual: descartado. Um compromisso em um ano não é cobertura.
- iCalendar por compromisso (`vcal_view`): descartado. Exige uma chamada por compromisso, e a página do dia já traz tudo.
- Pedir a agenda de 2022 por LAI: descartado por ora. O arquivo público já tem as páginas.
