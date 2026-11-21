# ADR-0077: Agenda do Governador do Espírito Santo

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

O levantamento do ADR-0075 achou no Espírito Santo uma agenda do governador em formato estruturado.
O portal do governo (`https://www.es.gov.br/agenda-das-autoridades`) publica, no menu "Agenda do Governador", um calendário público do Google Agenda.
O calendário se chama "Agenda do Governador", declara o fuso `America/Sao_Paulo` e tem eventos de 2018 em diante.
O calendário é do cargo: continua depois da posse de Ricardo Ferraço, em 2026-04-02 (ADR-0074).

Decretos e despesas do Executivo capixaba não têm fonte coletável validada (docs/FONTES.md).

## Decisão

1. Um adaptador `executivoes`, com a fonte T0 `es-agenda-governador`: o arquivo iCalendar do calendário, renovado a cada execução.
2. Entram os eventos desde 2022-01-01 (ADR-0009). Evento cancelado e "Feriado" ficam de fora.
3. A hora em UTC passa ao fuso que o próprio calendário declara. Sem fuso declarado, o compromisso fica sem hora. Evento de dia inteiro fica sem hora.
4. O texto do compromisso é o `SUMMARY`, e o local, o `LOCATION`, sem os escapes do iCalendar.
5. A agenda vai para o governador de cada período, como em Minas Gerais (ADR-0075).

## Consequências

- Renato Casagrande ganha 2.203 compromissos, de 2022-01-01 a 2026-04-01; Ricardo Ferraço, 421, desde a posse.
- O site mostra a nota do bloco da agenda do ES, e os blocos de atos e despesas dizem que o estado não tem fonte validada.

## Limitações

- O calendário fica no Google Agenda, fora do domínio do governo. A ligação com o governo é o link do portal oficial.
- Parte dos eventos é genérica ("Agendas Internas", "Despachos Administrativos").
- Não validado: se o Google Agenda mantém eventos apagados ou editados; a coleta lê o arquivo inteiro a cada execução.

## Alternativas consideradas

- **Página HTML da agenda no portal**: descartada. A página monta o calendário do Google por JavaScript.
- **Despesa pelo CKAN estadual**: adiada. Os arquivos trazem o favorecido e têm até 1,5 GB por ano.
