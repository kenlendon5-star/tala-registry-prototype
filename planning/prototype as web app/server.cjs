const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const root = __dirname;
const contentTypes = new Map([
  ['.css', 'text/css; charset=utf-8'],
  ['.html', 'text/html; charset=utf-8'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.md', 'text/markdown; charset=utf-8'],
  ['.png', 'image/png'],
  ['.jpg', 'image/jpeg'],
  ['.jpeg', 'image/jpeg'],
  ['.svg', 'image/svg+xml'],
  ['.webp', 'image/webp'],
]);

function resolveRequestPath(rawUrl = '/') {
  let decoded;
  try {
    decoded = decodeURIComponent(rawUrl.split('?')[0]);
  } catch {
    return { status: 400 };
  }
  const requested = decoded === '/' ? '/index.html' : decoded;
  const file = path.resolve(root, `.${requested}`);
  const relative = path.relative(root, file);
  if (relative.startsWith('..') || path.isAbsolute(relative)) return { status: 403 };
  return { status: 200, file };
}

function contentType(file) {
  return contentTypes.get(path.extname(file).toLowerCase()) || 'application/octet-stream';
}

function handleRequest(req, res) {
  if (!['GET', 'HEAD'].includes(req.method || 'GET')) {
    res.writeHead(405, { Allow: 'GET, HEAD', 'Content-Type': 'text/plain; charset=utf-8' });
    return res.end('Method not allowed');
  }
  const resolved = resolveRequestPath(req.url);
  if (resolved.status !== 200) {
    res.writeHead(resolved.status, { 'Content-Type': 'text/plain; charset=utf-8' });
    return res.end(resolved.status === 400 ? 'Malformed request URL' : 'Forbidden');
  }
  fs.readFile(resolved.file, (error, body) => {
    if (error) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      return res.end('Not found');
    }
    res.writeHead(200, {
      'Content-Type': contentType(resolved.file),
      'X-Content-Type-Options': 'nosniff',
    });
    return res.end(req.method === 'HEAD' ? undefined : body);
  });
}

if (require.main === module) {
  http.createServer(handleRequest).listen(5173, '127.0.0.1', () => console.log('Tala: http://localhost:5173'));
}

module.exports = { contentType, handleRequest, resolveRequestPath };
