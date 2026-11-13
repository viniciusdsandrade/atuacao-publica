// Roda antes da primeira pintura. É arquivo próprio porque a CSP do site não aceita script embutido.
// 1. Aplica o tema salvo, para a página não piscar no tema do aparelho antes de o site iniciar.
//    A chave é a mesma de src/app/tema.ts (um teste confere).
// 2. Na página inicial, pede o índice em paralelo com o código do site. O site usa esta resposta em vez de pedir
//    de novo (src/app/dados.ts). Não é <link rel="preload">: o Safari não reaproveita a pré-carga e baixaria duas vezes.
(function () {
  try {
    var tema = localStorage.getItem('atuacao-publica:tema');
    if (tema === 'claro' || tema === 'escuro') {
      document.documentElement.setAttribute('data-tema', tema);
    }
  } catch (erro) {
    // Sem armazenamento local, vale o tema do aparelho.
  }

  // Lista de políticos: endereço sem rota ("", "#/") ou só com a busca ("#/?q=...").
  if (/^(#\/?(\?.*)?)?$/.test(location.hash) && typeof fetch === 'function') {
    var indice = fetch('dados/index.json').then(function (resposta) {
      if (!resposta.ok) {
        throw new Error('índice: ' + resposta.status);
      }
      return resposta.json();
    });
    // Se o pedido falhar, o site pede de novo pelo caminho normal. Aqui a falha só não vira erro solto no console.
    indice.catch(function () {});
    window.indiceAdiantado = indice;
  }
})();
