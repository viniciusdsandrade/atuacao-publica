# ADR-0075: Agenda e decretos do Governador de Minas Gerais

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

Só o Presidente e o Governador de São Paulo tinham agenda, atos ou despesas (ADR-0010, ADR-0011 e ADR-0014).
Os outros 26 governos tinham os três blocos vazios.
Os ADRs 0015 a 0051 só catalogaram o portal da transparência e o diário oficial de cada estado.

Em 2026-11-21, um levantamento testou as fontes do Executivo de MG, RJ, BA, PR, RS, PE, CE, GO, SC e ES.
Minas Gerais tem as duas fontes mais fáceis de coletar:

- a agenda do governador em JSON, com filtro de datas, desde 2015;
- os decretos numerados em JSON, por tipo e ano, nos dados abertos da ALMG.

Na maioria dos outros estados, a agenda não existe em formato estruturado.

## Decisão

1. Um adaptador `executivomg`, com duas fontes T0:
   - `mg-agenda-governador`: a API da Agência Minas, um mês por consulta, desde 2022-01;
   - `almg-legislacao`: os decretos numerados de origem Executivo, um ano por consulta, desde 2022.
2. Os registros têm o mesmo formato dos de São Paulo: compromisso com data, hora, texto, local e ente; ato com tipo, número, data, nome e ementa.
3. O texto do compromisso é o título e, quando há, a descrição publicada no corpo, separados por ponto. O local é o parágrafo com município e UF ou o que segue o rótulo "Local:".
4. A hora é a publicada. A API marca a hora com Z, mas é hora local: a posse de 2023-01-01 sai às 09:00.
5. Os dados do governo estadual vão para os perfis pelo período de cada mandato de governador, depois das contribuições.
   Motivo: a sucessão de 2026 (ADR-0074) entra por contribuição. Ligados antes, os dados de 2026-03-22 a 2026-04-04 iriam para o titular que já tinha saído.
6. A coleta entra na semente da coleta local (ADR-0034): a ALMG recusa conexão de fora do país. No CI, o passo `fetch executivomg` pula quando a semente traz o cache. O script que restaura a semente passa a aceitar o caminho no mesmo commit que o script de envio.
7. A tabela de fontes do governo estadual passa a ser por UF. Só a coleta da própria UF marca os blocos do governador como desatualizados. Antes, a coleta de SP marcava os blocos vazios dos outros 26 governadores.

## Consequências

- O perfil de Romeu Zema ganha 3.058 compromissos e 852 decretos, de 2022-01-01 a 2026-03-21.
- O de Mateus Simões ganha 472 compromissos e 125 decretos desde a posse, em 2026-03-22.
- O padrão serve aos próximos estados: um adaptador por UF e uma linha na tabela de fontes por UF.

## Limitações

- A despesa do Executivo mineiro não entra. O pacote agregado do CKAN estadual está vazio, e o pacote por empenho traz o credor.
- O decreto sem número (DNE), em geral ato de pessoal, fica de fora.
- A agenda do vice-governador (`/api/viceGovernorSchedule`) não é coletada.
- Não validado: se a API da Agência Minas responde ao runner do GitHub, fora do Brasil. Com a semente, o CI não precisa dela.
- Não validado: se a API da Agência Minas mantém compromissos editados depois do mês. A coleta renova o mês corrente e o anterior.

## Alternativas consideradas

- **Começar por Goiás**: a despesa mensal de GO já vem agregada e sem favorecido, mas o estado não publica a agenda. Fica para a etapa seguinte.
- **Decretos pelo Jornal Minas Gerais**: descartada. As edições só saem por uma API com token.
