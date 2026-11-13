# ADR-0054: Biografia fica fora do projeto

- Status: aceita
- Data: 2026-11-13

## Contexto

Dezesseis adaptadores de assembleias extraíam a biografia do deputado da ficha da casa e gravavam o texto no bloco normalizado.
O modelo publicado (`schemas/politico.schema.json`) nunca teve campo de biografia, e nenhum perfil a exibia.
A política editorial lista os textos que o projeto publica: ementa, resumo oficial de discurso, título de evento e nome de órgão.
A biografia da casa é escrita pela assessoria. Costuma trazer adjetivo, naturalidade, formação e dado de família.
Só parte das casas publica biografia: publicá-la daria a alguns políticos um campo que os outros não têm.
O texto ficava no cache normalizado sem uso.

## Decisão

1. O projeto não extrai, não guarda e não publica biografia.
2. Os dezesseis adaptadores deixam de gravar o campo `biografia`: ALAP, ALEAC, ALEAL, ALEAM, ALEMA, ALEMS, ALEPE, ALEPI, ALERO, ALERR, ALESE, ALETO, ALMT, ALPB, ALRN e CLDF. A ALEPA extraía o texto sem gravar, e também deixa de extrair.
3. A ficha da casa continua coletada quando dá outro dado: nome civil para o vínculo com o TSE, foto, partido, redes e comissões.
4. Na ALRN, o nome civil só aparece dentro do texto biográfico. O adaptador lê o texto em memória, tira o nome civil e descarta o resto.
5. Na ALAP e na ALEMA, o campo vinha da profissão declarada na lista de deputados. A profissão também deixa de ser extraída.
6. A decisão foi do mantenedor, em 2026-11-13.

## Consequências

- Ensaio local de 2026-11-13 nas 17 casas, antes e depois: 464 blocos traziam biografia, e nenhum traz depois.
- `pessoas.json` e os nomes civis de cada casa ficaram iguais. O vínculo com o TSE não muda.
- O site não muda: o campo nunca foi publicado.
- Os testes de cada adaptador passam a conferir que o bloco não tem biografia.
- As descrições de fonte em `docs/FONTES.md` e `contrib/fontes.yaml` seguem citando biografia quando a página da casa a traz. Elas descrevem a fonte, não o que o projeto usa.
- Os ADRs de integração de cada casa (0037 a 0051) citam biografia higienizada. Vale este ADR.

## Alternativas consideradas

- Publicar a biografia como texto oficial da casa: descartada. Exige regra editorial para adjetivo e dado de família, e trata políticos de forma desigual.
- Manter a extração sem publicar: descartada. Guardar texto pessoal sem uso contraria a minimização.
