# ADR-0023: Décimo estado: ALES e Governo do Espírito Santo

- Status: aceita
- Data: 2026-11-06

## Contexto

A fase 2 do projeto estabelece a expansão interestadual com a cobertura das assembleias legislativas e executivos estaduais das diversas macrorregiões do país.
Após a consolidação de 100% da Região Sul (Paraná com ALEP, Rio Grande do Sul com ALRS e Santa Catarina com ALESC), a expansão no Nordeste (Bahia com ALBA e Ceará com ALECE), a estreia no Centro-Oeste (Goiás com ALEGO) e a presença em São Paulo (ALESP), Minas Gerais (ALMG) e Rio de Janeiro (ALERJ), a inclusão do Espírito Santo (ES) alcança o marco histórico de **100% de cobertura de toda a Região Sudeste do Brasil**, consolidando duas macrorregiões completas (Sul e Sudeste) e elevando o catálogo nacional para dez estados da federação.
O levantamento técnico das fontes oficiais do Espírito Santo validou:
1. A Assembleia Legislativa do Estado do Espírito Santo (ALES) disponibiliza portal institucional aberto (`https://www.al.es.gov.br/`), servido via HTTPS na porta 443 padrão, sem barreiras de autenticação nem bloqueios de rede em nuvem.
2. A listagem oficial dos parlamentares em exercício na 20ª Legislatura (2023–2027) é servida em `https://www.al.es.gov.br/Deputado/Lista`, contendo os 30 deputados estaduais estruturados com identificador numérico oficial (`idDeputado`) e nomes parlamentares.
3. As páginas de perfil individual (`https://www.al.es.gov.br/Deputado/{slug}`) são acessíveis via GET e apresentam fotografias oficiais em alta resolução (`/appdata/anexos_internet/portal/deputados/`), filiação partidária, nome civil completo, data e município de nascimento, e histórico parlamentar ("Sobre").
4. As 18 comissões permanentes da Casa são catalogadas em `https://www.al.es.gov.br/Comissao/ListaComissao?tipoComissao=permanente` e detalhadas em rotas dedicadas (`/Comissao/{slug}`), contendo Presidente, Vice-Presidente, Membros Efetivos e Membros Suplentes nominalmente associados aos parlamentares.
5. No Poder Executivo estadual, o Governador Renato Casagrande (PSB) exerce mandato consecutivo até 2027, coberto pelo arquivo histórico via repositório do TSE e com portais oficiais de transparência (`es-transparencia`, `https://transparencia.es.gov.br/`) e Diário Oficial (`es-dioe`, `https://ioes.dio.es.gov.br/`).

## Decisão

1. Expansão interestadual: inclusão do Espírito Santo (UF: `ES`, ente: `ES`) na esfera estadual do projeto, atingindo 100% de cobertura de todos os estados da Região Sudeste.
2. Identificador canônico do deputado estadual da ALES: `ales-{id}`, onde `{id}` é o identificador numérico oficial utilizado pela Assembleia (ex.: `ales-305`, `ales-27`, `ales-349`).
3. Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `alerj`, `alrs`, `alece`, `alesc`, `alego`, `ales`, `sapl-{ibge}`, `tse`.
4. Campo `ids_externos`: inclusão da chave `ales` em `schemas/politico.schema.json` com padrão `^[0-9]+$` e nos perfis publicados.
5. Catálogo de fontes: inclusão de `ales-portal`, `ales-deputados` e `ales-deputados-detalhes` em `contrib/fontes.yaml` (`es-transparencia` e `es-dioe` já constavam no catálogo).
6. Bloqueio de dados pessoais e privacidade:
   - Telefones, e-mails institucionais e contatos de gabinete são estritamente descartados durante a extração e normalização, em cumprimento rigoroso a `AGENTS.md` e verificação com `Guarda`.
   - Nomes civis, nomes de urna e dados biográficos são utilizados para vinculação em memória com as bases de candidaturas do TSE.
7. Vínculo determinístico com o TSE:
   - Inclusão do Espírito Santo (`ES`) no conjunto `UFS_ESTADUAIS` de `Tse.java` para normalização de candidaturas a deputado estadual das eleições de 2018, 2022 e 2026.
   - Vinculação por nome civil e nome de urna com as bases eleitorais do TSE.
   - Parlamentares da ALES com mandatos anteriores na Câmara Federal ou Senado são fundidos deterministicamente no identificador canônico de maior precedência com redirecionamento de alias.
8. Adaptador da ALES (`Ales.java`):
   - Coleta (`fetch`): armazena em `data/raw/ales/` a listagem `deputados.html`, os perfis individuais `perfis/{id}.html` e as 18 comissões permanentes `comissoes/{slug}.html`.
   - Normalização (`normalize`): gera `data/normalizado/ales/pessoas.json` e `blocos/{id}.json` populando mandatos, fotos oficiais, comissões permanentes em `orgaos_e_frentes` e links oficiais.
9. Executivo estadual:
   - O Governador do Espírito Santo é vinculado ao catálogo de portais oficiais (`es-transparencia` e `es-dioe`) e mantém seus mandatos e candidaturas do TSE.

## Consequências

- O projeto passa a cobrir dez estados brasileiros (SP, MG, PR, BA, RJ, RS, CE, SC, GO e ES), consolidando 100% de cobertura de duas regiões inteiras do país (Sul e Sudeste).
- A consistência do modelo de dados, schemas JSON, testes automatizados e regras de privacidade é mantida.
