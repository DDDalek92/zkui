'use strict';
const assert = require('node:assert/strict');
const { chromium, firefox } = require('playwright');
const fs = require('node:fs/promises');

async function main() {
  assert.equal(process.env.ZKUI_OWNED_BROWSER_FIXTURE, 'true');
  const base = process.env.ZKUI_BROWSER_BASE;
  const url = new URL(base);
  assert.equal(url.protocol, 'https:');
  assert.equal(url.hostname, '127.0.0.1');
  assert.equal(url.pathname, '/browser-live');
  assert.ok(process.env.ZKUI_BROWSER_PASSWORD);
  await fs.mkdir('target/live-browser-evidence', { recursive: true });
  const reports = [];
  for (const [name, type] of [['chromium', chromium], ['firefox', firefox]]) {
    const browser = await type.launch(name === 'chromium' ? { channel: 'chrome' } : {});
    const context = await browser.newContext({ ignoreHTTPSErrors: true }); // Owned self-signed certificate only.
    const errors = [];
    context.on('page', page => page.on('pageerror', error => errors.push(error.message)));
    context.on('request', request => {
      const address = new URL(request.url());
      assert.equal(address.origin, url.origin, 'No external requests from owned browser pages');
      assert.ok(address.pathname.startsWith('/browser-live/'), 'All live paths retain the context');
    });
    context.setDefaultTimeout(5000);
    let page;
    try {
      page = await context.newPage();
      await page.goto(base + '/login');
      const before = (await context.cookies()).find(c => c.name === 'JSESSIONID');
      assert.ok(before);
      await page.locator('input[name=username]').fill('test-admin');
      await page.locator('input[name=password]').fill(process.env.ZKUI_BROWSER_PASSWORD);
      await Promise.all([page.waitForURL(base + '/home'), page.getByRole('button', { name: 'Sign In' }).click()]);
      const after = (await context.cookies()).find(c => c.name === 'JSESSIONID');
      assert.notEqual(after.value, before.value);
      assert.equal(after.httpOnly, true);
      assert.equal(after.secure, true);
      assert.equal(after.path, '/browser-live');
      const rejected = await context.request.post(base + '/home', { form: {
        action: 'Save Property', displayPath: '/', currentPath: '/', newProperty: 'browser-live-csrf-denied',
        newValue: 'denied', csrfToken: 'invalid-synthetic-token'
      } });
      assert.equal(rejected.status(), 403);
      await page.locator('#addPropertyBtn').click();
      await page.locator('#newProperty').fill('browser-live-' + name);
      await page.locator('#newValue').fill('browser <value> & 中文');
      await Promise.all([page.waitForURL('**/home?zkPath=*'), page.locator('#savePropertyBtn').click()]);
      assert.ok((await page.locator('body').innerText()).includes('browser <value> & 中文'));
      assert.equal(await page.locator('value').count(), 0, 'Escaped node data must not become an element');
      await page.locator('.dropdown-toggle').click();
      await Promise.all([page.waitForURL(base + '/login'), page.getByRole('button', { name: 'Logout', exact: true }).click()]);
      await page.goto(base + '/home');
      assert.equal(new URL(page.url()).pathname, '/browser-live/login');
      await page.locator('input[name=username]').fill('test-user');
      await page.locator('input[name=password]').fill(process.env.ZKUI_BROWSER_PASSWORD);
      await Promise.all([page.waitForURL(base + '/home'), page.getByRole('button', { name: 'Sign In' }).click()]);
      assert.equal(await page.locator('#addPropertyBtn').count(), 0);
      const token = await page.locator('input[name=csrfToken]').first().inputValue();
      const denied = await context.request.post(base + '/home', { form: {
        action: 'Save Property', displayPath: '/', currentPath: '/', newProperty: 'browser-live-user-denied',
        newValue: 'denied', csrfToken: token
      } });
      assert.equal(denied.status(), 403);
      assert.deepEqual(errors, []);
      reports.push({ engine: name, version: browser.version(), passed: true });
      console.log('LIVE_BROWSER_' + name.toUpperCase() + '_PASSED');
    } catch (error) {
      if (page) await page.screenshot({ path: 'target/live-browser-evidence/' + name + '-failure.png', fullPage: true }).catch(() => {});
      throw error;
    } finally { await context.close(); await browser.close(); }
  }
  await fs.writeFile('target/live-browser-evidence/results.json', JSON.stringify(reports, null, 2) + '\n');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
