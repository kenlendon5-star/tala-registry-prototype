const assert = require('node:assert/strict');
const path = require('node:path');
const { contentType, handleRequest, resolveRequestPath } = require('../server.cjs');

assert.equal(resolveRequestPath('/%').status, 400);
assert.equal(resolveRequestPath('/..%2fpackage.json').status, 403);
assert.equal(path.basename(resolveRequestPath('/').file), 'index.html');
assert.equal(contentType('fixture.json'), 'application/json; charset=utf-8');
assert.equal(contentType('fixture.png'), 'image/png');
assert.equal(contentType('fixture.unknown'), 'application/octet-stream');

let status;
let body;
handleRequest(
  { method: 'GET', url: '/%' },
  {
    writeHead(value) { status = value; },
    end(value) { body = value; },
  },
);
assert.equal(status, 400);
assert.equal(body, 'Malformed request URL');

console.log('Preview server passed: malformed URLs, traversal, MIME types and safe response handling.');
