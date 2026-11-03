# ADR-0014: Executivo Estadual: gastos e agenda do Governador de São Paulo

- Status: aceita
- Data: 2026-11-03

## Contexto

A fase 2 do roadmap prevê a cobertura da esfera estadual, com assembleias legislativas e governos estaduais.
Após a conclusão da ingestão e publicação dos 153 deputados estaduais da Assembleia Legislativa do Estado de São Paulo (ALESP, ADR-0013), o próximo passo é estruturar os dados do Poder Executivo do Estado de São Paulo.

O projeto já contempla o mandato do Governador de São Paulo a partir de 2023 por meio das regras constitucionais de posse (`contrib/mandatos.yaml`, EC 111/2021) e dos dados de candidaturas do Tribunal Superior Eleitoral (TSE). Contudo, até o momento o cargo `governador` exibia apenas mandatos e candidaturas, enquanto `presidente` e `prefeito` contam com blocos de despesas do Executivo, agenda oficial e atos normativos.

O levantamento técnico das fontes oficiais do Estado de São Paulo identificou:
1. **SP-Agendas** (`https://spagendas.sp.gov.br`): plataforma oficial de integridade e transparência instituída pelo Decreto Estadual nº 69.475, gerida pela Secretaria de Gestão e Governo Digital (SGGD). Centraliza o registro diário de compromissos públicos, audiências e reuniões das autoridades do Executivo paulista, incluindo o Governador, Vice-Governador e Secretários de Estado.
2. **Portal da Transparência do Estado de São Paulo** (`https://www.transparencia.sp.gov.br`): publica despesas orçamentárias mensais por órgão da administração direta (Gabinete do Governador, Casa Civil e secretarias), classificadas por função, programa e elemento econômico.
3. **Diário Oficial do Estado de São Paulo** (`https://doe.sp.gov.br`): órgão oficial de divulgação dos atos normativos (leis sancionadas e decretos) do Governador.

## Decisão

1. **Blocos do Governador**:
   - Atualizar a configuração de blocos do cargo `governador` em `Publicador.java`, adicionando:
     - `despesas_executivo`: despesas agregadas por mês e categoria da administração estadual;
     - `agenda_oficial`: compromissos públicos diários do Governador;
     - `atos_normativos`: decretos e normas editadas pelo Executivo.
   - A lista de blocos do Governador passa a ser simétrica à do Presidente e do Prefeito: `mandatos`, `candidaturas`, `despesas_executivo`, `atos_normativos`, `agenda_oficial`, `posicionamentos_declarados`.

2. **Fontes Oficiais**:
   - `sp-agendas`: compromissos públicos da autoridade na plataforma SP-Agendas (nível T0);
   - `sp-transparencia`: despesas orçamentárias mensais no Portal da Transparência de SP (nível T0);
   - `sp-diario-oficial`: publicação oficial de atos normativos estaduais (nível T0).

3. **Metodologia de Despesas do Executivo Estadual**:
   - Segue a mesma diretriz dos ADR-0008 (Executivo municipal / TCE-SP) e ADR-0010 (Executivo federal / Portal da Transparência): valores consolidados por mês e categoria contábil.
   - Nenhum lançamento individual de favorecido pessoa física é publicado, eliminando qualquer risco de exposição de CPF.

4. **Metodologia de Agenda Oficial**:
   - Extrai data, horário de início, término, título e local de cada compromisso.
   - Aplica a sanitização estrita do projeto: remoção de e-mails, telefones ou CPFs porventura descritos no corpo do compromisso antes da gravação.

5. **Adaptador `ExecutivoSp.java`**:
   - Coleta os compromissos do Governador via SP-Agendas e as despesas do Gabinete do Governador / Casa Civil via Portal da Transparência.
   - Associa os blocos normalizados ao perfil do Governador de São Paulo no período de cobertura (mandato 2023 a 2027).

## Consequências

- O perfil do Governador de São Paulo atinge paridade informativa com o Presidente da República e com o Prefeito do piloto municipal.
- A integridade dos dados e o respeito à privacidade dos cidadãos permanecem garantidos pela agregação de despesas e filtros de texto.
- O padrão estabelecido servirá de referência para a expansão aos demais 26 governadores estaduais.
