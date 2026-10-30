# ADR-0003: Cache bruto e chaves de vínculo

- Status: aceita
- Data: 2026-10-30

## Contexto

Os arquivos oficiais trazem dado pessoal: CPF, título de eleitor, e-mail, data de nascimento.
O vínculo entre fontes (ADR-0002) precisa do CPF e da data de nascimento.
`fetch`, `normalize` e `publish` rodam como processos separados.
A proveniência exige hash do arquivo como foi baixado.

## Decisão

1. `data/raw` guarda o arquivo oficial exatamente como recebido, com `.meta.json` ao lado (URL, sha256, bytes, instante de captura).
2. `data/raw` nunca é versionado nem publicado. No GitHub Actions, fica só no cache do Actions, que não é baixável por terceiros. Nunca vai para artefato nem para Release.
3. `normalize` descarta os campos sensíveis. `data/normalizado` não contém CPF, título, e-mail, telefone, endereço nem data de nascimento.
4. `publish` lê as chaves de vínculo direto de `data/raw`, em memória, e não grava nem escreve em log.
5. O ETL nunca imprime valor de campo sensível. Mensagens de erro citam arquivo e campo, não o valor.

## Consequências

- O hash publicado em `_meta/{fonte}.json` confere com o arquivo oficial.
- O dado sensível existe só em cache transitório e na memória da execução.
- Apagar `data/raw` força nova coleta completa. O resultado publicado é o mesmo.

## Alternativas consideradas

- Sanitizar o arquivo bruto antes de gravar: o hash deixaria de conferir com o arquivo oficial, e o vínculo por CPF ficaria impossível.
- Pseudonimizar o CPF com HMAC: continua sendo dado pessoal (LGPD) e exige guardar e rotacionar uma chave.
- Fazer o vínculo no `fetch`: acopla as fontes e obriga a baixar tudo de novo para refazer um vínculo.
