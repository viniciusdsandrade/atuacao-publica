# Segurança

## Como relatar uma vulnerabilidade

1. Abra a aba **Security** do repositório no GitHub.
2. Clique em **Report a vulnerability**.
3. Descreva o problema, o impacto e os passos para reproduzir.

Não abra issue pública para vulnerabilidade.
Não publique o problema antes da correção.

## Escopo

- Código do ETL, do site e dos workflows.
- Exposição de dado pessoal em dados publicados, logs ou histórico do repositório.
- Segredos expostos (chaves de API, tokens).

## Dado pessoal publicado por engano

Se você encontrar CPF, telefone, e-mail pessoal, endereço residencial ou dado de familiar no site ou no repositório:

1. Use o mesmo canal de vulnerabilidade acima.
2. Informe a URL ou o arquivo e o campo.

O projeto remove o dado e corrige a regra de ingestão que deixou o dado passar.
