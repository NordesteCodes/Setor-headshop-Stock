import { test, expect } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { createHmac } from 'node:crypto';
import { execFileSync } from 'node:child_process';

// Test against the isolated Compose project at 8081, never the operational database.
function testAccount(prefix) {
  const email=`${prefix}-${Date.now()}@example.test`, password='synthetic-test-password';
  execFileSync('docker',['compose','-p','inventory-e2e','-f','compose.yaml','-f','compose.e2e.yaml','exec','-T','db','psql','-U','inventory','-d','inventory','-v','ON_ERROR_STOP=1'],{
    input:`SELECT provision_internal_user('${email}','${password}');`,stdio:['pipe','pipe','pipe']
  });
  return {email,password};
}
function totp(secret, offset=0) {
  const alphabet='ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';let bits='';for(const c of secret)bits+=alphabet.indexOf(c).toString(2).padStart(5,'0');
  const key=Buffer.from(bits.match(/.{8}/g).map(b=>parseInt(b,2)));const counter=Buffer.alloc(8);counter.writeBigUInt64BE(BigInt(Math.floor(Date.now()/30000)+offset));
  const hash=createHmac('sha1',key).update(counter).digest();return String((hash.readUInt32BE(hash[19]&15)&0x7fffffff)%1000000).padStart(6,'0');
}
test('login 2FA → estoque → cliente → fiado → entrega → recebimento → caixa',async({page,baseURL})=>{
  test.skip(!baseURL.includes(':8081'),'Use E2E_BASE_URL=http://localhost:8081 (banco isolado).');
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const account=testAccount('workflow');
  await page.goto('/');await page.getByLabel('E-mail ou usuário',{exact:true}).fill(account.email);
  await page.getByLabel('Senha',{exact:true}).fill(account.password);
  const setupResponse=page.waitForResponse(r=>r.url().endsWith('/api/auth/enrollment'));
  await page.getByRole('button',{name:'Continuar',exact:true}).click();
  const setup=await (await setupResponse).json();
  await page.getByLabel('Código de autenticação').fill(totp(setup.secret));
  await page.getByRole('button',{name:'Ativar autenticador e entrar'}).click();
  await expect(page.getByRole('heading',{name:'Dashboard Analítico & Operacional'})).toBeVisible();
  const startingProfit=await page.evaluate(async()=>Number((await (await fetch('/api/dashboard')).json()).month_realized_profit));
  await page.getByRole('link',{name:'Produtos',exact:true}).click();await page.getByRole('button',{name:'Categorias',exact:true}).click();await page.getByRole('button',{name:'+ Nova categoria'}).click();
  const suffix=Date.now().toString();const category='Categoria QA '+suffix,product='Produto QA '+suffix,customer='Cliente QA '+suffix;
  await page.getByLabel('Nome da categoria *',{exact:true}).fill(category);await page.getByRole('button',{name:'Salvar',exact:true}).click();await expect(page.getByRole('dialog')).not.toBeVisible();
  await page.getByRole('button',{name:'Novo produto',exact:true}).click();
  await page.getByLabel('Nome *',{exact:true}).fill(product);await page.getByRole('combobox',{name:'Categoria *',exact:true}).selectOption({label:category});
  await page.locator('#dialog [name=price]').fill('20.00');await page.locator('#dialog [name=purchasePrice]').fill('10.00');await page.getByLabel('Estoque *',{exact:true}).fill('10');
  await page.getByRole('button',{name:'Salvar',exact:true}).click();await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.getByRole('row').filter({hasText:product})).toContainText('10');
  await page.getByLabel('Buscar').fill(category);await page.getByRole('button',{name:'Filtrar'}).click();await expect(page.getByRole('row').filter({hasText:product})).toBeVisible();
  await page.getByRole('button',{name:`Ações de ${product}`}).click();await page.getByRole('button',{name:'Ver detalhes'}).click();await expect(page.getByRole('dialog')).toContainText('Valor agregado em estoque');await expect(page.getByRole('dialog')).toContainText(/100,00/);await page.locator('.detail-actions button').click();
  await page.getByRole('button',{name:`Ações de ${product}`}).click();await page.getByRole('button',{name:'Ocultar produto'}).click();await expect(page.getByRole('row').filter({hasText:product})).toContainText('Oculto');
  await page.getByLabel('Situação').selectOption({label:'Ocultos'});await page.getByRole('button',{name:'Filtrar'}).click();await expect(page.getByRole('row').filter({hasText:product})).toContainText('Oculto');
  await page.getByRole('button',{name:`Ações de ${product}`}).click();await page.getByRole('button',{name:'Mostrar produto'}).click();await expect(page.getByRole('row').filter({hasText:product})).toHaveCount(0);
  await page.getByRole('link',{name:'Clientes',exact:true}).click();await page.getByRole('button',{name:'Novo cliente'}).click();
  for(const [label,value] of [['Nome *',customer],['Rua *','Rua de teste, 10'],['Bairro *','Centro'],['Cidade-UF *','São Paulo-SP']])await page.getByLabel(label,{exact:true}).fill(value);
  await page.getByRole('button',{name:'Salvar',exact:true}).click();await expect(page.getByRole('dialog')).not.toBeVisible();
  await page.getByRole('row').filter({hasText:customer}).getByRole('button',{name:`Gerenciar cliente ${customer}`}).click();await page.getByRole('button',{name:'Ver cliente'}).click();
  await page.getByRole('button',{name:'+ Nova venda',exact:true}).click();await page.getByRole('combobox',{name:'Produto *',exact:true}).selectOption({label:product+' · estoque: 10'});
  await page.getByLabel('Quantidade *',{exact:true}).fill('4');await page.getByLabel('Recebido agora').fill('0');
  await page.getByRole('button',{name:'Confirmar pedido'}).click();await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.getByRole('heading',{name:/Pedido /})).toBeVisible();
  await expect(page.locator('main')).toContainText('Aguardando entrega');
  await page.getByRole('button',{name:'Confirmar saída / entrega'}).click();await page.getByRole('button',{name:'Confirmar entrega integral'}).click();await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.locator('main')).toContainText('Entregue');
  await page.getByRole('button',{name:'Registrar recebimento'}).click();await page.getByLabel('Valor recebido').fill('30');await page.getByRole('button',{name:'Confirmar recebimento'}).click();await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.locator('main')).toContainText('Parcialmente pago');
  await page.getByRole('button',{name:'Registrar recebimento'}).click();await page.getByLabel('Valor recebido').fill('50');await page.getByRole('button',{name:'Confirmar recebimento'}).click();await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.getByRole('button',{name:'Registrar recebimento'})).not.toBeVisible();
  await page.getByRole('link',{name:'Dashboard',exact:true}).click();await expect(page.locator('main')).toContainText('Lucro bruto · mês');await expect.poll(async()=>Number(await page.evaluate(async()=>Number((await (await fetch('/api/dashboard')).json()).month_realized_profit)))-startingProfit).toBe(40);await expect(page.getByRole('heading',{name:/Pedidos & Vendas de Hoje/})).toBeVisible();await expect(page.getByRole('button',{name:'Novo produto'})).toHaveCount(0);
  await expect(page.locator('#notice')).toHaveText('',{timeout:8000});
  mkdirSync('.local/screenshots',{recursive:true});await page.screenshot({path:'.local/screenshots/dashboard-desktop.png',fullPage:true});
  await page.getByRole('link',{name:'Caixa',exact:true}).click();await page.locator('.reference-tag').first().click();
  await expect(page.getByRole('heading',{name:/Pedido /})).toBeVisible();await expect(page.locator('main')).toContainText(product);
  await page.getByRole('link',{name:'Caixa',exact:true}).click();await page.getByRole('button',{name:'Lançamento',exact:true}).click();
  await page.getByLabel('Valor *',{exact:true}).fill('12.00');await page.getByLabel('Descrição / motivo').fill('Frete QA '+suffix);await page.getByRole('button',{name:'Registrar lançamento'}).click();await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.locator('main')).toContainText('Frete QA '+suffix);
  await expect(page.locator('#notice')).toHaveText('',{timeout:8000});
  mkdirSync('.local/screenshots',{recursive:true});await page.screenshot({path:'.local/screenshots/finance-desktop.png',fullPage:true});
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:'.local/screenshots/finance-mobile.png',fullPage:true});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
  await page.getByRole('button',{name:'Alternar tema'}).click();await page.screenshot({path:'.local/screenshots/finance-light-mobile.png',fullPage:true});
  await page.setViewportSize({width:1280,height:900});
  await page.getByRole('link',{name:'Produtos',exact:true}).click();await page.getByLabel('Buscar').fill(product);await page.getByRole('button',{name:'Filtrar'}).click();await page.getByRole('button',{name:`Ações de ${product}`}).click();await page.getByRole('button',{name:'Excluir produto'}).click();
  await page.locator('#dialog [name=confirmationName]').fill('nome incorreto');await page.getByRole('button',{name:'Excluir produto',exact:true}).click();await expect(page.locator('#dialog')).toContainText('não corresponde');
  await page.locator('#dialog [name=confirmationName]').fill(product);await page.getByRole('button',{name:'Excluir produto',exact:true}).click();await expect(page.locator('#dialog')).not.toBeVisible();await expect(page.getByRole('row').filter({hasText:product})).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('conta criada no banco configura QR no primeiro acesso e exige código no próximo',async({page,baseURL})=>{
  test.skip(!baseURL.includes(':8081'),'Somente banco isolado.');
  const {email,password}=testAccount('enrollment');
  await page.goto('/');
  await page.getByLabel('E-mail ou usuário').fill(email);await page.getByLabel('Senha',{exact:true}).fill(password);
  const enrollmentResponse=page.waitForResponse(r=>r.url().endsWith('/api/auth/enrollment'));
  await page.getByRole('button',{name:'Continuar',exact:true}).click();
  const enrollment=await (await enrollmentResponse).json();
  await expect(page.getByRole('heading',{name:'Configure seu autenticador'})).toBeVisible();
  await expect(page.getByRole('img',{name:'QR Code para configurar o autenticador'})).toBeVisible();
  expect(await page.evaluate(async()=> (await fetch('/api/products')).status)).toBe(401);
  await page.getByLabel('Código de autenticação').fill(totp(enrollment.secret));
  await page.getByRole('button',{name:'Ativar autenticador e entrar'}).click();
  await expect(page.getByRole('heading',{name:'Dashboard Analítico & Operacional'})).toBeVisible();
  await page.getByRole('button',{name:'Sair',exact:true}).click();
  await page.getByLabel('E-mail ou usuário').fill(email);await page.getByLabel('Senha',{exact:true}).fill(password);
  await page.getByRole('button',{name:'Continuar',exact:true}).click();
  await expect(page.getByRole('heading',{name:'Confirme seu acesso'})).toBeVisible();
  await expect(page.getByRole('img',{name:'QR Code para configurar o autenticador'})).toHaveCount(0);
  await page.getByLabel('Código de autenticação').fill(totp(enrollment.secret,1));
  await page.getByRole('button',{name:'Entrar com segurança'}).click();
  await expect(page.getByRole('heading',{name:'Dashboard Analítico & Operacional'})).toBeVisible();
});
