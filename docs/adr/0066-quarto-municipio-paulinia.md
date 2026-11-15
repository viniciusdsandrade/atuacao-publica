# ADR-0066: Quarto município: Câmara Municipal e Prefeitura de Paulínia

- **Status**: Aceita
- **Data**: 2026-11-15

## Contexto

O modelo municipal cobre três municípios paulistas (ADR-0007, ADR-0027 e ADR-0057).
Este ADR acrescenta Paulínia (SP), código IBGE `3536505`, unidade eleitoral `68314` no TSE.

Fontes levantadas em 2026-11-15, todas sem autenticação:

| Fonte | O que traz | Formato | Coleta |
|---|---|---|---|
| Portal da Câmara (`www.camarapaulinia.sp.gov.br`) | Vereadores em exercício, legislaturas com nome civil, Mesa Diretora e comissões | HTML | sim |
| Sistema legislativo da Câmara (`paulinia.siscam.com.br`), SISCAM | Proposituras por vereador, sessões, votações e lista de frequência | HTML | não |
| TCE-SP (`tce-sp-transparencia-api`) | Despesa do município por mês e órgão | JSON | sim |
| TSE | Candidaturas, bens e prestação de contas de 2020 e 2024 | CSV em ZIP | sim |

O que pesou na decisão:

- **O `robots.txt` do SISCAM libera só buscadores nomeados e proíbe os demais agentes** (`User-agent: *`, `Disallow: /`). O do portal da Câmara só fecha a área administrativa.
- A página de cada vereador no portal é uma biografia, com família, formação e bairro onde mora. O projeto não guarda biografia (ADR-0054).
- A lista de vereadores do portal traz telefone e e-mail.
- As páginas do portal são texto editado à mão, sem contrato. A página de legislaturas separa nome civil e nome parlamentar ora com travessão, ora com vírgula.
- O portal não publica a data de posse de cada vereador. Só o período da legislatura.

Medidas de 2026-11-15:

- 17 vereadores em exercício na página do portal, na 15ª legislatura (01/01/2025 a 31/12/2028). O TSE tem 17 eleitos em 2024.
- Os 17 têm linha na página de legislaturas. 16 nomes civis são iguais ao do TSE. O outro casa pelo nome de urna.
- 4 cargos na Mesa e 15 em cinco comissões.
- A página de legislaturas lista 18 nomes na 14ª legislatura (2021 a 2024), para 15 eleitos em 2020. Não diz quem foi suplente nem as datas de cada um.

## Decisão

1. **Escopo**: vereadores em exercício na legislatura vigente, e prefeito e vice eleitos em 2024.
2. **Adaptador**: `Paulinia.java`, fonte `paulinia` na CLI.
3. **O SISCAM não é coletado.** O `robots.txt` dele recusa o projeto, e o projeto não contorna. A fonte fica no catálogo com status "bloqueado".
4. **Coleta**: quatro páginas do portal. Vereadores em exercício a cada execução. Legislaturas, Mesa e comissões, por semana.
5. **Só linhas de texto vão para o cache.** Marcação, telefone e e-mail saem antes. Linha com mais de 200 caracteres também sai: é o texto do regimento, na página da Mesa.
6. **As páginas individuais dos vereadores não são coletadas.**
7. **Em exercício** é quem está na página "Vereadores em Exercício". O nome e o partido saem como a página grafa.
8. **Identificador**: `portal-3536505-{nome parlamentar em minúsculas, sem acento e com hífen}`. Chave `paulinia` em `ids_externos`.
9. **Mandato**: um por vereador, na legislatura do cabeçalho da página. O início é o da legislatura para quem a página de legislaturas lista sem a marca "Suplente". Para os demais fica vazio. O texto do cabeçalho vai no campo `texto_oficial`.
10. **A condição do mandato** só sai quando a página diz "Suplente". O projeto não escreve "Titular".
11. **Mesa e comissões**: composição atual, sem data. Nome que não é de vereador em exercício fica fora e aparece na saída do `normalize`.
12. **Quem sai da lista fica no perfil**, como "Fora de exercício", pelo registro acumulado do ADR-0059.
13. **Vínculo com o TSE**, nesta ordem, dentro da unidade eleitoral `68314`:
    - nome civil da página de legislaturas igual ao nome civil do TSE;
    - nome parlamentar igual ao nome de urna;
    - única candidatura eleita em 2024, do mesmo partido, com as palavras do nome (ADR-0065).
14. **O nome civil não é publicado.** Fica num arquivo do normalizado, lido só pelo vínculo.
15. **Executivo**: prefeito e vice de 2024 entram pelo TSE, com regras de mandato do ente `3536505` em `contrib/mandatos.yaml`.
16. **Despesas do Executivo**: `despesas/paulinia/{ano}/{mes}` do TCE-SP, com as regras do ADR-0008.
17. **Na tela, o que fica no SISCAM sai como "Não coletado pelo site"**, e não como "Sem registro na fonte". Vale para proposições, votos, participação em votações e presença do vereador de Paulínia. A nota do bloco diz por quê.
18. **Página fora do formato esperado não vira dado.** Se os pares de partido e nome não fecham, nada é normalizado, e a publicação fica com a cópia anterior (ADR-0006).
19. **Ritmo**: no mínimo 1 segundo entre chamadas ao portal.

## Consequências

- O site passa a ter quatro municípios.
- O perfil do vereador de Paulínia tem mandato, Mesa, comissões, candidaturas e financiamento de campanha. Não tem proposições, votos nem presença.
- O perfil do prefeito tem mandato, candidaturas, financiamento e despesas da prefeitura.
- A tela ganha um segundo texto para bloco vazio. "Sem registro na fonte" segue valendo quando a fonte não tem o dado.
- A primeira coleta baixa 4 páginas.

## Limitações

- Sem proposições, votações e presença: a Câmara publica os três só no SISCAM.
- Sem despesas por vereador: o portal da transparência da Câmara não foi avaliado.
- Sem data de posse por vereador. O titular que tomar posse depois do início da legislatura sai com o início da legislatura.
- O identificador vem do nome parlamentar. Vereador que mudar de nome parlamentar ganha identificador novo.
- Dois vereadores com o mesmo nome parlamentar ficam fora, para um não levar os dados do outro.
- A 14ª legislatura fica fora: a página não tem datas por vereador nem marca os suplentes.
- O mandato do prefeito e do vice eleitos em 2020 fica fora: não há fonte oficial validada que registre o exercício dos quatro anos.
- Atos do prefeito: sem fonte validada. A legislação do município fica em serviço de terceiro.
- A leitura é de texto editado à mão. Mudança de formato deixa o município com a última publicação válida.

## Alternativas consideradas

- **Coletar o SISCAM mesmo assim**: descartada. O operador do sistema recusa coleta automática de quem não é buscador nomeado. O projeto não contorna recusa (como em `tse-divulgacand` e `alep-ressarcimento`).
- **Pedir os dados à Câmara**: fica para o mantenedor. Um pedido pela Lei de Acesso à Informação, ou a liberação do `robots.txt`, destrava proposituras, votações e presença.
- **Coletar as páginas individuais para o nome civil**: descartada. A página de legislaturas já dá o nome civil, sem a biografia.
- **Guardar a página inteira no cache**: descartada. Traz telefone e e-mail, que o projeto não usa.
- **Cobrir a 14ª legislatura com as datas da legislatura**: descartada. Atribuiria quatro anos de mandato a suplente.
- **Mostrar "Sem registro na fonte" nos blocos vazios**: descartada. A fonte tem o registro. Quem não coleta é o projeto.

## Atualização em 2026-11-15: primeira publicação

- A coleta rodou no runner do GitHub Actions no mesmo dia: 4 páginas, sem falha.
- O site publicou os 19 perfis, com candidaturas e financiamento de campanha.
