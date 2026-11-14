# ADR-0061: Cache cifrado opcional de dados brutos no GitHub Actions

- **Data**: 2026-11-14
- **Status**: Aceito

## Contexto

O workflow `.github/workflows/dados.yml` preserva `data/raw`, `data/publicado/_meta` e `data/publicado/index.json` entre execuções diárias usando `actions/cache`. Embora os dados coletados sejam públicos, respostas brutas antes da minimização podem conter campos originais descartados na publicação, além de o cache do Actions em repositórios públicos ficar acessível conforme as regras de escopo de branch da plataforma.

Ao mesmo tempo, é necessário manter compatibilidade retroativa com execuções e ambientes que ainda não tenham o segredo de cifra configurado, bem como suportar transição e rotação de chave sem quebrar a continuidade do cache.

## Decisão

1. **Segredo opcional `CACHE_CHAVE`**:
   - Quando `secrets.CACHE_CHAVE` estiver definido no repositório, `scripts/cache-preparar.sh` empacota `data/raw`, `data/publicado/_meta` e `data/publicado/index.json` em `tar.gz` e cifra com OpenSSL (`aes-256-cbc`, PBKDF2 com 200.000 iterações e SHA-256) em `.cache-cifrado/dados.tar.gz.enc`.
   - O workflow salva `.cache-cifrado` sob a chave `dados-cifrado-<run_id>-<run_attempt>`.

2. **Restauração e transição compatível**:
   - Na abertura do job, o workflow tenta baixar `dados-cifrado-` e `scripts/cache-restaurar.sh` decifra e valida estritamente a lista de caminhos do arquivo tar antes de extrair para `data/`.
   - Se `CACHE_CHAVE` estiver ausente, se ainda não existir pacote cifrado (primeira execução após ativar a chave) ou se a chave tiver sido rotacionada, o passo sinaliza `cifrado_restaurado=false` e o workflow recorre normalmente ao fallback legado `dados-`.
   - Ao final da execução, existindo `CACHE_CHAVE`, apenas a entrada `dados-cifrado-*` é salva.

## Consequências

- A ativação exige apenas cadastrar o segredo `CACHE_CHAVE` no GitHub, sem perda do histórico de coleta anterior.
- Sem o segredo, o comportamento permanece inalterado.
- `.cache-cifrado/` fica listado no `.gitignore` para evitar envio acidental em commits locais.
