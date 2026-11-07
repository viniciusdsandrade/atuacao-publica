# ADR-0031: Redes e sites declarados ao TSE

- Status: aceita
- Data: 2026-11-06

## Contexto

O candidato comunica à Justiça Eleitoral os endereços das redes e sites da campanha (Lei 9.504/1997, art. 57-B, § 1º).
O TSE publica esses endereços em `consulta_cand/rede_social_candidato_{ano}.zip`, um por eleição, com uma linha por endereço: `SQ_CANDIDATO`, `NR_ORDEM_REDE_SOCIAL` e `DS_URL`.
Em 2026-11-06, os ZIPs iam de 0,6 MB (2018) a 20,2 MB (2024), cada um com o CSV `_BRASIL`.
O roadmap previa o perfil verificado do político como fonte T1, só com snapshot arquivado e com o rótulo "declaração do próprio político".
O arquivo de 2026 traz a maior parte dos endereços todo em maiúsculas: 35.244 dos cerca de 55 mil com esquema. A grafia original do caminho se perdeu na fonte.
O mesmo arquivo traz milhares de entradas que não são endereço: 2.760 textos com espaço, 2.048 "@perfil" soltos e 474 palavras sem ponto. Os anos anteriores vêm quase só com URLs.
Parte dos endereços leva a telefone: link de conversa e convite de grupo do WhatsApp, link de Telegram por número. Quem entra num grupo do WhatsApp vê o telefone de quem o administra.

## Decisão

1. Cada candidatura publica `redes_declaradas`: o nome da rede, tirado do domínio ("Site" quando não é rede conhecida), e o endereço, com a proveniência do arquivo de redes.
2. Só o endereço é publicado. O site não lê, não copia e não arquiva o conteúdo da página. Por isso não há snapshot: a regra do snapshot vale para conteúdo publicado como fonte T1.
3. O site mostra os endereços como declaração do próprio candidato ao TSE, com o ano da eleição.
4. Ficam fora os endereços que levam a telefone ou e-mail: WhatsApp, exceto canal; Telegram por número; `tel:`; `mailto:`; número com DDI; e os padrões da guarda de CPF, telefone e e-mail.
5. Fica fora o texto que não é endereço. O site não adivinha a rede de um "@perfil" solto. Endereço com espaço ou com credencial embutida também fica fora.
6. Esquema e domínio vão para minúsculas, porque não diferenciam maiúsculas (RFC 3986). O caminho fica como a fonte publica. Texto que começa por domínio, sem esquema, ganha `https://`.
7. Entram até 20 endereços por candidatura, na ordem declarada. O restante é contado na saída do `normalize`.
8. O topo do perfil mostra os endereços da candidatura mais recente que declarou algum. O JSON guarda os de todas as candidaturas.
9. A coleta segue a política do `consulta_cand`: a eleição mais recente baixa sempre, as demais reaproveitam o cache. A falha deixa a fonte `tse` parcial, sem derrubar as candidaturas.

## Consequências

- No ensaio local de 2026-11-06, entraram 99.386 endereços das candidaturas cobertas. Ficaram fora 2.583 por telefone, e-mail ou CPF, 6.381 por não serem endereço e 463 além do limite.
- 1.758 dos 1.833 perfis com candidatura passaram a mostrar endereços declarados: 8.902 no topo do perfil, a maioria da eleição de 2026.
- O perfil cresce, na mediana, 2 KB, e no máximo 14,4 KB.
- Endereço de 2026 cujo caminho diferencia maiúsculas pode não abrir. Não validado quais.
- O endereço é o declarado na eleição. A conta pode ter mudado de dono ou deixado de existir depois. O ano fica à vista.

## Alternativas consideradas

- Perfil verificado como fonte T1, com snapshot: exige ler e arquivar conteúdo de rede social. A declaração ao TSE é fonte oficial e cobre todos os candidatos.
- Campo `redeSocial` da API da Câmara: cobre só deputados federais. O arquivo do TSE cobre todos os cargos.
- Completar o "@perfil" com uma rede presumida: inventaria dado.
- Passar o endereço inteiro para minúsculas: mudaria o caminho, que pode diferenciar maiúsculas.
- Nona coluna na tabela de candidaturas: a tabela já tem oito, e o topo do perfil é onde se procura onde o político está na rede.
