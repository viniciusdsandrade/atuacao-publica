# ADR-0060: Logotipo do site

- **Status**: Aceita
- **Data**: 2026-11-14

## Contexto

O logotipo era um frontão com colunas dentro de um quadrado de cantos arredondados.
É o ícone genérico de instituição. Serve igual para banco, tribunal, museu e órgão público.

Problemas:

- O desenho mostra o prédio. O projeto trata do que as pessoas eleitas fazem.
- Nada no desenho fala de registro nem de fonte, que são o ponto do projeto (política editorial, princípio 4).
- O ícone de órgão público faz o site parecer portal oficial. O projeto é independente.
- Não distingue o site de nenhum outro.

Restrições mantidas:

- Paleta única e neutra, sem cor de partido, sem vermelho e sem verde com amarelo (ADR-0012 e ADR-0035).
- Direção "papel e tinta" (ADR-0035).
- SVG embutido, sem fonte web e sem arquivo de terceiros (ADR-0012).
- Temas claro e escuro, `forced-colors` e leitura em 16 px no favicon.

## Decisão

1. **O logotipo é o plenário entre colchetes.** O plenário diz de quem o site trata. Os colchetes dizem como: tudo vem com fonte.
2. **O plenário é um hemiciclo de 27 pontos iguais**, em três fileiras de 6, 9 e 12, com um ponto maior no centro.
   - É o gráfico de bancadas que o noticiário colore por partido. Aqui todos os pontos têm a mesma cor e o mesmo tamanho (política editorial, princípio 1).
   - São 27 pontos, um por unidade da federação.
   - São três fileiras, uma por esfera: federal, estadual e municipal.
   - O ponto maior é a tribuna, de onde parte a atuação que o site registra.
3. **Os colchetes são os da citação de fonte**, como em `[1]`. São retos e de canto vivo, como os de um tipo de imprensa.
4. **A redução fica com os colchetes e o ponto da tribuna.** Vale onde os assentos não se leem. Hoje é o favicon, sobre um quadrado de cantos arredondados, para ter contraste em qualquer aba.
5. **Cor**: `--destaque` sobre a superfície, nos dois temas. O favicon troca de cor com `prefers-color-scheme`.
6. **Nome**: "Atuação Pública" segue na fonte do sistema, ao lado do desenho. O desenho é decorativo (`aria-hidden`).

### Desenho

Assinatura, em quadro de 48 por 32:

- Colchetes: traço de 3, na altura inteira do quadro, com abas de 7.
- Centro do hemiciclo em (24; 23,8).
- Fileiras com raio de 7,5, 12 e 16,5. O passo entre assentos é o mesmo nas três: 1,5π.
- Assento: círculo de raio 1,7. Tribuna: círculo de raio 2,6.

Redução, em quadro de 32 por 32:

- Fundo com raio de canto 7.
- Colchetes: traço de 4, de 6 a 26 na altura, com abas de 8.
- Ponto de raio 4 no centro.
- Todas as bordas retas caem em coordenada par. Em 16 px o desenho fica na grade de pixels.

Tamanhos no site:

| Lugar | Largura × altura |
|---|---|
| Cabeçalho | 48 × 32 px |
| Cabeçalho abaixo de 24 rem | 39 × 26 px |
| Rodapé | 33 × 22 px |
| Favicon | redução, 16 e 32 px |

### Neutralidade

- Um plenário de uma cor só. Nenhum ponto é destacado, maior ou de outra forma.
- A contagem dos pontos vem das unidades da federação, e não de bancada, sigla ou número de partido.
- Nenhum símbolo de juízo ou de vigilância: balança, lupa, olho, visto, estrela ou medidor.
- O hemiciclo não é medidor. Não há arco parcialmente preenchido nem ponteiro (ADR-0012: nada que sugira ranking).

## Alternativas consideradas

- **Manter o frontão com colunas**: descartada pelos problemas do contexto.
- **Frontão com pessoas no lugar das colunas**: descartada. Continua sendo o prédio.
- **Cúpulas do Congresso Nacional**: descartada. Cobre só a esfera federal e, em 16 px, parece gráfico de barras.
- **Hemiciclo em arcos cortados em blocos**: descartada. Em tamanho pequeno lê-se como símbolo de Wi-Fi.
- **Hemiciclo de pontos sem vazio no centro**: descartada. Parece uma pilha de pontos, e não um plenário.
- **Um ponto vazado para a lacuna ("sem registro na fonte")**: descartada. Com 27 pontos, sugere uma unidade da federação sem dados.
- **Só colchetes e ponto em todo tamanho**: descartada como assinatura. Diz o método e não diz o assunto. Ficou como redução.
- **Numeral de citação, como `[1]`**: descartada. Numeral em contexto político lê-se como número de partido ou posição em ranking.
- **Monograma com as iniciais**: descartada. "AP" é a marca de uma agência de notícias, e o projeto não usa imprensa.
- **Lupa, olho, balança ou visto**: descartadas. Sugerem vigilância ou juízo (política editorial, princípio 2).
- **Registro com nota de rodapé e trio "fez, faz, vai fazer"**: descartadas. Em 16 px viram ícone de lista e indicador de carga.
- **Pontos por traço pontilhado (`stroke-dasharray`)**: descartada. Chromium e WebKit deixam de desenhar o último ponto de cada fileira. Cada assento é um círculo.

## Consequências

- A marca do cabeçalho passa de 32 para 48 px de largura. Em 320 px, marca e botão "Menu" seguem na mesma linha.
- O favicon muda de desenho e ganha versão para o tema escuro.
- O componente `app-marca` calcula os 27 assentos a partir das três fileiras. O arquivo do favicon é estático.
- Nenhum dado, rota ou texto muda.

## Limitações

- Não validado: teste com pessoas. O gráfico de bancadas pode não ser reconhecido por quem não acompanha o noticiário político.
- Não validado: favicon no Safari e em aparelho real.
- O site não tem ícone em PNG para a tela inicial do celular nem imagem de prévia de link. Ficam fora deste ADR.
