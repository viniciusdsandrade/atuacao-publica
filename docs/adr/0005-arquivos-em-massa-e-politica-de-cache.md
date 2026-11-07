# ADR-0005: Arquivos em massa e política de cache

- Status: aceita
- Data: 2026-10-30

## Contexto

A Câmara tem 513 deputados e centenas de votações nominais por ano.
Consultar a API por votação e por deputado passaria de dezenas de milhares de requisições por execução.
A Câmara publica arquivos anuais com votações, votos, orientações, proposições, autores, temas, eventos e presenças.
O servidor aceita compressão gzip: o arquivo de orientações de 2026 cai de 749 KB para 19 KB.
O endpoint de despesas por deputado responde vazio; a cota parlamentar existe em arquivo anual.
Endpoints por deputado às vezes falham: em 2026-10-30, `/deputados/{id}/frentes` respondeu 504 seguidamente.
No mesmo dia, entre 12:40 e 15:10 UTC, a API da Câmara respondeu 504, depois de cerca de 15 segundos, a boa parte das requisições, dentro e fora do CI.
Nesse intervalo, a primeira carga no GitHub Actions passou de duas horas no passo de coleta sem terminar.
A partir de 15:57 UTC, com a API respondendo, a coleta da Câmara com cache vazio levou 35 minutos no GitHub Actions: 3.812 arquivos, com 80 respostas 504 recuperadas por nova tentativa.
A coleta do Senado levou 3 minutos, a do TSE 1 minuto e a do Executivo 11 segundos.

## Decisão

1. Usar o arquivo anual em massa sempre que a fonte publicar um. A API por registro fica para o que não tem arquivo (detalhe, histórico, frentes e discursos do deputado; dados por senador).
2. Guardar no cache bruto o que o servidor envia compactado, com a extensão `.gz`.
3. Política por arquivo:
   - `SEMPRE`: período aberto e arquivos cujo conteúdo muda com o tempo (situação de proposições). Baixa de novo a cada execução. O período aberto é o ano corrente e, até 31 de março, o ano anterior: registros dele ainda podem chegar ou mudar no início do ano seguinte.
   - `SE_AUSENTE`: período fechado. Reaproveita o cache.
   - `SEMANAL`: dado que muda pouco (detalhe e frentes do deputado). Reaproveita a cópia por até 7 dias. Cada arquivo é renovado no seu dia de rodízio, e a carga fica espalhada pela semana. Partido e UF vêm da lista diária de deputados em exercício, e o histórico de exercício continua diário.
4. Se a atualização falhar e houver cópia anterior, usar a cópia com a data de captura original e marcar a fonte como `parcial`.
5. Depois de 3 falhas no mesmo tipo de arquivo, não tentar de novo na mesma execução. Fonte que alterna sucesso e erro não pode prender a coleta por horas.
6. ZIP grande do qual só interessa a lista de arquivos (propostas de governo do TSE): baixar para um temporário, ler o diretório central e guardar só a lista com o sha256 do ZIP.
7. No CI, cada fonte roda em passo próprio, com limite de tempo. Fonte lenta não consome o tempo das outras.
8. O cache do Actions é gravado no fim do job mesmo quando um passo falha ou estoura o limite. Se o processo for encerrado no meio da coleta, o cursor da fonte registra a falha.
9. Fonte com mais de 3 dias sem coleta válida abre uma issue com o rótulo `coleta`. A issue fecha sozinha quando a coleta volta. A falha de uma fonte não derruba o job, então sem esse alerta ela só apareceria no resumo da execução.
10. ZIP grande do qual só interessam algumas entradas (prestação de contas do TSE, ADR-0032): ler por requisição Range o fim do arquivo, o diretório central e cada entrada, sem baixar o resto. O cache guarda só o recorte útil da entrada. O meta registra a URL do ZIP com o nome da entrada depois de `#`, o sha256 e o tamanho da entrada descompactada. A entrada é conferida pelo CRC-32 do diretório central. Servidor que ignora o Range (responde 200) vira falha, não download do arquivo inteiro.

## Consequências

- A primeira coleta da Câmara faz cerca de 3.800 requisições; as seguintes reaproveitam os anos fechados.
- Em 2026-10-31, com o cache preenchido, a coleta da Câmara baixou 1.213 arquivos e reaproveitou 2.599, em 15 minutos no GitHub Actions.
- Uma falha pontual da fonte não apaga dados do site: o bloco mostra o aviso de fonte desatualizada.
- O cache do GitHub Actions guarda `data/raw` entre execuções. Se o cache sumir, a próxima execução baixa tudo de novo e publica o mesmo resultado.
- Uma carga que estoura o limite de tempo não se perde: a execução seguinte reaproveita os arquivos de período fechado já baixados.
- O log de cada passo de coleta mostra o progresso a cada 250 downloads e as tentativas que falharam, por causa.
- Com o item 10, a primeira carga da prestação de contas baixa cerca de 530 MB das entradas, em vez de cerca de 3,8 GB dos ZIPs inteiros (medido em 2026-11-06). O item 6 pode usar o mesmo caminho para ler só o diretório central.

## Alternativas consideradas

- API por registro para tudo: mais requisições, mais tempo, mais exposição a limite de taxa.
- Arquivo anual de frentes (`frentesDeputados.json`, 157 MB, todas as legislaturas): volume desproporcional ao uso. Fica como plano B se o endpoint por deputado continuar falhando.
