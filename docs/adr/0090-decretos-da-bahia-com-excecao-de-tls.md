# ADR-0090: Decretos da Bahia, com exceção de TLS só no passo da Bahia

- **Status**: Aceita
- **Data**: 2026-11-21

## Contexto

O Legisla Bahia publica os decretos numerados do Governo da Bahia em HTML, com pesquisa por período (ADR-0089).
O servidor só negocia TLS 1.2 com troca de chave RSA (`AES256-GCM-SHA384`), sem sigilo futuro. O Java 25 desativa essa troca de chave por padrão (`TLS_RSA_*` em `jdk.tls.disabledAlgorithms`), e a conexão falha.
A propriedade de segurança é lida uma vez por processo: mudá-la no código depois da primeira conexão não tem efeito.
O mantenedor autorizou, em 2026-11-21, a exceção restrita à coleta da Bahia.

## Decisão

1. O arquivo `etl/tls-legado-ba.security` repete a lista de algoritmos desativados do JDK 25 sem `TLS_RSA_*`. As demais restrições ficam iguais.
2. Só o passo `fetch executivoba` do CI roda com `-Djava.security.properties=etl/tls-legado-ba.security`. Os outros passos, cada um num processo próprio, seguem com a configuração padrão.
3. A coleta da Bahia passa a trazer os decretos numerados desde 2022, um mês por consulta, 20 por página (`ba-legisla`). Os meses anteriores aos dois últimos vêm do cache; os recentes renovam uma vez por semana.
4. O orçamento do passo sobe para 20 min: cada página leva cerca de 12 s.

## Consequências

- O perfil do governador da Bahia ganha os decretos numerados do mandato.
- A exceção vale para todo o processo do passo da Bahia, inclusive o download do painel de despesas. O servidor de dados abertos negocia troca de chave com sigilo futuro, e a negociação continua escolhendo a mais forte que os dois lados aceitam.

## Limitações

- A troca de chave RSA não tem sigilo futuro. Os dados são públicos e não há credencial na conexão; o risco aceito é de confidencialidade de dado público.
- Sem a opção, a coleta dos decretos falha no handshake, e a da despesa segue normal. Quem roda `fetch` de todas as fontes num processo só fica sem os decretos da Bahia.
- Se o Legisla Bahia passar a negociar troca de chave com sigilo futuro, a exceção deve sair.
