import { spawn } from 'node:child_process';
import { mkdir } from 'node:fs/promises';
import http from 'node:http';
import { chromium } from 'playwright';

const host = '127.0.0.1';
const port = 4173;
const baseUrl = `http://${host}:${port}`;
const cwd = new URL('..', import.meta.url).pathname;

function waitForServer(url, timeoutMs = 15000) {
  const startedAt = Date.now();
  return new Promise((resolve, reject) => {
    const tick = () => {
      const req = http.get(url, (res) => {
        res.resume();
        if ((res.statusCode || 500) < 500) {
          resolve();
          return;
        }
        if (Date.now() - startedAt > timeoutMs) {
          reject(new Error(`Preview server not ready: ${res.statusCode}`));
          return;
        }
        setTimeout(tick, 300);
      });
      req.on('error', () => {
        if (Date.now() - startedAt > timeoutMs) {
          reject(new Error('Preview server did not start in time'));
          return;
        }
        setTimeout(tick, 300);
      });
    };
    tick();
  });
}

async function run() {
  const preview = spawn('npm', ['run', 'preview', '--', '--host', host, '--port', String(port)], {
    cwd,
    stdio: ['ignore', 'pipe', 'pipe']
  });

  let serverLogs = '';
  preview.stdout.on('data', (chunk) => {
    serverLogs += chunk.toString();
  });
  preview.stderr.on('data', (chunk) => {
    serverLogs += chunk.toString();
  });

  let browser;
  try {
    await waitForServer(baseUrl);
    await mkdir(new URL('../artifacts', import.meta.url).pathname, { recursive: true });

    browser = await chromium.launch({ headless: true });
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    const pageErrors = [];
    page.on('pageerror', (err) => {
      pageErrors.push(err.message);
    });

    await page.goto(baseUrl, { waitUntil: 'networkidle' });
    await page.getByText('OpenManus Frontend').waitFor({ timeout: 6000 });
    await page.getByText('Conversation').first().waitFor({ timeout: 6000 });
    await page.getByText('Tools').first().waitFor({ timeout: 6000 });
    await page.getByText('Browser / Sandbox').waitFor({ timeout: 6000 });

    const textarea = page.getByPlaceholder('Type a message, Ctrl/⌘+Enter to send');
    await textarea.fill('Smoke check prompt');
    await page.getByRole('button', { name: 'Clear' }).click();
    const value = await textarea.inputValue();
    if (value.length !== 0) {
      throw new Error('Clear button did not reset input');
    }

    await page.getByRole('button', { name: 'Tool Output' }).click();
    await page.getByText('No tool output yet').waitFor({ timeout: 6000 });
    await page.getByRole('button', { name: 'Search Results' }).click();
    await page.getByText('No search results yet').waitFor({ timeout: 6000 });

    await page.screenshot({ path: new URL('../artifacts/ui-smoke.png', import.meta.url).pathname, fullPage: true });

    if (pageErrors.length > 0) {
      throw new Error(`Page errors detected: ${pageErrors.join(' | ')}`);
    }

    console.log('UI smoke test passed. Screenshot saved to front/artifacts/ui-smoke.png');
  } catch (error) {
    console.error('UI smoke test failed.');
    if (serverLogs.trim().length > 0) {
      console.error(serverLogs);
    }
    throw error;
  } finally {
    if (browser) {
      await browser.close();
    }
    preview.kill('SIGTERM');
  }
}

run().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
});
