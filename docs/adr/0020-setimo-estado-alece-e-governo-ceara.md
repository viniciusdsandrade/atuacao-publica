# ADR-0020: Sétimo estado: ALECE e Governo do Estado do Ceará

- Status: aceita
- Data: 2026-11-05

## Contexto

A fase 2 do projeto estabelece a expansão interestadual com a cobertura das assembleias legislativas e executivos estaduais dos principais estados da federação.
Após a integração de São Paulo (ALESP, ADR-0013 e ADR-0014), Minas Gerais (ALMG, ADR-0015), Paraná (ALEP, ADR-0016), Bahia (ALBA, ADR-0017), Rio de Janeiro (ALERJ, ADR-0018) e Rio Grande do Sul (ALRS, ADR-0019), a inclusão do Ceará (CE) amplia a cobertura da Região Nordeste incorporando um dos polos legislativos e políticos mais representativos do país.
O levantamento técnico das fontes oficiais do Ceará validou:
1. A Assembleia Legislativa do Estado do Ceará (ALECE) disponibiliza portal institucional aberto (`https://www.al.ce.gov.br/`), acessível publicamente via HTTPS na porta padrão 443 sem barreiras de autenticação nem bloqueios de rede em nuvem.
2. A listagem de parlamentares é servida em `https://www.al.ce.gov.br/deputados`, contendo os cards dos deputados estaduais em exercício na 31ª Legislatura (2023–2027), com fotos oficiais (`https://www.al.ce.gov.br/image/...`), nomes parlamentares, partidos e slugs textuais únicos de perfil (ex.: `agenor-neto`, `alcides-fernandes`, `carmelo-bolsonaro`).
3. As páginas individuais de cada parlamentar (`https://www.al.ce.gov.br/deputados/{slug}`) fornecem biografias detalhadas com nome civil, data e cidade de nascimento, histórico parlamentar e atuações legislativas.
4. No Poder Executivo estadual, o Governador Elmano de Freitas (PT) exerce mandato até 2027, coberto pelo arquivo histórico via repositório do TSE e com portais oficiais de transparência (`ce-transparencia`, `https://transparencia.ce.gov.br/`) e Diário Oficial (`ce-diario-oficial`, `https://www.doe.seplag.ce.gov.br/`).

## Decisão

1. Expansão interestadual: inclusão do Ceará (UF: `CE`, ente: `CE`) na esfera estadual do projeto.
2. Identificador canônico do deputado estadual da ALECE: `alece-{slug}`, onde `{slug}` é o identificador textual hifenizado oficial utilizado nas rotas da Assembleia (ex.: `alece-agenor-neto`, `alece-alcides-fernandes`).
3. Precedência de identificadores: `cam`, `sen`, `alesp`, `almg`, `alep`, `alba`, `alerj`, `alrs`, `alece`, `sapl-{ibge}`, `tse`.
4. Campo `ids_externos`: inclusão da chave `alece` em `schemas/politico.schema.json` com padrão `^[a-z0-9-]+$` e nos perfis publicados.
5. Catálogo de fontes: inclusão de `alece-portal`, `alece-deputados` e `alece-deputados-detalhes` em `contrib/fontes.yaml` (`ce-transparencia` e `ce-diario-oficial` já constavam no catálogo).
6. Bloqueio de dados pessoais e privacidade:
   - Telefones, endereços de gabinete e e-mails institucionais são descartados durante a extração e normalização, em estrito cumprimento a `AGENTS.md` e verificação com `Guarda`.
   - Nomes civis, nomes de urna e biografias oficiais são utilizados para vinculação em memória com as bases de candidaturas do TSE.
7. Vínculo determinístico com o TSE:
   - Inclusão do Ceará (`CE`) no conjunto `UFS_ESTADUAIS` de `Tse.java` para normalização de candidaturas a deputado estadual das eleições de 2018, 2022 e 2026.
   - Vinculação por nome de urna, nome civil, casamento por palavras e remoção de títulos honoríficos e profissionais (ex.: "Dra.", "Ap.") com o TSE.
   - Parlamentares da ALECE com mandatos anteriores na Câmara Federal ou Senado são fundidos deterministicamente no identificador canônico de maior precedência com redirecionamento de alias.
8. Adaptador da ALECE (`Alece.java`):
   - Coleta (`fetch`): armazena em `data/raw/alece/` a listagem `deputados.html` e os perfis individuais `perfis/{slug}.html` via HTTPS na porta 443 padrão.
   - Normalização (`normalize`): gera `data/normalizado/alece/pessoas.json` e `blocos/{slug}.json`.
9. Executivo estadual:
   - O Governador do Ceará é vinculado ao catálogo de portais oficiais (`ce-transparencia` e `ce-diario-oficial`) e mantém seus mandatos e candidaturas do TSE.

## Consequências

- O projeto passa a cobrir sete unidades federativas: SP, MG, PR, BA, RJ, RS e CE, atingindo ampla diversidade regional (Sudeste, Sul e Nordeste).
- A comunicação na porta 443 nativa HTTPS da ALECE assegura execução contínua em ambientes de integração contínua (CI) e runners de cloud internacional sem dependência de portas não convencionais.
- A consistência do modelo de dados, schemas JSON, testes automatizados e regras de privacidade é mantida.
