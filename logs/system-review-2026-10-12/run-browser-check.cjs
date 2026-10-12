// Audit wrapper: preserve existing screenshots and bound Edge teardown.
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const target = path.resolve(process.argv[2]);
let source = fs.readFileSync(target, 'utf8');
source = 'const auditSkipScreenshot = async () => Buffer.alloc(0);\n' + source
  .replaceAll('await page.screenshot(', 'await auditSkipScreenshot(')
  .replaceAll('await browser.close()', 'await Promise.race([browser.close(), new Promise(r => setTimeout(r, 3000))]); setTimeout(() => process.exit(process.exitCode || 0), 500).unref()');
const testModule = new Module(target, module);
testModule.filename = target;
testModule.paths = Module._nodeModulePaths(path.dirname(target));
testModule._compile(source, target);
