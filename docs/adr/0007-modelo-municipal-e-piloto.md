# ADR-0007: Modelo municipal e piloto da esfera local

- Status: aceita
- Data: 2026-10-31

## Contexto

As fases 0 a 8 da POC cobriram a esfera federal e executivos estaduais (Presidente, governadores, senadores e deputados federais).
O modelo canônico nasceu hierárquico (`esfera > ente > cargo > político`) para acomodar municípios sem refatoração estrutural (ADR-0002).
A fase 2 prevê um piloto municipal, escolhido em Campinas (SP), código IBGE 3509502.
O levantamento de fontes em `docs/FONTES.md` validou que:
1. A câmara de vereadores possui API REST aberta pelo SAPL (`campinas-camara-sapl`), sem autenticação, cobrindo legislaturas, parlamentares, mandatos (inclusive suplentes), presenças em plenário, votações nominais e matérias normativas.
2. A prefeitura não disponibiliza API aberta de dados nem agenda estruturada (o portal de transparência exige chave `x-api-key`).
3. As eleições municipais vêm do repositório de dados abertos do TSE (2020 e 2024), onde Campinas possui 33 vereadores e prefeito/vice-prefeito.

## Decisão

1. Cargos municipais adicionados ao modelo: `prefeito`, `vice_prefeito` e `vereador`.
2. O ente na esfera municipal é o código IBGE com 7 dígitos (ex.: `3509502`). O campo `uf` guarda a unidade da federação correspondente (`SP`).
3. Identificador canônico do vereador: `sapl-{ibge}-{id}`, onde `{ibge}` é o código de 7 dígitos do município e `{id}` é o identificador numérico da pessoa no SAPL da respectiva câmara municipal (ex.: `sapl-3509502-123`).
4. Precedência de identificadores: `cam`, `sen`, `sapl-{ibge}`, `tse`. Se a pessoa exercer mandatos em esferas diferentes, o identificador de nível mais abrangente ou anterior prevalece, mantendo estabilidade e gerando `aliases` para redirecionamento.
5. `ids_externos`: inclusão do campo `sapl` no perfil e no índice, guardando o identificador do parlamentar na casa legislativa municipal.
6. Adaptador do SAPL (`Sapl.java`):
   - Coleta (`fetch`): lê a API REST do SAPL a partir da raiz configurada por ente municipal, com respeito a taxa de requisições e paginação.
   - Normalização (`normalize`): gera `pessoas.json` e `blocos/{id}.json` compatíveis com o pipeline canônico de legisladores (`Vinculo.legisladores`).
   - Cobertura de mandatos: identifica titulares e suplentes a partir do endpoint `parlamentares/mandato/`, marcando situação e `em_exercicio`.
7. Vínculo com o TSE: vincula vereadores às candidaturas do TSE por nome civil normalizado, UF do município e histórico partidário/ano.
8. Executivo municipal: no piloto, a prefeitura entra por candidaturas do TSE (prefeito eleito) e registros manuais em `contrib/` com fontes T0 públicas, até abertura formal de API municipal.

## Consequências

- O modelo canônico e os esquemas JSON passam a aceitar cargos municipais e identificadores do SAPL.
- A interface web passa a exibir opções para filtrar vereadores e prefeitos.
- Adicionar novos municípios no futuro se torna uma questão de configurar o ente IBGE e a URL base da instalação do SAPL correspondente.
- A trava do publish e o histórico permanente (ADR-0006) continuam aplicando-se identicamente aos políticos municipais.

## Alternativas consideradas

- Prefixo genérico `sapl-{id}` sem código IBGE: colidiria entre câmaras municipais de diferentes cidades que usam SAPL independente.
- Adaptador único misturando prefeitura e câmara: descartado porque a câmara tem dados abertos padronizados via SAPL e a prefeitura tem arquitetura distinta.
