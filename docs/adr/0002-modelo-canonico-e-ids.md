# ADR-0002: Modelo canônico e identificadores

- Status: aceita
- Data: 2026-10-30

## Contexto

A mesma pessoa aparece em várias fontes: Câmara, Senado e TSE.
Cada fonte usa um identificador próprio.
O TSE cria um identificador novo (`SQ_CANDIDATO`) a cada eleição.
A URL de um perfil precisa ser estável entre execuções.
A LGPD proíbe guardar dados pessoais sem relação com a função pública.

## Decisão

### Hierarquia

O modelo segue `esfera > ente > cargo > político`.

| Campo | Valores |
|---|---|
| `esfera` | `federal`, `estadual`, `municipal` |
| `ente` | `BR` (federal), sigla da UF (estadual), código IBGE do município (municipal) |
| `cargo` | `presidente`, `governador`, `deputado_federal`, `senador`; novos cargos entram como novos valores |

Deputados federais e senadores pertencem à esfera federal (`ente = BR`).
O campo `uf` guarda a unidade da federação pela qual o parlamentar foi eleito.
Adicionar um município é adicionar um adaptador de fonte e um valor de `ente`.

### Identificador canônico

1. O identificador canônico deriva do primeiro identificador oficial conhecido.
2. Formato: `cam-{id da Câmara}`, `sen-{código do Senado}`, `tse-{SQ_CANDIDATO}`.
3. Quando a pessoa surge em mais de uma fonte na mesma execução, vale a ordem `cam`, `sen`, `tse`. Os códigos das casas legislativas não mudam entre eleições, e o do TSE muda.
4. O índice publicado guarda o mapa `ids_externos` de cada perfil.
5. Cada execução lê o índice anterior. Se um identificador externo já pertence a um perfil, o identificador canônico desse perfil se mantém.
6. Se duas entradas forem reconhecidas como a mesma pessoa, fica o identificador publicado primeiro. O outro entra em `aliases` no índice, e a SPA redireciona.

### Vínculo entre fontes

1. O vínculo é resolvido em memória, durante o ETL.
2. Câmara e TSE: CPF. O CPF vem de `/deputados/{id}` e dos arquivos do TSE.
3. Senado e TSE: nome civil normalizado, UF e data de nascimento.
4. TSE entre eleições: CPF.
5. A chave sensível (CPF, data de nascimento) nunca é gravada em disco nem escrita em log.
6. Só o mapa `ids_externos` é persistido.

### Registros

1. Todo registro publicado tem `fonte_id`, `url_origem` e `capturado_em`.
2. Registro manual tem também `snapshot_url` e `contribuidor`.
3. Tipos: `mandato`, `voto`, `participacao_votacao`, `despesa`, `proposicao`, `discurso`, `evento`, `orgao_ou_frente`, `candidatura`, `posicionamento_declarado`, `agenda_oficial`, `ato_normativo`.
4. Nomes de campo em português, `snake_case`.
5. Datas em ISO 8601. Instantes em UTC.
6. Valores em reais como número decimal.
7. Dado ausente na fonte é `null`. A interface mostra "sem registro na fonte". Zero só aparece quando a fonte informa zero.

### Arquivos publicados

| Caminho | Conteúdo |
|---|---|
| `index.json` | lista de busca: id, nome, cargo, esfera, ente, uf, partido, foto, aliases |
| `politicos/{id}.json` | perfil com blocos agregados, até cerca de 200 KB |
| `politicos/{id}/{tipo}-{ano}.json` | detalhe anual (despesas, votos, proposições, discursos, eventos, atos) |
| `fontes.json` | catálogo de fontes publicado a partir de `contrib/fontes.yaml` |
| `_meta/{fonte}.json` | cursor da fonte: período coberto, contagens, hash |
| `_meta/execucao.json` | relatório da última execução, com status por fonte |

## Consequências

- A URL de um perfil sobrevive a novas eleições e a mudanças de cargo.
- O vínculo depende de dados sensíveis que existem só durante a execução.
- Se o índice anterior se perder, a regra de precedência recalcula os mesmos identificadores para as mesmas entradas.
- Um vínculo errado se corrige por contribuição (caminho "corrigir um dado").

## Alternativas consideradas

- UUID aleatório por pessoa: exige registro central persistido e esconde a origem do dado.
- Hash do CPF: é dado pessoal derivado e permite reidentificação por força bruta.
- Nome normalizado como identificador: colide entre homônimos e muda com o nome de urna.
