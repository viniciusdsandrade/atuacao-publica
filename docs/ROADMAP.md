# Roadmap

Estado real de cada fase. Atualizado ao fechar cada fase.

## POC (esfera federal)

| Fase | Entrega | Estado |
|---|---|---|
| 0 | Reconhecimento das fontes, `docs/FONTES.md`, ADR-0001 e ADR-0002 | concluída |
| 1 | Esqueleto do monorepo, documentos, templates, CI | concluída: CI verde no GitHub Actions |
| 2 | JSON Schemas do modelo e de `contrib/`, exemplos, validação | concluída: 10 schemas, 41 exemplos, `normalize contrib` no CI |
| 3 | ETL da Câmara | pendente |
| 4 | ETL do Senado | pendente |
| 5 | Executivo e TSE | pendente |
| 6 | Site | pendente |
| 7 | Pipeline agendado, deploy, relatório de execução | pendente |
| 8 | Contribuição de ponta a ponta | pendente |

## Limitações conhecidas

- `/deputados/{id}/despesas` da Câmara responde vazio. O ETL usa o arquivo anual de cotas.
- DivulgaCandContas bloqueia o User-Agent do projeto. O ETL usa os arquivos do TSE.
- A agenda presidencial existe só em HTML. Ela entra por contribuição manual.
- O e-Agendas não traz a Presidência da República.
- O Planalto recusou conexão a partir do ambiente de validação. Os atos normativos vêm da API do Senado.

## Fase 2 (depois da POC)

### Esfera estadual e municipal

1. Piloto municipal: prefeitura e câmara de vereadores de um município, com um adaptador por fonte.
2. Assembleias legislativas e governos estaduais: um adaptador por estado.
3. Gastos e agenda de governadores a partir dos portais estaduais catalogados em `docs/FONTES.md`.
4. Gastos do Executivo federal (cartão de pagamento e viagens) pelo Portal da Transparência.

### Redes sociais

- Perfil verificado do político como fonte T1, só com snapshot arquivado.
- Rótulo obrigatório: "declaração do próprio político".

### Processos judiciais, sanções e condenações

Só entram depois de um ADR que defina:

- critérios de fonte oficial;
- status processual explícito em cada registro;
- regra de presunção de inocência na exibição.

### Fora do escopo até novo ADR

Contas de usuário, comentários, denúncias e análises automáticas.
