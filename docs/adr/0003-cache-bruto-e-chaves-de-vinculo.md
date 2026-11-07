# ADR-0003: Cache bruto e chaves de vínculo

- Status: aceita
- Data: 2026-10-30

## Contexto

Os arquivos oficiais trazem dado pessoal: CPF, título de eleitor, e-mail, data de nascimento.
O vínculo entre fontes (ADR-0002) precisa do CPF e da data de nascimento.
No arquivo de candidaturas de 2024, o TSE mascarou o CPF de todas as linhas e manteve o título de eleitor completo.
`fetch`, `normalize` e `publish` rodam como processos separados.
A proveniência exige hash do arquivo como foi baixado.

## Decisão

1. `data/raw` guarda o arquivo oficial exatamente como recebido, com `.meta.json` ao lado (URL, sha256, bytes, instante de captura).
2. `data/raw` nunca é versionado nem publicado. No GitHub Actions, fica só no cache do Actions, que não é baixável por terceiros. Nunca vai para artefato nem para Release.
3. `normalize` descarta os campos sensíveis. `data/normalizado` não contém CPF, título, e-mail, telefone, endereço nem data de nascimento.
4. `publish` lê as chaves de vínculo direto de `data/raw`, em memória, e não grava nem escreve em log.
5. O ETL nunca imprime valor de campo sensível. Mensagens de erro citam arquivo e campo, não o valor.
6. O título de eleitor também é chave de vínculo em memória. Ele leva à candidatura com CPF mascarado o CPF da mesma pessoa em outra eleição, só quando a correspondência é inequívoca: o título aparece com um único CPF, esse CPF com um único título, e a data de nascimento confere. Sem isso, vale o nome civil com a data de nascimento.

## Consequências

- O hash publicado em `_meta/{fonte}.json` confere com o arquivo oficial.
- O dado sensível existe só em cache transitório e na memória da execução.
- Apagar `data/raw` força nova coleta completa. O resultado publicado é o mesmo.
- Em 2026-11-06, o título ligou 714 candidaturas de 2024 a outra eleição da mesma pessoa. Outras 9 tinham título ambíguo e ficaram com o nome civil e a data de nascimento. No ensaio local do publish, um vereador eleito em 2024 ganhou a candidatura que faltava, e cinco perfis passaram a listar a candidatura de 2024 na ordem das eleições. Nenhuma fusão de perfis mudou.

## Alternativas consideradas

- Sanitizar o arquivo bruto antes de gravar: o hash deixaria de conferir com o arquivo oficial, e o vínculo por CPF ficaria impossível.
- Pseudonimizar o CPF com HMAC: continua sendo dado pessoal (LGPD) e exige guardar e rotacionar uma chave.
- Fazer o vínculo no `fetch`: acopla as fontes e obriga a baixar tudo de novo para refazer um vínculo.
