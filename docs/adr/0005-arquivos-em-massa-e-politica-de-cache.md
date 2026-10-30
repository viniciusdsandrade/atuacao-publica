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
Na primeira carga no GitHub Actions, o passo de coleta, que começa pela Câmara, passou de duas horas sem terminar.
Na máquina de desenvolvimento, a coleta completa leva cerca de 25 minutos.

## Decisão

1. Usar o arquivo anual em massa sempre que a fonte publicar um. A API por registro fica para o que não tem arquivo (detalhe, histórico, frentes e discursos do deputado; dados por senador).
2. Guardar no cache bruto o que o servidor envia compactado, com a extensão `.gz`.
3. Política por arquivo:
   - `SEMPRE`: período aberto (ano corrente e anterior) e arquivos cujo conteúdo muda com o tempo (situação de proposições). Baixa de novo a cada execução.
   - `SE_AUSENTE`: período fechado. Reaproveita o cache.
4. Se a atualização falhar e houver cópia anterior, usar a cópia com a data de captura original e marcar a fonte como `parcial`.
5. Depois de 3 falhas no mesmo tipo de arquivo, não tentar de novo na mesma execução. Fonte que alterna sucesso e erro não pode prender a coleta por horas.
6. ZIP grande do qual só interessa a lista de arquivos (propostas de governo do TSE): baixar para um temporário, ler o diretório central e guardar só a lista com o sha256 do ZIP.
7. No CI, cada fonte roda em passo próprio, com limite de tempo. Fonte lenta não consome o tempo das outras.
8. O cache do Actions é gravado no fim do job mesmo quando um passo falha ou estoura o limite. Se o processo for encerrado no meio da coleta, o cursor da fonte registra a falha.

## Consequências

- A primeira coleta da Câmara faz cerca de 3.000 requisições; as seguintes reaproveitam os anos fechados.
- Uma falha pontual da fonte não apaga dados do site: o bloco mostra o aviso de fonte desatualizada.
- O cache do GitHub Actions guarda `data/raw` entre execuções. Se o cache sumir, a próxima execução baixa tudo de novo e publica o mesmo resultado.
- Uma carga que estoura o limite de tempo não se perde: a execução seguinte reaproveita os arquivos de período fechado já baixados.
- O log de cada passo de coleta mostra o progresso a cada 250 downloads e as tentativas que falharam, por causa.

## Alternativas consideradas

- API por registro para tudo: mais requisições, mais tempo, mais exposição a limite de taxa.
- Arquivo anual de frentes (`frentesDeputados.json`, 157 MB, todas as legislaturas): volume desproporcional ao uso. Fica como plano B se o endpoint por deputado continuar falhando.
