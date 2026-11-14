# ADR-0063: Votações nominais e presença em plenário nas assembleias em SAPL

- **Data**: 2026-11-14
- **Status**: Aceito

## Contexto

As cinco assembleias estaduais conectadas a instâncias ativas do SAPL com registros na legislatura vigente (`ALPB`, `ALEAM`, `ALERO`, `ALEPI` e `ALEAC`) já publicavam mandatos, Mesa Diretora, filiações e proposições via `SaplMateria`, mas não coletavam votações nominais nem presença em sessões plenárias.

Verificações diretas nas cinco APIs REST (`https://sapl3.al.pb.leg.br/api`, `https://sapl.al.am.leg.br/api`, `https://sapl.al.ro.leg.br/api`, `https://sapl.al.pi.leg.br/api`, `https://sapl.al.ac.leg.br/api`) confirmaram:

1. `sessao/votoparlamentar/?data_hora__year={ano}&page_size=100` devolve os votos nominais da legislatura vigente com `data_hora`, `voto`, `votacao`, `parlamentar` e resumo da matéria e do resultado em `__str__`. Os campos `ip` e `user` aparecem na resposta bruta da API e devem ser removidos na coleta.
2. `votoparlamentar` lista apenas parlamentares com registro individual na votação nominal (`Sim`, `Não`, `Abstenção`, `Não Votou`), não permitindo inferir ausências sem voto em cada votação.
3. `sessao/sessaoplenaria/?data_inicio__year={ano}&page_size=100` identifica o menor identificador de sessão da legislatura vigente (`minSessaoId`), enquanto `sessao/sessaoplenariapresenca/?sessao_plenaria__gte={minSessaoId}&page_size=100` devolve as presenças registradas (armazenadas em `presencas/p{p}.json`) sem recorrer a consultas por sessão ou ao filtro `data_inicio__gte`, ignorado pelo backend Django do SAPL.
4. Em `ALEAL` os registros de sessão terminam em 2019, em `ALERR` há zero votações no SAPL e `ALETO` apresenta erro de verificação SSL na cadeia emissora do servidor. Essas três casas permanecem sem coleta de sessões neste momento.

## Decisão

1. **Auxiliar compartilhado `SaplSessao`**:
   - Coleta anualmente `sessao/tiposessaoplenaria/` (quando disponível), `sessao/sessaoplenaria/?data_inicio__year={ano}` e `sessao/votoparlamentar/?data_hora__year={ano}`, além de `sessao/sessaoplenariapresenca/?sessao_plenaria__gte={minSessaoId}` em `presencas/p{p}.json`.
   - Usa `Coleta.obterDerivado` em `votoparlamentar` para remover os campos `ip` e `user` antes mesmo de gravar a página no cache bruto em `data/raw/`.
   - Adota `Politica.porAnoSemanalEmLista` nas listagens anuais paginadas e `Politica.SEMANAL_EM_LISTA` nas presenças, evitando deslocamento de página durante a semana.
   - Na normalização, trunca instantes para `yyyy-MM-ddTHH:mm:ss` (sem fração nem fuso, conforme `comum.schema.json#/$defs/data_ou_instante`), extrai proposição e resultado de `__str__`, deduplica votos por `votacao_id` e presenças por sessão, resolve `partido_na_data` pelo histórico de filiações do parlamentar e publica `Participacao` com `sem_registro = null` e `Evento` com `presenca = "registrada"`.

2. **Fontes e orçamento no CI**:
   - Cada uma das cinco assembleias (`alpb`, `aleam`, `alero`, `alepi`, `aleac`) ganha duas fontes registradas em `contrib/fontes.yaml` e `docs/FONTES.md`: `{fonte}-votacoes` e `{fonte}-presenca`.
   - Em `.github/workflows/dados.yml`, o passo da `aleam` recebe `ETL_ORCAMENTO_MINUTOS: '25'` e `timeout-minutes: 30` para comportar o volume inicial superior a 63 mil registros de voto em frio antes do congelamento dos anos fechados em cache.

## Consequências

- Deputados estaduais da Paraíba, Amazonas, Rondônia, Piauí e Acre passam a contar com os blocos `votos`, `participacao_votacoes` e `eventos` no perfil publicado, habilitando também a comparação legislativa na página Lado a lado.
- Nenhum endereço IP vindo de `votoparlamentar` entra em `data/raw`, `data/normalizado` ou `data/publicado`.
- Anos anteriores ao exercício corrente (com exceção do trimestre inicial após o fechamento do ano) permanecem em cache permanente após a primeira coleta.
