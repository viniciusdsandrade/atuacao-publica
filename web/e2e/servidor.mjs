// Servidor dos testes E2E. Serve o build do site (dist/web/browser) e, em /dados, o conjunto sintético.
// Só Node, sem dependências. Escuta apenas em 127.0.0.1.
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

import { montarDados } from './dados-sinteticos.mjs';

const RAIZ_WEB = resolve(fileURLToPath(new URL('..', import.meta.url)));
const SITE = join(RAIZ_WEB, 'dist', 'web', 'browser');
const PORTA = Number(process.env.E2E_PORTA ?? 4173);

const TIPOS = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.md': 'text/markdown; charset=utf-8',
  '.csv': 'text/csv; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.png': 'image/png',
  '.txt': 'text/plain; charset=utf-8',
  '.gz': 'application/gzip',
  '.woff2': 'font/woff2',
};

const dados = await montarDados(RAIZ_WEB);

function responder(res, status, corpo, tipo) {
  res.writeHead(status, { 'Content-Type': tipo, 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
  res.end(corpo);
}

const servidor = createServer(async (req, res) => {
  let caminho;
  try {
    caminho = decodeURIComponent(new URL(req.url ?? '/', 'http://127.0.0.1').pathname);
  } catch {
    return responder(res, 400, 'requisição inválida', TIPOS['.txt']);
  }

  if (caminho.startsWith('/dados/')) {
    const item = dados.get(caminho.slice('/dados/'.length));
    if (item === undefined) {
      return responder(res, 404, 'não encontrado', TIPOS['.txt']);
    }
    if (Buffer.isBuffer(item)) {
      return responder(res, 200, item, TIPOS['.gz']);
    }
    if (typeof item === 'string') {
      return responder(res, 200, item, TIPOS[extname(caminho)] ?? TIPOS['.txt']);
    }
    return responder(res, 200, JSON.stringify(item), TIPOS['.json']);
  }

  const relativo = caminho === '/' ? 'index.html' : normalize(caminho).replace(/^[/\\]+/, '');
  const arquivo = resolve(SITE, relativo);
  if (arquivo !== SITE && !arquivo.startsWith(SITE + sep)) {
    return responder(res, 403, 'proibido', TIPOS['.txt']);
  }
  try {
    return responder(res, 200, await readFile(arquivo), TIPOS[extname(arquivo)] ?? 'application/octet-stream');
  } catch {
    return responder(res, 404, 'não encontrado', TIPOS['.txt']);
  }
});

servidor.listen(PORTA, '127.0.0.1');
