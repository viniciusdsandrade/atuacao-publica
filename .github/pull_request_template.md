## O que muda

<!-- Uma ou duas frases. Cite a issue, se houver. -->

## Tipo

- [ ] Registro manual em `contrib/`
- [ ] Fonte nova ou revalidada (`contrib/fontes.yaml` e `docs/FONTES.md`)
- [ ] Correção de dado
- [ ] Código (ETL, site ou workflow)
- [ ] Documentação

## Checklist

- [ ] A fonte está em `contrib/fontes.yaml` e é T0, T1 ou T2.
- [ ] Todo registro manual tem `url_origem` e `snapshot_url` (web.archive.org ou archive.today).
- [ ] O texto é literal da fonte (`texto_oficial`) ou um resumo factual de até 300 caracteres, sem adjetivos.
- [ ] Não há CPF, título de eleitor, telefone, e-mail pessoal, endereço ou dado de familiar.
- [ ] `java -jar etl/target/etl.jar normalize contrib` passou localmente.
- [ ] `java -jar etl/target/etl.jar fetch contrib` passou localmente.
- [ ] Commits pequenos, mensagem em português no modo indicativo.
- [ ] Nenhum dado pessoal meu ou do meu ambiente (caminhos, nomes, e-mails) entrou nos arquivos.

## Revisão

A revisão humana é obrigatória antes do merge.
Quem revisa confere a fonte pelo snapshot e compara o texto publicado com a fonte.
