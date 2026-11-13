# ADR-0057: Terceiro município: Câmara Municipal e Prefeitura de Caraguatatuba

- **Status**: Aceita
- **Data**: 2026-11-13

## Contexto

O modelo municipal cobre dois municípios paulistas: o piloto, com SAPL (ADR-0007), e a capital, com serviço próprio (ADR-0027).
Este ADR acrescenta Caraguatatuba (SP), código IBGE `3510500`, unidade eleitoral `63118` no TSE.

Fontes levantadas em 2026-11-13, todas sem autenticação:

| Fonte | O que traz | Formato |
|---|---|---|
| Sistema legislativo da Câmara (`sistema.camaracaragua.sp.gov.br`), SAGL da OpenLegis | Vereadores por legislatura, nome civil, filiações, mandatos, Mesa Diretora, comissões até 2022 e matérias por autor | HTML |
| Portal da Câmara (`caraguatatuba.sp.leg.br`) | Composição atual das comissões e lista de presença de cada sessão | HTML e JSON |
| Portal da Transparência do município (`portaldatransparencia.caraguatatuba.sp.gov.br`) | Diárias da Câmara, com beneficiário, cargo, valor e datas | JSON, com documentação Swagger |
| TCE-SP (`tce-sp-transparencia-api`) | Despesa do município por mês e órgão | JSON |
| TSE | Candidaturas, bens e prestação de contas de 2020 e 2024 | CSV em ZIP |

A Câmara não usa SAPL.
O `robots.txt` do portal libera tudo.
Os termos de uso do portal, de 2026-07-06, permitem "acessar e consultar conteúdos para fins informativos, educacionais, jornalísticos e de transparência".

Medidas de 2026-11-13:

- 25 vereadores nas legislaturas 18ª (2021 a 2024) e 19ª (2025 a 2028). Os 25 casam com candidatura do TSE no município pelo nome civil.
- Matérias no sistema legislativo: 496 em 2022, 691 em 2023, 416 em 2024, 1.292 em 2025 e 1.066 em 2026.
- 86 listas de presença, de 2025 e 2026. 80 têm marcação, sempre dos mesmos 15 vereadores: 1.178 presenças, 10 licenças, 11 faltas justificadas e 1 falta.
- Diárias da Câmara: 96 em 2025 e 183 em 2026. 40 casam com vereador pelo nome idêntico.
- A galeria de ex-prefeitos e a página de legislaturas do portal registram o prefeito eleito em 2020 de 01/01/2021 a 31/12/2024 e o vice de 2021 a 2024.

Dois defeitos das fontes pesaram na decisão:

- A situação de tramitação do sistema legislativo não é mantida. 69 dos 70 projetos de lei de 2024 e 577 dos 580 requerimentos de 2025 constam "em tramitação".
- Cada sistema grafa o nome civil de um jeito. Um vereador aparece com uma letra de diferença no portal, e outra aparece com um sobrenome a mais nas diárias.

## Decisão

1. **Escopo**: vereadores com mandato a partir de 2022-01-01, nas duas legislaturas, e prefeito e vice eleitos em 2020 e em 2024.
2. **Adaptador**: `Caraguatatuba.java`, fonte `caraguatatuba` na CLI.
3. **Identificador**: `sagl-3510500-{cod_parlamentar}`, no padrão do ADR-0007 (sistema, código IBGE e identificador). Chave `caraguatatuba` em `ids_externos`, regex `^[0-9]+$`.
4. **Vereador em exercício**: quem o sistema legislativo marca "Em exercício" e tem mandato vigente na data.
5. **Partido**: o da filiação vigente na data de referência. A sigla sai como a fonte grafa, sem os dígitos que o sistema cola no fim ("AGIR36" vira "AGIR").
6. **Mandatos, Mesa e comissões**: da ficha do vereador no sistema legislativo, com as datas da fonte. O órgão sai como "Mesa Diretora", que é como a ficha o chama. As comissões de 2025 em diante vêm da página "Composição atual" do portal, sem data, porque o sistema legislativo parou em 2022.
7. **Proposições**: pesquisa de matérias por autor e ano, 200 por página. Entram tipo, número, ano, ementa, data de apresentação e o link do texto integral.
8. **A situação de tramitação não é publicada.** O campo fica vazio.
9. **Presença**: uma lista por sessão. `P` vira presença registrada, `FJ` vira ausência justificada e `F` vira falta. `L` (licença) não vira evento, como as outras situações na ALBA. Lista sem marcação e sessão futura ficam fora. O link de cada registro leva à mesma lista em PDF, que é a versão que a Câmara oferece para impressão.
10. **Diárias**: bloco de despesas, categoria "Diária", com o valor, a data de concessão e o número do empenho. Destino e motivo não entram: o schema de despesa não tem campo para eles. Só entra a diária com data dentro de um mandato do vereador: quem foi vereador e virou servidor não leva diária de servidor para o perfil.
11. **Só diária de vereador vai para o cache.** A derivação guarda o código do vereador, valor, datas e empenho. Nome de servidor e matrícula não são gravados.
12. **Ligação por nome entre os sistemas**, nesta ordem:
    - nome idêntico, sem acento e sem caixa, ao nome civil ou ao nome parlamentar da ficha;
    - nas listas de presença e na composição das comissões, que só têm vereadores: uma única palavra diferente, que não seja a primeira, com no máximo duas letras de diferença, e um único vereador possível. A grafia do portal passa a valer como nome do vereador;
    - nas diárias, com o cargo "vereador" ou "presidente" na fonte: o nome da ficha seguido de mais sobrenomes, ou o inverso, e um único vereador possível. "Júnior", "Filho", "Neto" e "Sobrinho" não contam como sobrenome a mais.
    Sem ligação, o registro fica fora e é contado na saída do `normalize`.
13. **Vínculo com o TSE**: nome civil da ficha, normalizado, dentro da unidade eleitoral `63118`. Sem ele, o nome parlamentar contra o nome de urna.
14. **Executivo**: prefeito e vice de 2020 e de 2024 entram pelo TSE, com regras de mandato do ente `3510500` em `contrib/mandatos.yaml`.
15. **Despesas do Executivo**: `despesas/caraguatatuba/{ano}/{mes}` do TCE-SP, com as regras do ADR-0008.
16. **Privacidade**: telefone, e-mail e biografia da ficha são retirados antes de o arquivo ir para o cache. A lista de vereadores do portal, que traz telefone e e-mail, não é coletada.
17. **Ritmo**: no mínimo 1 segundo entre chamadas a cada servidor da Câmara e do município.

### Recorte da prestação de contas

O recorte da prestação de contas do TSE (ADR-0032) ficava no cache para sempre nas eleições fechadas.
Quem entrava na cobertura depois ficava sem o bloco de financiamento.
Em 2026-11-13, deputados estaduais do AP, de RR e de MT tinham candidaturas e nenhum registro de financiamento.

O `fetch tse` passa a gravar, por eleição, a assinatura das candidaturas cobertas.
Quando a assinatura muda, o recorte daquela eleição é refeito.
Sem a marca, o recorte é refeito uma vez.

## Consequências

- O site passa a ter três municípios.
- O perfil do vereador de Caraguatatuba tem mandatos, Mesa, comissões, proposições, presença em sessão, diárias, candidaturas e financiamento de campanha.
- O perfil do prefeito tem mandato, candidaturas, financiamento e despesas da prefeitura.
- A primeira execução do `fetch tse` depois desta mudança refaz o recorte das cinco eleições: cerca de 530 MB lidos por Range.
- A primeira carga da Câmara baixou 199 arquivos. As seguintes renovam o ano aberto e as sessões recentes, em rodízio semanal.
- A nota do bloco de eventos no site passa a citar a Câmara Municipal de Caraguatatuba.
- Na esfera municipal, o bloco de despesas do vereador passa a se chamar "Despesas informadas pela câmara municipal", com nota que diz o que cada câmara publica. Nenhuma das três câmaras tem cota parlamentar como a federal.
- O bloco de proposições dos vereadores de Caraguatatuba ganha nota que explica a situação vazia.
- A nota dos atos normativos municipais passa a dizer, fora do piloto, que não há fonte validada.
- O selo de liderança do perfil passa a quebrar a linha. Colegiado sem sigla entra pelo nome inteiro, e o selo alargava a página no celular.

## Limitações

- Votações nominais: sem fonte estruturada. O sistema legislativo tem uma sessão cadastrada, de 2023.
- Atos do prefeito: sem fonte validada. O sistema legislativo não tem norma de 2025 nem de 2026, e a legislação compilada fica em serviço de terceiro.
- Agenda do prefeito: é um calendário do Google embutido na página da Prefeitura. O endereço do calendário tem e-mail, que o projeto não publica.
- Presença: só 2025 e 2026. Duas sessões passadas estavam sem marcação em 2026-11-13.
- Diárias: em 2025 a fonte deixa o cargo em branco em 47 de 96 registros. Nesses, só vale o nome idêntico.
- Uma diária de 2026 tem cargo "vereadora" e nome que não é de vereador. Fica fora.
- Comissões de 2023 e 2024: sem registro em nenhuma das duas fontes.
- O bloco de despesas traz só diárias. Não há fonte validada de outras despesas por vereador.
- O sistema legislativo não aceita conexão do runner do GitHub Actions. A casa vem da coleta local cifrada (ADR-0034), e os dados só atualizam quando a coleta local roda.
- A leitura é de HTML sem contrato. Mudança de marcação no sistema legislativo ou no portal quebra a coleta, e o perfil fica com a última cópia válida.

## Atualização em 2026-11-13: coleta local cifrada

- No primeiro `dados` com a casa, às 22:04 UTC, o `fetch caraguatatuba` falhou no runner: cinco tentativas de conexão com `sistema.camaracaragua.sp.gov.br`, todas com tempo esgotado. Da rede do mantenedor, a mesma coleta tinha baixado 199 arquivos sem falha uma hora antes.
- Prefeito e vice entraram no site nas duas execuções anteriores do mesmo dia, com candidaturas, financiamento e despesas da prefeitura: o TSE e o TCE-SP respondem ao runner.
- Os vereadores passam a vir da coleta local cifrada (ADR-0034). O passo `fetch caraguatatuba` continua no workflow e só roda sem a semente.
- O portal da Câmara tem um proxy público para o sistema legislativo (`proxy-server.caraguatatuba.sp.leg.br`), usado para embutir a pesquisa de matérias. Não foi usado: reescreve as páginas, corta em 30 segundos e não foi testado a partir do runner.

## Alternativas consideradas

- **Exportação RSS da pesquisa de matérias**: descartada. Só responde com o tipo de matéria informado, o que multiplicaria as chamadas por 14.
- **Publicar a situação de tramitação**: descartada. A fonte não a mantém, e o perfil afirmaria "em tramitação" para lei já publicada.
- **Ligar nomes por semelhança em qualquer contexto**: descartada. Irmãos com os mesmos sobrenomes e primeiro nome parecido seriam confundidos nas diárias.
- **Guardar a resposta inteira das diárias**: descartada. Traz nome e matrícula de servidor, que o projeto não usa.
- **Lista de vereadores do portal como fonte principal**: descartada. Não tem mandato nem filiação com data, e traz telefone e e-mail.
- **Começar em 2024, como na capital**: descartada. O portal da Câmara registra que os eleitos em 2020 cumpriram o mandato inteiro.
