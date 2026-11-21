# ADR-0078: Proposições da ALERJ

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

Na ALERJ, os 70 deputados tinham só mandatos e despesas (ADR-0018 e ADR-0049).
O portal novo não tem página de proposições (`/Proposicoes` responde 404).
O sistema de processo legislativo da legislatura continua em Lotus Domino (`alerjln1.alerj.rj.gov.br/scpro2327.nsf`), com uma visão por tipo de proposição e pesquisa por campo.

## Decisão

1. A coleta da ALERJ passa a trazer as PECs, os PLCs, os PLs, os PDLs e os PRs da 13ª legislatura, pela pesquisa de cada visão de tipo (`alerj-proposicoes`).
2. A consulta é pelo prefixo do código (`AAAATT`, ano e tipo). Quando chega ao limite de 1.000 linhas, ela se divide pelo dígito seguinte. Os números são contínuos: o primeiro prefixo vazio depois de um cheio encerra a divisão.
3. O ano fechado vem do cache. O ano aberto renova uma vez por semana, como nas outras listagens grandes.
4. Cada autoria vira um registro no perfil do deputado, com a ordem de assinatura. O primeiro autor é o proponente.
5. A autoria se liga pelo nome, sem acento e sem pontuação. Sem ligação exata, a segunda tentativa tira o tratamento ("Dr.", "Prof.", "Delegado") e só liga quando um único deputado tem o nome.
6. A situação fica nula: a fonte não a publica. Os temas ficam nulos: o que vem depois de `=>` são as comissões de destino, não temas.
7. A data publicada é a da linha da pesquisa, a data de publicação no sistema.
8. A pesquisa leva até 1 min por resposta. O passo `fetch alerj` do CI ganha 30 min de orçamento; esgotado, o resto segue na execução seguinte.

## Consequências

- Os deputados da ALERJ ganham o bloco de proposições.
- A carga inicial leva dezenas de consultas e se espalha por mais de uma execução do CI.

## Limitações

- Indicações, requerimentos e moções ficam de fora nesta etapa.
- Autoria de quem saiu da Assembleia antes do início da coleta não tem perfil e fica de fora.
- Nome parcial ("YURI", "POUBEL") não se liga.
- Voto por deputado não existe na fonte. A presença sai por mês, sem a data da sessão, e não é coletada.
- Não validado: se o servidor responde ao runner do GitHub, fora do Brasil. A conexão oscila também da rede do mantenedor.

## Alternativas consideradas

- **Ler o documento de cada proposição**: descartada. São cerca de 23 mil documentos, de 20 s a 45 s cada.
- **Paginar a visão com `Start=`**: descartada. O servidor recusa a consulta com esse parâmetro.
