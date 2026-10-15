'use strict';
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const fs = require('node:fs/promises');
const path = require('node:path');
let browser;
const web = path.resolve('src/main/resources/webapp');
before(async () => {
  browser = await chromium.launch(process.env.ZKUI_BROWSER_CHANNEL ? { channel: process.env.ZKUI_BROWSER_CHANNEL } : {});
});
after(async () => { if (browser) await browser.close(); });

async function pageFor(name) {
  const page = await browser.newPage();
  const failures = [];
  page.on('pageerror', error => failures.push(error.message));
  await page.route('**/*', async route => {
    const url = new URL(route.request().url());
    assert.equal(url.origin, 'http://zkui.test', 'Page must work without external resources');
    if (url.pathname === '/home' || url.pathname === '/search') {
      await route.fulfill({ contentType: 'text/html; charset=UTF-8', body: await fs.readFile('target/browser-pages/' + name + '.html', 'utf8') });
    } else {
      const file = path.resolve(web, '.' + url.pathname);
      assert.ok(file.startsWith(web + path.sep));
      await route.fulfill({ path: file });
    }
  });
  await page.goto('http://zkui.test/' + (name === 'search' ? 'search' : 'home'));
  return { page, failures };
}

function names(page, column) {
  return page.locator('table[data-sortable] tbody tr').evaluateAll((rows, index) => rows.map(row => row.cells[index].textContent.trim()), column);
}

test('home sorting supports keyboard, toggles direction and preserves controls and modal editing', async () => {
  const { page, failures } = await pageFor('home');
  try {
    const selected = page.locator('input[name=propChkGroup][value="/appconfig/sort/Beta"]');
    await selected.check();
    const name = page.getByRole('button', { name: 'Name', exact: true });
    await name.focus();
    await page.keyboard.press('Enter');
    assert.deepEqual(await names(page, 1), ['alpha', 'Beta', 'db.Password', 'gamma']);
    assert.equal(await name.locator('..').getAttribute('aria-sort'), 'ascending');
    assert.equal(await selected.isChecked(), true);
    await page.keyboard.press('Space');
    assert.deepEqual(await names(page, 1), ['gamma', 'db.Password', 'Beta', 'alpha']);
    assert.equal(await name.locator('..').getAttribute('aria-sort'), 'descending');
    await page.getByRole('button', { name: 'Value', exact: true }).click();
    assert.equal(await name.locator('..').getAttribute('aria-sort'), 'none');
    const order = await names(page, 1);
    assert.ok(order.indexOf('Beta') < order.indexOf('alpha'), 'Equal values keep their prior relative order');
    assert.equal(await selected.isChecked(), true);
    await page.locator('a.href-select').filter({ hasText: /^Beta$/ }).click();
    assert.equal(await page.locator('#newProperty').inputValue(), 'Beta');
    assert.equal(await page.locator('#newValue').inputValue(), 'sort-fixture equal');
    assert.equal(await page.evaluate(() => window.valueExecuted), undefined);
    assert.equal(await page.locator('span.mono-pre-wrap').filter({ hasText: 'window.valueExecuted' }).evaluate(el => getComputedStyle(el).whiteSpace), 'pre-wrap');
    assert.deepEqual(failures, []);
  } finally { await page.close(); }
});

test('search sorting has independent columns and cannot execute displayed markup', async () => {
  const { page, failures } = await pageFor('search');
  try {
    await page.getByRole('button', { name: 'Name', exact: true }).click();
    assert.deepEqual(await names(page, 1), ['alpha', 'Beta', 'gamma']);
    await page.getByRole('button', { name: 'Path', exact: true }).click();
    assert.deepEqual(await names(page, 1), ['alpha', 'Beta', 'gamma']);
    assert.equal(await page.evaluate(() => window.valueExecuted), undefined);
    assert.deepEqual(failures, []);
  } finally { await page.close(); }
});

test('readonly page retains password masking after sorting', async () => {
  const { page, failures } = await pageFor('readonly');
  try {
    await page.getByRole('button', { name: 'Value', exact: true }).click();
    const text = await page.locator('body').textContent();
    assert.ok(text.includes('SOPA/PIPA BLACKLISTED VALUE'));
    assert.ok(!text.includes('synthetic-browser-secret'));
    assert.equal(await page.locator('a.href-select').count(), 0);
    assert.deepEqual(failures, []);
  } finally { await page.close(); }
});
