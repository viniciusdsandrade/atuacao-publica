# ADR-0018: Quinto estado: ALERJ e Governo do Rio de Janeiro

- Status: aceita
- Data: 2026-11-05

## Contexto

A fase 2 do projeto estabelece a expansão interestadual com a cobertura das assembleias legislativas e executivos estaduais dos principais estados da federação.
Após a integração de São Paulo (ALESP, ADR-0013 e ADR-0014), Minas Gerais (ALMG, ADR-0015), Paraná (ALEP, ADR-0016) e Bahia (ALBA, ADR-0017), a inclusão do Rio de Janeiro (RJ) complementa o núcleo central do Sudeste e incorpora o terceiro maior colégio eleitoral e segunda maior economia do país.
O levantamento técnico das fontes oficiais do Rio de Janeiro validou:
1. A Assembleia Legislativa do Estado do Rio de Janeiro (ALERJ) disponibiliza portal institucional aberto (`https://www.alerj.rj.gov.br/`), acessível publicamente via HTTP/2 sem barreiras de autenticação nem chave de API.
2. A listagem de deputados estaduais em exercício é servida em `https://www.alerj.rj.gov.br/Deputados/QuemSao`, listando com exatidão os 70 deputados da 13ª legislatura (2023–2027) com identificadores numéricos oficiais inteiros (ex.: `505`, `420`, `346`), fotos oficiais no repositório (`/Uploads/PerfilDeputado/Imagem/...`), siglas partidárias e nomes parlamentares.
3. O perfil individual de cada parlamentar em `https://www.alerj.rj.gov.br/Deputados/PerfilDeputado/{id}?Legislatura=20` fornece a data de posse formal na 13ª Legislatura (ex.: `11/01/2023`, `01/02/2023`, `08/01/2025`), mandatos anteriores e biografia oficial contendo trajetória e atuação.
4. No Poder Executivo estadual, o Governador Cláudio Castro (PL) exerce mandato até 2027, coberto pelo arquivo histórico via repositório do TSE e com portais oficiais de transparência (`rj-transparencia`, `https://www.transparencia.rj.gov.br/`) e Diário Oficial (`rj-diario-oficial`, `https://www.ioerj.com.br/`).

## Decisão

1. Expansão interestadual: inclusão do Rio de Janeiro (UF: `RJ`, ente: `RJ`) na esfera estadual do projeto.
2. Identificador canônico do deputado estadual da ALERJ: `alerj-{id}`, onde `{id}` é o identificador numérico oficial utilizado nas rotas da Assembleia (ex.: `alerj-505` para Alan Lopes, `alerj-420` para Alexandre Knoploch).
3. Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `alerj`, `sapl-{ibge}`, `tse`.
4. Campo `ids_externos`: inclusão da chave `alerj` em `schemas/politico.schema.json` com padrão `^[0-9]+$` e nos perfis publicados.
5. Catálogo de fontes: inclusão de `alerj-portal`, `alerj-deputados` e `alerj-deputados-detalhes` em `contrib/fontes.yaml` (`rj-transparencia` e `rj-diario-oficial` já constavam no catálogo).
6. Bloqueio de dados pessoais e privacidade:
   - Telefones, endereços de gabinete, dados familiares e e-mails são descartados durante a extração, em estrito cumprimento a `AGENTS.md` e verificação com `Guarda`.
   - Nomes civis e termos biográficos são utilizados para vinculação em memória com as bases de candidaturas do TSE.
7. Vínculo determinístico com o TSE:
   - Inclusão do Rio de Janeiro (`RJ`) no conjunto `UFS_ESTADUAIS` de `Tse.java` para normalização de candidaturas a deputado estadual das eleições de 2018, 2022 e 2026.
   - Vinculação por nome de urna, nome civil e casamento por palavras com o TSE.
   - Parlamentares da ALERJ com mandatos anteriores na Câmara Federal ou Senado são fundidos deterministicamente no identificador canônico de maior precedência com redirecionamento de alias.
8. Adaptador da ALERJ (`Alerj.java`):
   - Coleta (`fetch`): armazena em `data/raw/alerj/` a listagem `deputados.html` e os perfis individuais `perfis/{id}.html`.
   - Normalização (`normalize`): gera `data/normalizado/alerj/pessoas.json` e `blocos/{id}.json`.
9. Executivo estadual:
   - O Governador do Rio de Janeiro é vinculado ao catálogo de portais oficiais (`rj-transparencia` e `rj-diario-oficial`) e mantém seus mandatos e candidaturas do TSE.

## Consequências

- O projeto passa a cobrir cinco unidades federativas essenciais: SP, MG, PR, BA e RJ, consolidando a cobertura do Sudeste, Sul e Nordeste.
- A consistência do modelo de dados, schemas JSON, testes automatizados e regras de privacidade é mantida.
