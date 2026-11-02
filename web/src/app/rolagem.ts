/**
 * Mantém um item à vista dentro de um contêiner que rola, sem mexer na rolagem da página.
 * `scrollIntoView` num item de contêiner fixo (sticky) rola a página inteira no Safari.
 */
export function manterVisivel(item: HTMLElement, contêiner: HTMLElement): void {
  const c = contêiner.getBoundingClientRect();
  const i = item.getBoundingClientRect();
  if (contêiner.scrollWidth > contêiner.clientWidth) {
    contêiner.scrollLeft += i.left - c.left - (c.width - i.width) / 2;
  }
  if (contêiner.scrollHeight > contêiner.clientHeight) {
    if (i.top < c.top) {
      contêiner.scrollTop += i.top - c.top - 8;
    } else if (i.bottom > c.bottom) {
      contêiner.scrollTop += i.bottom - c.bottom + 8;
    }
  }
}
