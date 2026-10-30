# Política editorial

## Princípios

1. **Apartidário.** Todos os políticos recebem os mesmos campos, a mesma profundidade e a mesma linguagem.
2. **Factual.** O projeto não publica adjetivo, nota, ranking nem juízo sobre político.
3. **Fonte oficial.** Só entram fatos públicos de fontes do catálogo (`docs/FONTES.md`).
4. **Proveniência.** Todo registro publicado traz fonte, URL de origem e data de captura. Registro manual traz também snapshot arquivado.
5. **Ausência não é dado negativo.** Dado ausente na fonte aparece como "sem registro na fonte".
6. **Minimização.** Só entram dados ligados ao exercício da função pública.

## O que o projeto publica como texto

- Texto oficial da fonte: ementa, resumo oficial de discurso, título de evento, nome de órgão.
- Resumo factual de contribuidor, com até 300 caracteres, verificável na fonte e revisado antes do merge.

O projeto não publica texto gerado por IA sobre político.
O projeto não publica boato, notícia de imprensa, conteúdo de rede social de terceiros nem inferência.

## Dados que nunca entram

CPF, título de eleitor, endereço residencial, telefone, e-mail pessoal e dados de familiares.
A regra vale mesmo quando a fonte oficial expõe o dado.
CNPJ de fornecedor pode constar.

## Declaração do próprio político

Fonte de nível T1 (site do mandato, site do partido) aparece sempre com o rótulo "declaração do próprio político".

## Conflito entre fontes

1. T0 vence T1, e T1 vence T2.
2. Para o mesmo fato, o dado automático vence o manual.
3. Exceção: correção aprovada com fonte T0.

## Retificação

Qualquer pessoa pode pedir correção pelo mesmo fluxo de issue, inclusive a assessoria do político.
O pedido precisa indicar a fonte oficial.

## Base legal

- [Lei 12.527/2011](https://www.planalto.gov.br/ccivil_03/_ato2011-2014/2011/lei/l12527.htm) (Lei de Acesso à Informação): informação pública é a regra; o sigilo é a exceção.
- [Decreto 8.777/2016](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2016/decreto/d8777.htm): política de dados abertos do Poder Executivo federal.
- [Lei 13.709/2018](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm) (LGPD): o tratamento de dado pessoal tem finalidade definida e se limita ao mínimo necessário (art. 6º, I e III).

Dado de agente público ligado ao exercício da função é informação de interesse público.
Dado pessoal sem relação com a função fica fora, pela regra de minimização.
