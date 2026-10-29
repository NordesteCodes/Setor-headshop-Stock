const $ = (s, root = document) => root.querySelector(s);
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
}[c]));
const money = value => new Intl.NumberFormat('pt-BR', {style: 'currency', currency: 'BRL'}).format(Number(value || 0));
const date = value => value ? new Date(value).toLocaleString('pt-BR', {timeZone: 'America/Sao_Paulo'}) : '—';
const uuid = () => crypto.randomUUID();
let csrf, user, page = 0, route = '', records = [], filters = {search: '', filter: '', sort: ''};
let categories = [], products = [], customers = [];
let dashboardData;
let selectedCustomerId = '';
const dashboardView = {period: 'month', status: 'all', page: 0};
const count = value => new Intl.NumberFormat('pt-BR').format(Number(value || 0));
const dialog = $('#dialog');
document.documentElement.dataset.theme = localStorage.getItem('theme') || 'dark';

async function api(path, options = {}) {
    const response = await fetch(`/api${path}`, {
        ...options,
        headers: {'Content-Type': 'application/json', ...(csrf ? {[csrf.header]: csrf.token} : {}), ...options.headers}
    });
    if (response.status === 204) return null;
    const data = await response.json().catch(() => ({message: 'Resposta inválida do servidor.'}));
    if (!response.ok) {
        const error = new Error(data.message || 'Não foi possível concluir.');
        error.status = response.status;
        throw error;
    }
    return data;
}

async function command(path, body, method = 'POST') {
    const payload = JSON.stringify(body);
    const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(method + path + payload));
    const signature = 'command:' + Array.from(new Uint8Array(bytes), b => b.toString(16).padStart(2, '0')).join('');
    const key = sessionStorage.getItem(signature) || uuid();
    sessionStorage.setItem(signature, key);
    try {
        const result = await api(path, {method, body: payload, headers: {'Idempotency-Key': key}});
        sessionStorage.removeItem(signature);
        return result;
    } catch (error) {
        if (error.status && error.status < 500) sessionStorage.removeItem(signature);
        throw error;
    }
}

function notify(text) {
    $('#notice').textContent = text;
    setTimeout(() => $('#notice').textContent = '', 6000);
}

function metric(label, value, note = '', symbol = 'chart', tone = '') {
    return `<article class="metric ${tone}"><div class="metric-heading"><small>${esc(label)}</small><span class="metric-icon">${icon(symbol)}</span></div><strong>${value}</strong><p>${esc(note)}</p></article>`;
}

function debtMetric(label, value, note = '') {
    return metric(label, money(value), note, 'wallet', Number(value) > 0 ? 'tone-yellow' : 'tone-green');
}

function pendingMetric(value, note = '') {
    return metric('Entregas pendentes', `<span class="${Number(value) > 0 ? 'attention-pulse' : ''}">${count(value)}</span>`, note, 'truck', Number(value) > 0 ? 'tone-orange' : 'tone-green');
}

function interactiveChart(svg) {
    return `<div class="interactive-chart">${svg}<div class="chart-tooltip" role="status" hidden></div></div>`;
}

function badge(text, kind = '') {
    return `<span class="badge ${kind}">${esc(text)}</span>`;
}

function payment(s) {
    return Number(s.paid) === 0 ? badge('Pendente', 'warn') : Number(s.paid) < Number(s.total) ? badge('Parcialmente pago', 'warn') : badge('Pago', 'good');
}

function delivery(s) {
    return s.delivered_at ? badge('Entregue', 'good') : badge('Aguardando entrega', 'warn');
}

function table(headers, rows, emptyText = 'Nenhum registro encontrado. Os dados aparecerão após o primeiro cadastro.') {
    return `<div class="table-wrap"><table><thead><tr>${headers.map(h => `<th>${h}</th>`).join('')}</tr></thead><tbody>${rows.length ? rows.join('') : `<tr><td colspan="${headers.length}" class="empty">${esc(emptyText)}</td></tr>`}</tbody></table></div>`;
}

function input(label, name, value = '', type = 'text', extra = '') {
    return `<label>${label}<input name="${name}" type="${type}" value="${esc(value)}" ${extra}></label>`;
}

function moneyCents(value, label = 'Valor') {
    const text = String(value ?? '').trim().replace(/^R\$\s*/i, '').replace(/[\s\u00a0]/g, '');
    let normalized;
    if (text.includes(',')) {
        if (!/^(?:\d+|\d{1,3}(?:\.\d{3})+),\d{1,2}$/.test(text)) throw new Error(`${label}: use um valor como 2.150,99.`);
        normalized = text.replaceAll('.', '').replace(',', '.');
    } else if (/^\d{1,3}(?:\.\d{3})+$/.test(text)) normalized = text.replaceAll('.', '');
    else if (/^\d+(?:\.\d{1,2})?$/.test(text)) normalized = text;
    else throw new Error(`${label}: use um valor como 2.150,99.`);
    const [whole, fraction = ''] = normalized.split('.');
    const cents = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
    if (cents > 999999999999999999n) throw new Error(`${label}: valor acima do limite permitido.`);
    return cents;
}

function decimalMoney(cents) {
    return `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}`;
}

function localizedMoney(cents) {
    return `${(cents / 100n).toLocaleString('pt-BR')},${String(cents % 100n).padStart(2, '0')}`;
}

function currencyInput(label, name, value = '0.00', extra = '') {
    const attributes = extra.replace(/\s*(?:min|max|step)="[^"]*"/g, '');
    const formatted = value === '' ? '' : localizedMoney(moneyCents(value, label));
    return `<label>${label}<span class="currency-input"><span aria-hidden="true">R$</span><input name="${name}" type="text" inputmode="decimal" data-money data-money-label="${esc(label.replace(/\s*\*/g, ''))}" maxlength="24" value="${esc(formatted)}" ${attributes}></span></label>`;
}

function select(label, name, items, value = '', extra = '') {
    return `<label>${label}<select name="${name}" ${extra}>${items.map(([id, text]) => `<option value="${esc(id)}" ${String(id) === String(value) ? 'selected' : ''}>${esc(text)}</option>`).join('')}</select></label>`;
}

function requiredSelection(label, name, items, value = '', placeholder = 'Selecione') {
    return select(label, name, [['', placeholder], ...items], value, 'required');
}

function formEnd(text = 'Salvar') {
    return `<p class="error span2" role="alert"></p><div class="form-actions span2"><button type="button" data-close>Cancelar</button><button class="primary">${text}</button></div>`;
}

function modal(title, html, submit) {
    dialog.classList.remove('side-drawer');
    dialog.innerHTML = `<div class="dialog-head"><h2 id="dialog-title">${title}</h2><button type="button" data-close aria-label="Fechar">✕</button></div>${html}`;
    if (!dialog.open) dialog.showModal();
    dialog.querySelectorAll('[data-close]').forEach(b => b.onclick = () => dialog.close());
    const form = $('form', dialog);
    if (form) form.noValidate = true;
    if (form) form.onsubmit = async event => {
        event.preventDefault();
        if (form.dataset.busy) return;
        const errorText = $('.error', form);
        errorText.textContent = '';
        if (!form.reportValidity()) {
            errorText.textContent = 'Confira os campos destacados antes de confirmar.';
            return;
        }
        form.dataset.busy = 'true';
        const buttons = form.querySelectorAll('button');
        try {
            const values = Object.fromEntries(new FormData(form));
            for (const field of form.querySelectorAll('[data-money]')) {
                try {
                    values[field.name] = decimalMoney(moneyCents(field.value, field.dataset.moneyLabel));
                } catch (error) {
                    field.focus();
                    throw error;
                }
            }
            buttons.forEach(b => b.disabled = true);
            const result = await submit(values, form);
            dialog.close();
            notify('Operação registrada.');
            if (result?.route && location.hash !== `#${result.route}`) location.hash = result.route;
            else await render();
        } catch (e) {
            errorText.textContent = e.message + (e instanceof TypeError && !e.status ? ' Verifique a conexão e tente novamente.' : '');
        } finally {
            delete form.dataset.busy;
            buttons.forEach(b => b.disabled = false);
        }
    };
}

function drawer(title, html) {
    dialog.classList.add('side-drawer');
    dialog.innerHTML = `<div class="dialog-head"><h2 id="dialog-title">${title}</h2><button type="button" data-close aria-label="Fechar">✕</button></div>${html}`;
    if (!dialog.open) dialog.showModal();
    dialog.querySelectorAll('[data-close]').forEach(b => b.onclick = () => dialog.close());
}

function shell() {
    const links = [['dashboard', 'grid', 'Dashboard'], ['products', 'box', 'Produtos'], ['stock-movements', 'transfer', 'Entradas & Saídas'], ['customers', 'users', 'Clientes'], ['sales', 'truck', 'Vendas & Entregas'], ['finance', 'wallet', 'Caixa']];
    $('#app').innerHTML = `<aside class="sidebar"><div class="brand"><span class="brand-icon">${icon('box')}</span><span>Setor Headshop<small>ESTOQUE INTERNO</small></span><i aria-hidden="true"></i></div><nav>${links.map(([id, symbol, label]) => `<a href="#${id}" data-route="${id}"><span class="nav-icon" aria-hidden="true">${icon(symbol)}</span>${label}</a>`).join('')}</nav></aside><div class="shell"><header class="topbar"><button class="mobile-menu" data-action="menu" aria-label="Abrir navegação">☰</button><small class="context">${icon('box')} OPERAÇÃO <span>/</span> <b>CONTROLE DE ESTOQUE</b></small><div class="account"><button data-action="theme" aria-label="Alternar tema">◐</button><span class="account-avatar" title="${esc(user.username)}">${esc(user.username.slice(0, 1).toUpperCase())}</span><button data-action="logout">Sair</button></div></header><main id="main"></main></div>`;
}

function title(name, description, actions = '') {
    return `<div class="page-title"><div><p class="eyebrow"><span class="operational-pill"><i></i>Painel operacional</span></p><h1>${name}</h1><p class="subtitle">${description}</p></div><div class="actions">${actions}</div></div>`;
}

function toolbar(options = [], sorts = [], placeholder = 'Nome ou identificação') {
    return `<form id="filters" class="toolbar filter-strip">${input('Buscar', 'search', filters.search, 'search', `maxlength="150" placeholder="${placeholder}"`)}${options.length ? select('Situação', 'filter', [['', 'Todos'], ...options], filters.filter) : ''}${sorts.length ? select('Ordenar', 'sort', sorts, filters.sort) : ''}<button>${icon('filter')}Filtrar</button><button type="button" class="filter-reset" data-action="clear-filters" aria-label="Limpar filtros">${icon('refresh')}</button></form>`;
}

function pager(data) {
    return `<div class="pagination"><span>${data.total ? `${page * data.size + 1}–${Math.min((page + 1) * data.size, data.total)} de ${count(data.total)}` : '0'} registros</span><div class="actions"><button data-action="prev" aria-label="Página anterior" ${page === 0 ? 'disabled' : ''}>←</button><span class="current-page mono">${page + 1}</span><span class="muted">/ ${Math.max(1, Math.ceil(data.total / data.size))}</span><button data-action="next" aria-label="Próxima página" ${(page + 1) * data.size >= data.total ? 'disabled' : ''}>→</button></div></div>`;
}

async function list(resource, related) {
    return api(`/${resource}?${new URLSearchParams({...filters, page, size: 20, ...(related ? {related} : {})})}`);
}

async function lookups() {
    categories = (await api('/categories?size=100')).items;
}

async function all(resource) {
    let items = [], p = 0;
    while (true) {
        const r = await api(`/${resource}?size=100&page=${p}${resource === 'products' ? '&filter=active' : ''}`);
        items.push(...r.items);
        if (items.length >= r.total) return items;
        p++;
    }
}

async function render() {
    const current = location.hash.slice(1) || 'dashboard';
    if (current !== route) {
        route = current;
        page = 0;
        filters = {search: '', filter: '', sort: ''};
    }
    document.querySelectorAll('[data-route]').forEach(a => a.classList.toggle('active', a.dataset.route === route.split('/')[0]));
    $('.sidebar')?.classList.remove('open');
    const main = $('#main');
    if (!main) return;
    main.innerHTML = '<p class="loading" role="status">Carregando dados…</p>';
    try {
        if (route === 'dashboard' || route === 'finance') {
            await dashboard(main, route === 'finance');
        } else if (route === 'products') await productList(main);
        else if (route === 'customers') await customerList(main);
        else if (route.startsWith('customers/')) await customerDetail(main, route.split('/')[1]);
        else if (route === 'sales') await saleList(main);
        else if (route.startsWith('sales/')) await saleDetail(main, route.split('/')[1]);
        else if (route === 'stock-movements') await movements(main);
        else main.innerHTML = '<p>Página não encontrada.</p>';
        const form = $('#filters');
        if (form) form.onsubmit = e => {
            e.preventDefault();
            filters = {...filters, ...Object.fromEntries(new FormData(form))};
            page = 0;
            render();
        };
    } catch (e) {
        if (e.status === 401) {
            await loginScreen();
            return;
        }
        main.innerHTML = `<div class="panel"><h2>Não foi possível carregar</h2><p class="error">${esc(e.message)}</p><button data-action="retry">Tentar novamente</button></div>`;
    }
}

function icon(name) {
    const paths = {
        box: '<path d="m12 3 9 5-9 5-9-5 9-5Zm-9 5v9l9 5 9-5V8M12 13v9M7.5 5.5l9 5"/>',
        chart: '<path d="M4 19V5m0 14h16M8 15l4-5 4 3 4-7"/>',
        wallet: '<path d="M20 8V5H5a2 2 0 0 0 0 4h16v10H5a2 2 0 0 1-2-2V7m18 5h-6v4h6M17 14h.01"/>',
        receipt: '<path d="M6 3h12v18l-3-2-3 2-3-2-3 2V3Zm3 5h6m-6 4h6"/>',
        transfer: '<path d="M4 7h15m-4-4 4 4-4 4M20 17H5m4-4-4 4 4 4"/>',
        truck: '<path d="M3 5h11v12H3V5Zm11 5h4l3 4v3h-7M7 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4Zm10 0a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z"/>',
        users: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m20 0v-2a4 4 0 0 0-3-3.87M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm8-7.87a4 4 0 0 1 0 7.75"/>',
        grid: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
        plus: '<path d="M12 5v14M5 12h14"/>',
        arrow: '<path d="M4 12h16m-6-6 6 6-6 6"/>',
        pie: '<circle cx="12" cy="12" r="9"/><path d="M12 3v9h9M12 12v9"/>',
        clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
        filter: '<path d="M3 5h18M6 12h12m-9 7h6"/>',
        refresh: '<path d="M20 7a8 8 0 1 0 1 8M20 3v5h-5"/>',
        download: '<path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"/>'
    };
    return `<svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || paths.box}</svg>`;
}

function dashboardCard(label, value, note, symbol, tone = '', footer = '') {
    return `<article class="overview-card ${tone}"><div class="overview-card-head"><span>${label}</span><span class="overview-card-icon">${icon(symbol)}</span></div><strong>${value}</strong><p>${note}</p><div class="overview-card-foot">${footer}</div></article>`;
}

function dayLabel(value, options = {day: '2-digit', month: 'short'}) {
    return new Date(`${String(value).slice(0, 10)}T12:00:00-03:00`).toLocaleDateString('pt-BR', options).replaceAll('.', '');
}

function revenueChart(days) {
    const width = 720, top = 22, bottom = 224, left = 66, right = 700;
    const values = days.flatMap(d => [Number(d.revenue), Number(d.gross_profit)]);
    const max = Math.max(1, ...values), min = Math.min(0, ...values);
    const y = value => bottom - (value - min) / (max - min) * (bottom - top);
    const x = i => days.length === 1 ? (left + right) / 2 : left + i / Math.max(1, days.length - 1) * (right - left);
    const points = key => days.map((d, i) => [x(i), y(Number(d[key]))]);
    const curve = points => points.map(([px, py], i) => {
        if (!i) return `M${px},${py}`;
        const [prevX, prevY] = points[i - 1], gap = (px - prevX) / 3;
        return `C${prevX + gap},${prevY} ${px - gap},${py} ${px},${py}`;
    }).join(' ');
    const grid = Array.from({length: 5}, (_, i) => {
        const value = max - (max - min) * i / 4;
        const label = new Intl.NumberFormat('pt-BR', {notation: 'compact', maximumFractionDigits: 1}).format(value);
        return `<g><line class="analytics-grid" x1="${left}" y1="${y(value)}" x2="${right}" y2="${y(value)}"/><text class="analytics-axis" x="${left - 12}" y="${y(value) + 4}" text-anchor="end">${esc(label)}</text></g>`;
    }).join('');
    const dateStep = Math.max(1, Math.ceil((days.length - 1) / 6));
    const labels = days.map((d, i) => i % dateStep === 0 || i === days.length - 1 ? `<text class="analytics-axis ${i === days.length - 1 ? 'latest-date' : ''}" x="${x(i)}" y="253" text-anchor="middle">${esc(dayLabel(d.activity_date))}</text>` : '').join('');
    const lines = ['revenue', 'gross_profit'].map((key, series) => {
        const p = points(key), path = curve(p);
        if (!p.length) return '';
        const area = `${path} L${p.at(-1)[0]},${y(0)} L${p[0][0]},${y(0)} Z`;
        return `${p.length > 1 ? `<path d="${area}" fill="url(#overview-fill-${series})"/><path class="analytics-line series-${series}" d="${path}"/>` : ''}${p.map(([px, py], i) => `<circle class="analytics-point series-${series} ${p.length === 1 ? 'single-point' : ''}" cx="${px}" cy="${py}" r="${p.length === 1 ? 4 : 3}"><title>${esc(dayLabel(days[i].activity_date))} · ${series ? 'Lucro bruto' : 'Faturamento'}: ${esc(money(days[i][key]))}</title></circle>`).join('')}`;
    }).join('');
    const hitWidth = (right - left) / Math.max(1, days.length - 1);
    const hits = days.map((d, i) => {
        const start = days.length === 1 ? left : Math.max(left, x(i) - hitWidth / 2);
        const end = days.length === 1 ? right : Math.min(right, x(i) + hitWidth / 2);
        const tip = `${dayLabel(d.activity_date)}\nFaturamento: ${money(d.revenue)}\nLucro bruto: ${money(d.gross_profit)}`;
        return `<g class="chart-hover-target" tabindex="0" data-chart-tip="${esc(tip)}" role="img" aria-label="${esc(tip)}"><rect class="chart-hit" x="${start}" y="${top}" width="${end - start}" height="${bottom - top}"/><line class="chart-hover-line" x1="${x(i)}" y1="${top}" x2="${x(i)}" y2="${bottom}"/></g>`;
    }).join('');
    return interactiveChart(`<svg class="revenue-chart" viewBox="0 0 ${width} 270" role="group" aria-label="Faturamento e lucro bruto diário de pedidos entregues e quitados"><defs><linearGradient id="overview-fill-0" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#4edea3" stop-opacity=".3"/><stop offset="100%" stop-color="#4edea3" stop-opacity="0"/></linearGradient><linearGradient id="overview-fill-1" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#ffb95f" stop-opacity=".22"/><stop offset="100%" stop-color="#ffb95f" stop-opacity="0"/></linearGradient></defs>${grid}${lines}${hits}${labels}</svg>`);
}

function compositionChart(d) {
    const parts = [
        {label: 'Estoque físico', value: Number(d.stock_value), color: 'stock'},
        {label: 'Caixa', value: Math.max(0, Number(d.cash)), color: 'cash'},
        {label: 'A receber', value: Number(d.receivables), color: 'due'}
    ];
    const total = parts.reduce((sum, p) => sum + p.value, 0), circumference = 2 * Math.PI * 72;
    let offset = 0;
    const segments = parts.map(p => {
        const length = total ? p.value / total * circumference : 0;
        const tip = `${p.label}\n${money(p.value)}\n${total ? (p.value / total * 100).toLocaleString('pt-BR', {maximumFractionDigits: 1}) : '0'}% da composição`;
        const segment = `<circle class="donut-segment composition-${p.color}" cx="100" cy="100" r="72" stroke-dasharray="${length} ${circumference - length}" stroke-dashoffset="${-offset}" transform="rotate(-90 100 100)" ${length ? `tabindex="0" role="img" aria-label="${esc(tip)}" data-chart-tip="${esc(tip)}"` : ''}><title>${p.label}: ${esc(money(p.value))}</title></circle>`;
        offset += length;
        return segment;
    }).join('');
    const share = total ? Math.round(parts[0].value / total * 100) : 0;
    return `<section class="analytics-panel composition-panel"><div class="analytics-heading"><h2>${icon('pie')}Composição de valores</h2><span class="mono">${money(total)}</span></div><div class="donut-wrap">${interactiveChart(`<svg viewBox="0 0 200 200" role="group" aria-label="Distribuição entre estoque, caixa positivo e valores a receber"><circle class="donut-track" cx="100" cy="100" r="72"/>${segments}<text class="donut-caption" x="100" y="94" text-anchor="middle">${total ? 'Estoque' : 'Sem valores'}</text><text class="donut-value" x="100" y="119" text-anchor="middle">${total ? `${share}%` : '—'}</text></svg>`)}</div><ul class="composition-legend">${parts.map(p => `<li><span><i class="composition-${p.color}"></i>${p.label}</span><b class="mono">${money(p.value)}</b></li>`).join('')}</ul>${Number(d.cash) < 0 ? `<p class="chart-note bad">Caixa negativo: ${money(d.cash)}. Excluído da composição.</p>` : ''}<p class="analytics-note">Posição atual · estoque avaliado pelo custo médio.</p></section>`;
}

function movementChart(days) {
    const width = 720, baseline = 144, barMax = 110;
    const max = Math.max(1, ...days.flatMap(d => [Number(d.inbound), Number(d.outbound), Number(d.damage)]));
    const step = 650 / Math.max(1, days.length), barWidth = Math.min(12, step / 4);
    const stride = Math.max(1, Math.ceil((days.length - 1) / 10));
    const marks = days.map((d, i) => {
        const px = 45 + step * (i + .5);
        return `<g><title>${esc(dayLabel(d.activity_date))}: ${count(d.inbound)} entradas, ${count(d.outbound)} vendas, ${count(d.damage)} avarias</title>${['inbound', 'outbound', 'damage'].map((key, j) => {
            const height = Number(d[key]) / max * barMax;
            return `<rect class="movement-${key}" x="${px + (j - 1.5) * barWidth}" y="${baseline - height}" width="${Math.max(1, barWidth - 1)}" height="${height}" rx="1.5"/>`;
        }).join('')}${i % stride === 0 || i === days.length - 1 ? `<text class="analytics-axis" x="${px}" y="167" text-anchor="middle">${esc(dayLabel(d.activity_date, {day: '2-digit'}))}</text>` : ''}</g>`;
    }).join('');
    const hits = days.map((d, i) => {
        const tip = `${dayLabel(d.activity_date)}\nEntradas: ${count(d.inbound)}\nVendas: ${count(d.outbound)}\nAvarias: ${count(d.damage)}`;
        return `<g class="chart-hover-target" tabindex="0" data-chart-tip="${esc(tip)}" role="img" aria-label="${esc(tip)}"><rect class="chart-hit" x="${45 + step * i}" y="20" width="${step}" height="${baseline - 20}"/></g>`;
    }).join('');
    return interactiveChart(`<svg class="movement-chart" viewBox="0 0 ${width} 180" role="group" aria-label="Entradas, saídas por vendas e avarias por dia"><line class="analytics-grid" x1="40" y1="${baseline}" x2="700" y2="${baseline}"/>${marks}${hits}</svg>`);
}

function renderOverview(main, d) {
    const dateParts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
        timeZone: 'America/Sao_Paulo',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit'
    }).formatToParts(new Date()).map(p => [p.type, p.value]));
    const today = `${dateParts.year}-${dateParts.month}-${dateParts.day}`;
    const month = today.slice(0, 7);
    const activity = d.daily_activity || [];
    const days = dashboardView.period === 'today' ? activity.slice(-1) : dashboardView.period === 'week' ? activity.slice(-7) : activity.filter(x => String(x.activity_date).startsWith(month));
    const orders = d.today_orders || [];
    records = orders;
    const sum = (rows, key) => rows.reduce((total, row) => total + Number(row[key] || 0), 0);
    const totalToday = sum(orders, 'total'), receivedToday = sum(orders, 'paid');
    const monthlyRevenue = Number(d.month_realized_revenue);
    const realizedProfit = Number(d.month_realized_profit);
    const realizedCost = monthlyRevenue - realizedProfit;
    const profitOnCost = realizedCost > 0 ? `${(realizedProfit / realizedCost * 100).toLocaleString('pt-BR', {maximumFractionDigits: 1})}%` : '—';
    const outbound = Math.abs(Number(d.month_outbound)), damage = Math.abs(Number(d.month_damage));
    const periodLabels = {today: 'Hoje', week: 'Últimos 7 dias', month: 'Mês vigente'};
    const filtered = orders.filter(s => dashboardView.status === 'all' || dashboardView.status === 'paid' && Number(s.paid) >= Number(s.total) || dashboardView.status === 'delivery' && !s.delivered_at || dashboardView.status === 'debt' && Number(s.paid) < Number(s.total));
    const pageSize = 5, pageCount = Math.max(1, Math.ceil(filtered.length / pageSize));
    dashboardView.page = Math.min(dashboardView.page, pageCount - 1);
    const visible = filtered.slice(dashboardView.page * pageSize, (dashboardView.page + 1) * pageSize);
    const peak = days.reduce((best, day) => !best || Number(day.revenue) > Number(best.revenue) ? day : best, null);
    const signed = value => `${value > 0 ? '+' : ''}${count(value)}`;
    const tabs = [['all', 'Todos', orders.length], ['paid', 'Pagos', orders.filter(s => Number(s.paid) >= Number(s.total)).length], ['delivery', 'Aguardando entrega', orders.filter(s => !s.delivered_at).length], ['debt', 'Pagamento pendente', orders.filter(s => Number(s.paid) < Number(s.total)).length]];
    main.innerHTML = `<div class="overview">
        <div class="overview-header"><div><p class="eyebrow">${icon('chart')} Visão analítica · resumo da operação</p><h1>Dashboard Analítico &amp; Operacional</h1><p class="subtitle">Estoque, lucro e pedidos. Uma visão clara da sua operação.</p></div><div class="overview-controls"><div class="period-switch" role="group" aria-label="Período dos gráficos">${[['today', 'Hoje'], ['week', '7 dias'], ['month', 'Este mês']].map(([id, label]) => `<button data-action="dashboard-period" data-value="${id}" aria-pressed="${dashboardView.period === id}">${label}</button>`).join('')}</div><div class="actions"><button class="primary" data-action="new-sale">${icon('plus')}Nova venda</button></div></div></div>
        <section class="overview-metrics" aria-label="Indicadores da operação">
            ${dashboardCard('Valor em estoque', money(d.stock_value), 'Valor de compra dos produtos em estoque', 'box', 'tone-amber', '<span>Custo médio ponderado</span>')}
            ${dashboardCard('Contas a receber', money(d.receivables), `${icon('clock')}${count(d.pending_payment_count)} pedidos com saldo pendente`, 'receipt', 'tone-amber', '<a href="#sales">Conferir recebimentos →</a>')}
            ${dashboardCard('Lucro bruto · mês', `<span class="${realizedProfit < 0 ? 'bad' : 'good'}">${money(realizedProfit)}</span>`, `Lucro sobre o custo: <b>${profitOnCost}</b>`, 'wallet', 'tone-green', '<span>Vendas entregues e quitadas</span>')}
            ${dashboardCard('Total de produtos no estoque', count(d.stock_quantity), 'Quantidade física atual', 'box', 'tone-blue', `<span class="stock-sales"><i class="dot green"></i>Produtos vendidos no mês: ${count(outbound)}</span><span class="stock-damage"><i class="dot red"></i>Avarias encontradas: ${count(damage)}</span>`)}
            ${dashboardCard('Pedidos de hoje', count(orders.length), `Total em pedidos: <b>${money(totalToday)}</b>`, 'truck', '', `<span>${count(d.pending_deliveries)} aguardando entrega no total</span>`)}
        </section>
        <div class="overview-analytics">
            <section class="analytics-panel revenue-panel"><div class="analytics-heading"><div><h2>${icon('chart')}Curva de Faturamento &amp; Lucro</h2><p>${periodLabels[dashboardView.period]} · vendas entregues e quitadas</p></div><div class="series-legend"><span><i class="dot green"></i>Faturamento</span><span><i class="dot amber"></i>Lucro bruto</span></div></div>${revenueChart(days)}<div class="analytics-footer"><span>Média diária: <b class="mono">${money(days.length ? sum(days, 'revenue') / days.length : 0)}</b></span><span>${peak && Number(peak.revenue) > 0 ? `Maior faturamento: <b>${esc(dayLabel(peak.activity_date))} · ${money(peak.revenue)}</b>` : 'Sem vendas entregues e quitadas no período'}</span></div><p class="analytics-note">Lucro bruto = venda − custo dos produtos. Despesas operacionais não incluídas.</p></section>
            ${compositionChart(d)}
            <section class="analytics-panel movement-panel"><div class="movement-intro"><h2>${icon('transfer')}Giro de produtos</h2><p>${periodLabels[dashboardView.period]}</p><strong class="mono">${signed(sum(days, 'inbound') - sum(days, 'outbound') - sum(days, 'damage'))}</strong><span class="muted">saldo físico no período</span><div class="series-legend"><span><i class="dot green"></i>Entradas</span><span><i class="dot green"></i>Vendas</span><span><i class="dot red"></i>Avarias</span></div></div><div class="movement-plot">${movementChart(days)}</div></section>
        </div>
        <section class="analytics-panel overview-orders"><div class="orders-heading"><div><h2>Pedidos &amp; Vendas de Hoje <span class="order-count">${count(orders.length)} pedidos</span></h2><p>Faturamento em pedidos: <b class="mono">${money(totalToday)}</b><span class="text-divider">·</span>Total recebido: <b class="mono good">${money(receivedToday)}</b></p></div><div class="order-tabs" role="group" aria-label="Filtrar pedidos de hoje">${tabs.map(([id, label, total]) => `<button data-action="dashboard-status" data-value="${id}" aria-pressed="${dashboardView.status === id}">${label}<span>${total}</span></button>`).join('')}</div></div>
        ${table(['Pedido / horário', 'Cliente', 'Itens selecionados', 'Valor total', 'Pagamento', 'Estado operacional'], visible.map(s => `<tr><td><a class="order-number mono" href="#sales/${esc(s.id)}">#${esc(s.id.slice(0, 6).toUpperCase())}</a><small class="mono">${esc(new Date(s.created_at).toLocaleTimeString('pt-BR', {
        timeZone: 'America/Sao_Paulo',
        hour: '2-digit',
        minute: '2-digit'
    }))}</small></td><td><b>${esc(s.customer_name)}</b>${s.customer_document ? `<small>${esc(s.customer_document)}</small>` : ''}</td><td><div class="order-items"><span class="item-count mono">${count(s.item_quantity)} itens</span><span class="item-summary" title="${esc(s.items_summary)}">${esc(s.items_summary || 'Ver itens do pedido')}</span></div></td><td class="num">${money(s.total)}<small>Recebido: ${money(s.paid)}</small></td><td>${payment(s)}</td><td>${delivery(s)}<a class="order-detail" href="#sales/${esc(s.id)}">Ver pedido ${icon('arrow')}</a></td></tr>`), orders.length ? 'Nenhum pedido de hoje corresponde a este filtro.' : 'Nenhum pedido criado hoje. Suas novas vendas aparecerão aqui.')}
        <div class="overview-pagination"><span>${filtered.length ? `${dashboardView.page * pageSize + 1}–${Math.min((dashboardView.page + 1) * pageSize, filtered.length)} de ${count(filtered.length)} pedidos` : '0 pedidos'} · ${esc(dayLabel(today, {
        day: '2-digit',
        month: 'long'
    }))}</span><div class="actions"><button data-action="dashboard-page" data-value="-1" aria-label="Página anterior" ${dashboardView.page === 0 ? 'disabled' : ''}>←</button><span class="mono">${dashboardView.page + 1} / ${pageCount}</span><button data-action="dashboard-page" data-value="1" aria-label="Próxima página" ${dashboardView.page + 1 >= pageCount ? 'disabled' : ''}>→</button><a href="#sales">Ver todos em Vendas &amp; Entregas ${icon('arrow')}</a></div></div></section>
    </div>`;
}

async function dashboard(main, finance) {
    const d = await api('/dashboard');
    if (!finance) {
        dashboardData = d;
        renderOverview(main, d);
        return;
    }
    main.innerHTML = title('Caixa', 'Recebimentos, despesas e histórico da sua operação.', `<button data-action="new-cash" class="primary">${icon('plus')}Lançamento</button>`);
    main.innerHTML += `<section class="metrics">${metric('Caixa disponível', money(d.cash), 'Saldo de entradas e saídas registradas', 'wallet', 'tone-green')}${metric('Contas a receber', money(d.receivables), `${count(d.pending_payment_count)} pedidos com saldo pendente`, 'receipt', 'tone-amber')}${metric('Vendas entregues e quitadas · mês', money(d.month_realized_revenue), 'Faturamento realizado no mês vigente', 'truck', 'tone-blue')}${metric('Lucro bruto realizado · mês', money(d.month_realized_profit), 'Entregues e quitados: venda menos custo na entrega', 'chart', 'tone-green')}</section><div class="section-heading"><h2>Movimentações do caixa</h2><span class="muted">Histórico com responsável e referência</span></div>${toolbar([['all', 'Todo o histórico'], ['archive', 'Arquivados · mais de 2 anos']], [], 'Descrição, responsável ou pedido')}`;
    const r = await list('cash');
    records = r.items;
    main.innerHTML += `<section class="panel table-panel cash-table">${table(['Data / responsável', 'Categoria / descrição', 'Valor', 'Referência', 'Ações'], r.items.map(e => `<tr><td class="mono">${date(e.created_at)}<small>${esc(e.actor_name)}</small></td><td><div class="cash-category"><i class="category-dot ${Number(e.amount) < 0 ? 'negative' : 'positive'}"></i><b>${esc(cashLabel(e.category))}</b>${e.reversed ? badge('Estornado') : ''}</div><small class="reason-text">${esc(e.description)}</small></td><td class="num ${Number(e.amount) < 0 ? 'bad' : 'good'}">${Number(e.amount) > 0 ? '+' : ''}${money(e.amount)}</td><td>${e.sale_id ? `<a class="reference-tag" href="#sales/${e.sale_id}">#${esc(e.sale_id.slice(0, 8))}${icon('arrow')}</a>` : badge(e.damage_id ? 'Avaria' : 'Manual')}</td><td><button class="kebab" data-action="cash-menu" data-id="${e.id}" aria-label="Ver lançamento de ${esc(cashLabel(e.category))}">•••</button></td></tr>`))}${pager(r)}</section>`;
}

function cashLabel(category) {
    return ({
        OPENING: 'Saldo inicial',
        CONTRIBUTION: 'Aporte',
        WITHDRAWAL: 'Retirada',
        EXPENSE: 'Despesa',
        PURCHASE: 'Compra paga',
        RECEIPT: 'Recebimento',
        REIMBURSEMENT: 'Ressarcimento (histórico)',
        REVERSAL: 'Estorno'
    })[category] || category;
}

function movementDrawer(m) {
    const type = ({
        INBOUND: 'Entrada',
        DAMAGE: 'Avaria',
        DELIVERY: 'Venda / entrega',
        CORRECTION: 'Correção'
    })[m.type] || m.type;
    drawer('Detalhes da movimentação', `<div class="drawer-summary"><b>${esc(m.product_name)}</b>${badge(type, m.type === 'INBOUND' ? 'good' : m.type === 'DAMAGE' ? 'damage' : m.type === 'DELIVERY' ? 'sale' : 'info')}<strong class="mono">${m.delta > 0 ? '+' : ''}${count(m.delta)} produtos</strong></div><dl class="drawer-data"><div><dt>Saldo após a movimentação</dt><dd class="mono">${count(m.balance)}</dd></div><div><dt>Custo unitário aplicado</dt><dd class="mono">${money(m.unit_cost)}</dd></div>${m.cost_repair_id ? `<div><dt>Custo original preservado</dt><dd>${money(m.original_unit_cost)}</dd></div><div><dt>Correção de custo</dt><dd>${esc(m.cost_repair_reason)}<small class="mono">${esc(m.cost_repair_id)}</small></dd></div>` : ''}<div><dt>Motivo / referência</dt><dd>${esc(m.reason)}</dd></div><div><dt>Responsável</dt><dd>${esc(m.actor_name)}</dd></div><div><dt>Data</dt><dd>${date(m.created_at)}</dd></div><div><dt>Origem</dt><dd>${m.source === 'INTERNAL' ? 'Operação interna' : esc(m.source)}</dd></div></dl>${m.sale_id ? `<div class="drawer-section"><a class="compact-detail-link" href="#sales/${m.sale_id}" data-close>Ver pedido ${icon('arrow')}</a></div>` : ''}`);
}

function cashDrawer(e) {
    drawer('Detalhes do lançamento', `<div class="drawer-summary"><b>${esc(cashLabel(e.category))}</b><strong class="${Number(e.amount) < 0 ? 'bad' : 'good'} mono">${money(e.amount)}</strong>${e.reversed ? badge('Estornado') : ''}</div><dl class="drawer-data"><div><dt>Descrição</dt><dd>${esc(e.description)}</dd></div><div><dt>Responsável</dt><dd>${esc(e.actor_name)}</dd></div><div><dt>Data</dt><dd>${date(e.created_at)}</dd></div></dl><div class="drawer-section">${e.sale_id ? `<a class="compact-detail-link" href="#sales/${e.sale_id}" data-close>Ver pedido e itens ${icon('arrow')}</a>` : ''}${!e.reversed && !['REVERSAL', 'OPENING'].includes(e.category) ? `<button class="drawer-action danger-text" data-action="reverse" data-id="${e.id}">Estornar lançamento</button>` : ''}</div>`);
}

function saleDrawer(s) {
    drawer(`Gerenciar pedido #${esc(s.id.slice(0, 8))}`, `<div class="drawer-summary"><b>${esc(s.customer_name)}</b><strong class="mono">${money(s.total)}</strong>${payment(s)}${delivery(s)}</div><dl class="drawer-data"><div><dt>Recebido</dt><dd class="mono">${money(s.paid)}</dd></div><div><dt>A receber</dt><dd class="mono">${money(Number(s.total) - Number(s.paid))}</dd></div><div><dt>Data do pedido</dt><dd>${date(s.created_at)}</dd></div></dl><div class="drawer-section"><a class="compact-detail-link" href="#sales/${s.id}" data-close>Ver pedido e itens ${icon('arrow')}</a>${Number(s.paid) < Number(s.total) ? `<button class="drawer-action" data-action="payment" data-id="${s.id}">Registrar recebimento</button>` : ''}${!s.delivered_at ? `<button class="drawer-action primary" data-action="deliver" data-id="${s.id}">Confirmar saída / entrega</button>` : ''}</div>`);
}

async function productList(main) {
    const [, r] = await Promise.all([lookups(), list('products')]);
    records = r.items;
    const quantityIcon = filters.sort === 'quantity' ? '↓' : filters.sort === 'quantity-asc' ? '↑' : '↕';
    const sortButton = `<button class="sort-button" type="button" data-action="sort-quantity" aria-label="Ordenar quantidade ${filters.sort === 'quantity' ? 'do maior para o menor' : 'do menor para o maior'}">Quantidade <span aria-hidden="true">${quantityIcon}</span></button>`;
    main.innerHTML = title('Produtos', 'Seu catálogo, preços e saldo físico em um só lugar.', `<button data-action="manage-categories">${icon('grid')}Categorias</button><button class="primary" data-action="new-product">${icon('plus')}Novo produto</button>`) + toolbar([['active', 'Ativos'], ['inactive', 'Ocultos']], [['', 'Nome A–Z'], ['name-desc', 'Nome Z–A'], ['purchase-price', 'Compra: menor valor'], ['sale-price', 'Venda: menor valor']], 'Nome, categoria, quantidade, compra, venda ou situação') + `<section class="panel table-panel catalog-table">${table(['Produto', 'SKU', 'Categoria', sortButton, 'Valor de compra', 'Valor de venda', 'Situação', 'Ações'], r.items.map(p => `<tr><td><div class="identity-cell"><span class="record-avatar">${icon('box')}</span><b>${esc(p.name)}</b></div></td><td><span class="code-tag">${esc(p.sku || '—')}</span></td><td>${badge(p.category, 'category')}</td><td class="num ${Number(p.quantity) === 0 ? 'bad' : ''}">${count(p.quantity)}${Number(p.minimum) > 0 ? `<small>Seguro: ${count(p.minimum)}</small>` : ''}</td><td class="num">${money(p.purchase_price)}</td><td class="num">${money(p.price)}</td><td>${!p.active ? badge('Oculto') : Number(p.quantity) === 0 ? badge('Sem saldo', 'warn') : Number(p.minimum) > 0 && Number(p.quantity) <= Number(p.minimum) ? badge('Estoque baixo', 'warn') : badge('Disponível', 'good')}</td><td><button class="kebab" aria-label="Ações de ${esc(p.name)}" title="Ações" data-action="product-menu" data-id="${p.id}">•••</button></td></tr>`))}${pager(r)}</section>`;
}

async function exportOrder(id, button) {
    button.disabled = true;
    try {
        const response = await fetch(`/api/sales/${encodeURIComponent(id)}/export.xlsx`);
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            throw new Error(error.message || 'Não foi possível exportar o pedido.');
        }
        const url = URL.createObjectURL(await response.blob());
        const link = document.createElement('a');
        link.href = url;
        link.download = `pedido-${id}.xlsx`;
        document.body.append(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    } finally {
        button.disabled = false;
    }
}

function showChartTooltip(target) {
    const tooltip = target?.closest('.interactive-chart')?.querySelector('.chart-tooltip');
    if (!tooltip) return;
    tooltip.textContent = target.dataset.chartTip;
    tooltip.hidden = false;
}

function hideChartTooltip(target, related) {
    if (!target || related instanceof Node && target.contains(related)) return;
    const tooltip = target.closest('.interactive-chart')?.querySelector('.chart-tooltip');
    if (tooltip) tooltip.hidden = true;
}

function productDetails(p) {
    const stockValue = Number(p.quantity) * Number(p.average_cost);
    const saleValue = Number(p.quantity) * Number(p.price);
    modal(`Detalhes · ${esc(p.name)}`, `<div class="detail-grid"><div><small>Categoria</small><b>${esc(p.category)}</b></div><div><small>Situação</small><b>${p.active ? 'Ativo' : 'Oculto'}</b></div><div><small>Estoque</small><b>${esc(p.quantity)}</b></div><div><small>Margem de estoque seguro</small><b>${Number(p.minimum) > 0 ? esc(p.minimum) : 'Não definido'}</b></div><div><small>Valor de compra unitário</small><b>${money(p.purchase_price)}</b></div><div><small>Custo médio ponderado</small><b>${money(p.average_cost)}</b></div><div><small>Valor de venda unitário</small><b>${money(p.price)}</b></div><div><small>Valor agregado em estoque · custo</small><b>${money(stockValue)}</b></div><div><small>Total pelo preço atual</small><b>${money(saleValue)}</b></div><div class="detail-actions"><button type="button" data-close>Fechar</button></div></div>`);
}

function productDrawer(p) {
    drawer(`Gerenciar produto ${esc(p.name)}`, `<div class="drawer-summary"><b>${esc(p.name)}</b><span>${esc(p.category)}</span><span>Estoque: ${esc(p.quantity)}${Number(p.minimum) > 0 ? ` · Seguro: ${esc(p.minimum)}` : ''}</span><strong>${money(p.price)} venda · ${money(p.purchase_price)} compra</strong>${!p.active ? badge('Oculto') : Number(p.quantity) === 0 ? badge('Sem saldo', 'warn') : badge('Ativo', 'good')}</div><div class="drawer-section"><h3>Produto</h3><button class="drawer-action" data-action="product-details" data-id="${p.id}">Ver detalhes</button><button class="drawer-action" data-action="edit-product" data-id="${p.id}">Editar produto</button><button class="drawer-action" data-action="toggle-product" data-id="${p.id}">${p.active ? 'Ocultar produto' : 'Mostrar produto'}</button></div><div class="drawer-section"><h3>Catálogo</h3><button class="drawer-action danger-text" data-action="delete-product" data-id="${p.id}">Excluir produto</button></div>`);
}

function manageCategories() {
    const rows = categories.map(c => `<div class="category-row"><b>${esc(c.name)}</b><button class="link" data-action="edit-category" data-id="${c.id}">Editar</button></div>`).join('');
    modal('Categorias', `<div class="category-list">${rows || '<p class="empty">Nenhuma categoria cadastrada.</p>'}</div><div class="form-actions"><button type="button" data-action="new-category">+ Nova categoria</button><button type="button" data-close>Fechar</button></div>`);
}

async function productForm(p = {}) {
    await lookups();
    const id = p.id || uuid();
    if (!categories.length) {
        notify('Crie uma categoria pelo botão “Categoria” antes de cadastrar o produto.');
        return;
    }
    const creating = !p.id;
    const unitId = p.unit_id || '00000000-0000-0000-0000-000000000001';
    const openingFields = creating ? input('Estoque *', 'initialQuantity', 0, 'number', 'required min="0" max="1000000000" step="1"') : `<label>Estoque<input value="${esc(p.quantity)}" disabled><small>Para ajustar o saldo, registre uma entrada ou avaria.</small></label>`;
    modal(p.id ? 'Editar produto' : 'Novo produto', `<form class="grid product-form">${input('Nome *', 'name', p.name, 'text', 'required maxlength="120"')}${input('SKU', 'sku', p.sku, 'text', 'maxlength="80"')}${requiredSelection('Categoria *', 'categoryId', categories.map(c => [c.id, c.name]), p.category_id)}${currencyInput('Valor de venda (unitário) *', 'price', p.price ?? '0.00', 'required min="0" step="0.01"')}${currencyInput('Valor de compra (unitário) *', 'purchasePrice', p.purchase_price ?? '0.00', 'required min="0" step="0.01"')}${openingFields}${input('Margem de estoque seguro', 'minimum', p.minimum ?? '', 'number', 'min="0" max="1000000000" step="1"')}${select('Situação', 'active', [['true', 'Ativo'], ['false', 'Oculto']], String(p.active ?? true))}${formEnd()}</form>`, v => {
        const product = {
            id,
            name: v.name,
            sku: v.sku,
            description: '',
            categoryId: v.categoryId,
            unitId,
            price: v.price,
            purchasePrice: v.purchasePrice,
            minimum: Number(v.minimum || 0),
            active: v.active === 'true',
            version: p.version || 0
        };
        return command(p.id ? `/products/${id}` : '/products', p.id ? product : {
            product,
            initialQuantity: Number(v.initialQuantity)
        }, p.id ? 'PUT' : 'POST');
    });
}

async function movements(main) {
    const [r, d] = await Promise.all([list('stock-movements'), api('/dashboard')]);
    records = r.items;
    main.innerHTML = title('Entradas & Saídas', 'Cada alteração conserva motivo, custo e responsável.', `<button class="primary" data-action="new-stock">${icon('plus')}Registrar movimento</button>`) + `<section class="metrics">${metric('Movimentações no mês', count(d.month_movement_count), 'Registros confirmados no período', 'transfer')}${metric('Entradas / reposições', `+${count(d.month_inbound)}`, 'Quantidade recebida', 'box', 'tone-green')}${metric('Saídas & vendas', count(d.month_outbound), 'Produtos entregues em vendas', 'truck', 'tone-green')}${metric('Perdas & avarias', count(d.month_damage), 'Baixas por dano, separadas das vendas', 'box', 'tone-red')}</section>${toolbar([['inbound', 'Entradas'], ['delivery', 'Vendas / entregas'], ['damage', 'Avarias']])}<section class="panel table-panel movements-table">` + table(['Data / responsável', 'Produto', 'Tipo / motivo', 'Quantidade', 'Saldo após', 'Custo unitário', 'Auditoria'], r.items.map(m => `<tr><td class="mono">${date(m.created_at)}<small>${esc(m.actor_name)}</small></td><td><b>${esc(m.product_name)}</b></td><td>${badge(({
        INBOUND: 'Entrada',
        DAMAGE: 'Avaria',
        DELIVERY: 'Venda / entrega',
        CORRECTION: 'Correção'
    })[m.type] || m.type, m.type === 'INBOUND' ? 'good' : m.type === 'DAMAGE' ? 'damage' : m.type === 'DELIVERY' ? 'sale' : 'info')}<small class="reason-text">${esc(m.reason)}</small></td><td class="num"><span class="quantity-tag ${m.type === 'INBOUND' ? 'good' : m.type === 'DAMAGE' ? 'damage' : m.type === 'DELIVERY' ? 'sale' : 'info'}">${m.delta > 0 ? '+' : ''}${count(m.delta)}</span></td><td class="num">${count(m.balance)}</td><td class="num">${money(m.unit_cost)}</td><td><button class="kebab" data-action="movement-menu" data-id="${m.id}" aria-label="Ver movimentação de ${esc(m.product_name)}">•••</button></td></tr>`)) + pager(r) + '</section>';
}

async function stockForm(productId = '') {
    products = await all('products');
    if (!products.length) {
        notify('Cadastre um produto ativo primeiro.');
        return;
    }
    modal('Registrar movimento', `<form class="grid">${requiredSelection('Produto *', 'productId', products.map(p => [p.id, `${p.name} · estoque: ${count(p.quantity)}`]), productId, 'Selecionar')}${select('Tipo *', 'type', [['INBOUND', 'Entrada / reposição'], ['DAMAGE', 'Baixa por avaria / defeito']])}${input('Quantidade *', 'quantity', 1, 'number', 'required min="1" max="1000000000" step="1"')}${currencyInput('Valor de compra unitário *', 'unitCost', '', 'required')}${input('Motivo / referência *', 'reason', '', 'text', 'required maxlength="500"')}<p class="form-hint span2">Avarias usam o custo médio atual. Vendas são baixadas na entrega do pedido.</p>${formEnd('Confirmar movimento')}</form>`, v => command('/stock-commands', {
        ...v,
        quantity: Number(v.quantity)
    }));
    const productField = $('[name=productId]', dialog);
    const typeField = $('[name=type]', dialog);
    const costField = $('[name=unitCost]', dialog);
    const updateCost = () => {
        const selected = products.find(p => p.id === productField.value);
        const damage = typeField.value === 'DAMAGE';
        costField.value = selected ? localizedMoney(moneyCents(damage ? Number(selected.average_cost).toFixed(2) : selected.purchase_price, 'Valor de compra unitário')) : '';
        costField.readOnly = damage;
        costField.dispatchEvent(new Event('input', {bubbles: true}));
    };
    productField.addEventListener('change', updateCost);
    typeField.addEventListener('change', updateCost);
    updateCost();
}

async function customerList(main) {
    const [r, d] = await Promise.all([list('customers'), api('/dashboard')]);
    records = r.items;
    main.innerHTML = title('Clientes', 'Identificação, entregas e contas a receber.', `<button class="primary" data-action="new-customer">${icon('users')}Novo cliente</button>`) + `<section class="metrics metrics-two customers-summary">${debtMetric('Saldo devedor total', d.receivables, `${count(d.pending_payment_count)} pedidos com pagamento pendente`)}${pendingMetric(d.pending_deliveries, 'Pedidos aguardando saída')}</section>${toolbar([['debt', 'Com saldo devedor'], ['pending', 'Entregas pendentes'], ['settled', 'Contas em dia']], [], 'Nome, CPF/CNPJ, telefone ou cidade')}<div class="customers-layout"><section class="panel table-panel customers-table">${table(['Cliente', 'Contato', 'Localidade', 'Saldo devedor', 'Entregas', 'Ações'], r.items.map(c => `<tr data-customer-row="${c.id}"><td><div class="identity-cell"><button class="record-avatar initials avatar-button" data-action="customer-preview" data-id="${c.id}" aria-label="Selecionar cliente ${esc(c.name)}">${esc(c.name.slice(0, 2).toUpperCase())}</button><div><button class="customer-name" data-action="customer-preview" data-id="${c.id}">${esc(c.name)}</button><small>${esc(c.document || '—')}</small></div></div></td><td>${esc(c.phone || '—')}<small>${esc(c.email || '—')}</small></td><td>${esc(cityUf(c.city, c.state))}</td><td class="num ${Number(c.debt) > 0 ? 'debt-value' : 'good'}">${money(c.debt)}</td><td>${Number(c.pending_deliveries) > 0 ? badge(`${count(c.pending_deliveries)} pendentes`, 'warn') : '—'}</td><td><button class="kebab" aria-label="Gerenciar cliente ${esc(c.name)}" title="Ações" data-action="customer-menu" data-id="${c.id}">•••</button></td></tr>`))}${pager(r)}</section><aside id="customer-preview" class="customer-preview"><p class="empty">Selecione um cliente para consultar os dados e os pedidos.</p></aside></div>`;
    const selected = r.items.find(c => c.id === selectedCustomerId) || r.items[0];
    if (selected) await showCustomerPreview(selected.id);
}

async function showCustomerPreview(id) {
    const target = $('#customer-preview'), c = records.find(c => c.id === id);
    if (!target || !c) return;
    selectedCustomerId = id;
    target.dataset.customerId = id;
    document.querySelectorAll('[data-customer-row]').forEach(row => row.classList.toggle('selected', row.dataset.customerRow === id));
    target.innerHTML = '<p class="loading" role="status">Carregando cliente…</p>';
    try {
        const [summary, orders] = await Promise.all([api(`/customers/${id}/summary`), api(`/sales?related=${encodeURIComponent(id)}&size=3`)]);
        if (!target.isConnected || target.dataset.customerId !== id) return;
        const address = {
            street: c.street,
            number: c.number,
            district: c.district,
            city: c.city,
            state: c.state,
            postalCode: c.postal_code,
            complement: c.complement,
            reference: c.reference
        };
        target.innerHTML = `<div class="customer-preview-head"><button class="record-avatar initials avatar-button" data-action="customer-preview" data-id="${c.id}" aria-label="Selecionar cliente ${esc(c.name)}">${esc(c.name.slice(0, 2).toUpperCase())}</button><div><h2>${esc(c.name)}</h2><p>${esc(cityUf(c.city, c.state))}</p></div>${Number(summary.debt) > 0 ? badge('Em débito', 'warn') : badge('Em dia', 'good')}</div><dl class="customer-preview-data"><div><dt>CPF / CNPJ</dt><dd>${esc(c.document || '—')}</dd></div><div><dt>Telefone</dt><dd>${esc(c.phone || '—')}</dd></div><div class="span2"><dt>Email</dt><dd>${esc(c.email || '—')}</dd></div><div><dt>Total em pedidos</dt><dd class="mono">${money(summary.purchased_total)}</dd></div><div><dt>Saldo devedor</dt><dd class="mono ${Number(summary.debt) > 0 ? 'debt-value' : 'good'}">${money(summary.debt)}</dd></div></dl><div class="preview-section"><h3>Endereço de entrega</h3><p class="address">${addressText(address)}</p></div><div class="preview-section"><h3>Pedidos recentes</h3>${orders.items.length ? orders.items.map(s => `<a class="recent-order" href="#sales/${s.id}"><span><b>#${esc(s.id.slice(0, 8))}</b><small>${date(s.created_at)}</small></span><span class="mono">${money(s.total)}</span></a>`).join('') : '<p class="muted">Nenhum pedido registrado.</p>'}</div><div class="preview-actions"><button data-action="edit-customer" data-id="${id}" aria-label="Editar ${esc(c.name)}">Editar</button><a href="#customers/${id}">Ver cliente ${icon('arrow')}</a></div>`;
    } catch (error) {
        if (target.isConnected && target.dataset.customerId === id) target.innerHTML = `<div class="preview-section"><p class="error">${esc(error.message)}</p><button data-action="customer-preview" data-id="${id}">Tentar novamente</button></div>`;
    }
}

function customerDrawer(c) {
    drawer(`Gerenciar cliente ${esc(c.name)}`, `<div class="drawer-summary"><b>${esc(c.name)}</b><span>${esc(c.phone || 'Telefone não informado')}</span><span>${esc(c.email || 'Email não informado')}</span><strong>Em débito: ${money(c.debt)}</strong><span>${esc(c.pending_deliveries)} pedidos aguardando entrega</span></div><div class="drawer-section"><h3>Cliente</h3><button class="drawer-action" data-action="view-customer" data-id="${c.id}">Ver cliente</button><button class="drawer-action" data-action="edit-customer" data-id="${c.id}">Editar cadastro</button><button class="drawer-action primary" data-action="customer-sale" data-id="${c.id}">Nova venda</button></div><div class="drawer-section"><h3>Endereço</h3><p class="address">${addressText({
        street: c.street,
        number: c.number,
        district: c.district,
        city: c.city,
        state: c.state,
        postalCode: c.postal_code,
        complement: c.complement,
        reference: c.reference
    })}</p></div>`);
}

async function customerForm(c = {}) {
    const a = c.address || {};
    const id = c.id || uuid();
    const oldStreet = [a.street, a.number].filter(Boolean).join(', ');
    const oldCity = a.state && !String(a.city || '').toUpperCase().endsWith(`-${a.state}`) ? `${a.city}-${String(a.state).trim()}` : a.city;
    modal(c.id ? 'Editar cliente' : 'Novo cliente', `<form class="grid customer-form">${input('Nome *', 'name', c.name, 'text', 'required maxlength="150"')}${input('CPF/CNPJ', 'document', c.document, 'text', 'maxlength="18"')}${input('Telefone', 'phone', c.phone, 'tel', 'maxlength="30"')}${input('Email', 'email', c.email, 'email', 'maxlength="200"')}${input('Rua *', 'street', oldStreet, 'text', 'required maxlength="200" placeholder="Rua Liberdade - 890"')}${input('Bairro *', 'district', a.district, 'text', 'required maxlength="100"')}${input('Cidade-UF *', 'city', oldCity, 'text', 'required maxlength="100" placeholder="Aracaju-SE"')}${input('CEP', 'postalCode', a.postalCode, 'text', 'pattern="[0-9]{5}-?[0-9]{3}" maxlength="9"')}${input('Complemento', 'complement', a.complement, 'text', 'maxlength="200"')}${input('Referência', 'reference', a.reference, 'text', 'maxlength="200"')}<p class="note span2">Pedidos confirmados preservam o endereço usado na venda, mesmo após editar este cadastro.</p>${formEnd()}</form>`, v => command(c.id ? `/customers/${id}` : '/customers', {
        id,
        name: v.name,
        document: v.document,
        phone: v.phone,
        email: v.email,
        version: c.version || 0,
        address: {
            street: v.street,
            number: '',
            district: v.district,
            city: v.city,
            state: '',
            postalCode: v.postalCode,
            complement: v.complement,
            reference: v.reference
        }
    }, c.id ? 'PUT' : 'POST'));
}

function addressText(a) {
    const street = [a.street, a.number].filter(Boolean).join(', ');
    const city = cityUf(a.city, a.state);
    const cep = a.postalCode ? ` · CEP ${esc(a.postalCode)}` : '';
    return `${esc(street)}${a.complement ? ' · ' + esc(a.complement) : ''}<br>${esc(a.district)} · ${esc(city)}${cep}${a.reference ? '<br>Referência: ' + esc(a.reference) : ''}`;
}

function cityUf(city, state) {
    const locality = String(city || '').trim();
    const uf = String(state || '').trim();
    return uf && !locality.toUpperCase().endsWith(`-${uf.toUpperCase()}`) ? `${locality}-${uf}` : locality;
}

async function customerDetail(main, id) {
    const c = await api(`/customers/${id}`);
    const r = await list('sales', id);
    const stats = await api(`/customers/${id}/summary`);
    records = r.items;
    main.innerHTML = title(esc(c.name), 'Dados para entrega e pedidos.', `<button data-action="edit-customer" data-id="${id}">Editar cadastro</button><button class="primary" data-action="customer-sale" data-id="${id}">+ Nova venda</button>`) + `<div class="panel grid"><div><h2>Endereço de entrega</h2><div class="address">${addressText(c.address)}</div></div><div><h2>Contato</h2><p>${esc(c.phone || 'Telefone não informado')}</p><p>${esc(c.email || 'Email não informado')}</p><p>${esc(c.document || 'CPF/CNPJ não informado')}</p></div></div><section class="metrics">${debtMetric('Saldo devedor', stats?.debt, 'Valores ainda não recebidos')}${pendingMetric(stats?.pending_deliveries, 'Inclui pedidos pagos aguardando envio')}</section><h2>Pedidos</h2>` + salesTable(r.items, true) + pager(r);
}

function salesTable(items, customerDetail = false) {
    return table([customerDetail ? 'Pedido' : 'Pedido / cliente', 'Total', 'Recebido', 'A receber', 'Pagamento', 'Entrega', 'Ações'], items.map(s => `<tr><td><div class="sale-identity"><span class="order-id-tag ${Number(s.paid) < Number(s.total) ? 'open' : ''}">#${esc(s.id.slice(0, 6))}</span><span><a href="#sales/${s.id}"><b>${customerDetail ? 'Pedido' : esc(s.customer_name)}</b></a><small>${date(s.created_at)}</small></span></div></td><td class="num">${money(s.total)}</td><td class="num good">${money(s.paid)}</td><td class="num ${Number(s.total) > Number(s.paid) ? 'warn' : ''}">${money(Number(s.total) - Number(s.paid))}</td><td>${payment(s)}</td><td>${delivery(s)}</td><td><button class="kebab" data-action="sale-menu" data-id="${s.id}" aria-label="Gerenciar pedido ${esc(s.id.slice(0, 8))}">•••</button></td></tr>`));
}

async function saleList(main) {
    const [r, d] = await Promise.all([list('sales'), api('/dashboard')]);
    records = r.items;
    main.innerHTML = title('Vendas & Entregas', 'Monte o pedido, registre recebimentos e confirme a saída física.', `<button class="primary" data-action="new-sale">${icon('plus')}Nova venda</button>`) + `<section class="metrics">${metric('Total de vendas no mês', money(d.month_sales_total), `${count(d.month_sales_count)} pedidos criados neste mês`, 'wallet', 'tone-green')}${metric('Aguardando entrega', `${count(d.pending_deliveries)} pedidos`, 'Pedidos confirmados sem baixa', 'truck', Number(d.pending_deliveries) > 0 ? 'tone-yellow' : 'tone-green')}${metric('Pagamento pendente / fiado', money(d.receivables), `${count(d.pending_payment_count)} pedidos em aberto`, 'receipt', Number(d.receivables) > 0 ? 'tone-yellow' : 'tone-green')}${metric('Entregues e quitadas no mês', `${count(d.month_fulfilled_sales)} pedidos`, 'Pedidos concluídos neste mês', 'box', 'tone-blue')}</section>${toolbar([['pending', 'Aguardando entrega'], ['delivered', 'Entregues'], ['debt', 'Pagamento pendente'], ['partial', 'Pagamento parcial'], ['paid', 'Pago']], [], 'Pedido, cliente ou produto')}<section class="panel table-panel sales-table">${salesTable(r.items)}${pager(r)}</section>`;
}

async function saleForm(customerId = '') {
    [products, customers] = await Promise.all([all('products'), all('customers')]);
    if (!products.length || !customers.length) {
        notify('Cadastre pelo menos um produto ativo e um cliente.');
        return;
    }
    const cartLine = (selected = products[0]) => `<div class="cart-row">${select('Produto *', 'product', products.map(p => [p.id, `${p.name} · estoque: ${count(p.quantity)}`]), selected.id, 'required')}${input('Quantidade *', 'quantity', 1, 'number', 'required min="1" max="1000000000" step="1"')}${currencyInput('Valor unitário *', 'price', selected.price, 'required')}<button type="button" data-remove aria-label="Remover item">✕</button></div>`;
    modal('Montar compra do cliente', `<form class="purchase-form">${select('Cliente *', 'customerId', customers.map(c => [c.id, c.name]), customerId, 'required')}<div class="cart-heading"><h3>Produtos do pedido</h3><span>Quantidade e valor por unidade</span></div><div id="cart">${cartLine()}</div><button type="button" id="add-item" class="add-cart-item">${icon('plus')}Adicionar produto</button><div class="purchase-summary"><span>Total do pedido</span><strong id="cart-total" class="mono"></strong></div>${currencyInput('Recebido agora *', 'initialPayment', '0.00', 'required placeholder="2.150,99"')}<div class="payment-preview"><span id="cart-payment-status">Pagamento pendente</span><span>A receber: <b id="cart-due" class="mono"></b></span></div><p class="form-hint">Use zero para fiado. A baixa do estoque acontece na confirmação da entrega.</p>${formEnd('Confirmar pedido')}</form>`, async (v, form) => {
        const items = [...form.querySelectorAll('.cart-row')].map(row => ({
            productId: $('[name=product]', row).value,
            quantity: Number($('[name=quantity]', row).value),
            price: decimalMoney(moneyCents($('[name=price]', row).value, 'Valor unitário'))
        }));
        if (new Set(items.map(item => item.productId)).size !== items.length) throw new Error('O produto está repetido. Ajuste a quantidade na mesma linha.');
        const total = items.reduce((amount, item) => amount + moneyCents(item.price) * BigInt(item.quantity), 0n);
        if (total === 0n) throw new Error('O total do pedido deve ser maior que zero.');
        if (total > 999999999999999999n) throw new Error('O total do pedido excede o limite permitido.');
        if (moneyCents(v.initialPayment) > total) throw new Error('O valor recebido não pode exceder o total do pedido.');
        const r = await command('/sales', {customerId: v.customerId, items, initialPayment: v.initialPayment});
        return {route: `sales/${r.id}`};
    });
    const cart = $('#cart');
    const selectedProducts = () => new Set([...cart.querySelectorAll('[name=product]')].map(field => field.value));
    const syncProducts = () => {
        const selected = selectedProducts();
        for (const field of cart.querySelectorAll('[name=product]')) {
            for (const option of field.options) option.disabled = option.value !== field.value && selected.has(option.value);
        }
        const full = selected.size >= products.length;
        $('#add-item').disabled = full || cart.children.length >= 100;
        $('#add-item').title = full ? 'Todos os produtos já estão no pedido.' : '';
    };
    const sum = () => {
        try {
            const total = [...cart.children].reduce((amount, row) => {
                const quantity = $('[name=quantity]', row).value;
                if (!/^\d+$/.test(quantity)) throw new Error('Quantidade inválida.');
                return amount + BigInt(quantity) * moneyCents($('[name=price]', row).value);
            }, 0n);
            const paid = moneyCents($('[name=initialPayment]', dialog).value || '0');
            $('#cart-total').textContent = `R$ ${localizedMoney(total)}`;
            $('#cart-due').textContent = `R$ ${localizedMoney(total > paid ? total - paid : 0n)}`;
            $('#cart-payment-status').textContent = paid > total ? 'Recebimento acima do total' : paid === 0n ? 'Pagamento pendente' : paid < total ? 'Pagamento parcial' : 'Pagamento integral';
        } catch {
            $('#cart-total').textContent = '—';
            $('#cart-due').textContent = '—';
            $('#cart-payment-status').textContent = 'Confira os valores informados';
        }
    };
    $('#add-item').onclick = () => {
        if (cart.children.length >= 100) return;
        const selected = selectedProducts();
        const next = products.find(product => !selected.has(product.id));
        if (!next) return;
        cart.insertAdjacentHTML('beforeend', cartLine(next));
        syncProducts();
        sum();
    };
    cart.onclick = e => {
        if (e.target.closest('[data-remove]') && cart.children.length > 1) {
            e.target.closest('.cart-row').remove();
            syncProducts();
            sum();
        }
    };
    cart.onchange = e => {
        if (e.target.name === 'product') {
            $('[name=price]', e.target.closest('.cart-row')).value = localizedMoney(moneyCents(products.find(p => p.id === e.target.value).price));
            syncProducts();
        }
        sum();
    };
    cart.oninput = sum;
    $('[name=initialPayment]', dialog).oninput = sum;
    syncProducts();
    sum();
}

async function saleDetail(main, id) {
    const s = await api(`/sales/${id}`);
    records = [s];
    main.innerHTML = title(`Pedido ${esc(id.slice(0, 8))}`, esc(s.customer_name), `<button data-action="export-order" data-id="${id}">${icon('download')}Exportar Excel</button>${Number(s.paid) < Number(s.total) ? `<button data-action="payment" data-id="${id}">Registrar recebimento</button>` : ''}${!s.delivered_at ? `<button class="primary" data-action="deliver" data-id="${id}">Confirmar saída / entrega</button>` : ''}`) + `<section class="metrics">${metric('Total', money(s.total), '', 'receipt', 'tone-neutral')}${metric('Recebido', money(s.paid), '', 'wallet', Number(s.paid) >= Number(s.total) ? 'tone-green' : 'tone-neutral')}${metric('Saldo a receber', money(s.due), '', 'wallet', Number(s.due) === 0 ? 'tone-green' : 'tone-orange')}${metric('Situação', payment(s), s.delivered_at ? 'Entregue' : 'Aguardando entrega')}</section><div class="panel"><h2>Enviar para ${esc(s.customer_name)}</h2><div class="address">${addressText(s.address)}</div><p>${delivery(s)} ${s.delivered_at ? date(s.delivered_at) : 'A saída ainda não foi confirmada.'}</p></div>` + table(['Produto', 'Quantidade', 'Preço unitário', 'Subtotal'], s.items.map(l => `<tr><td>${esc(l.product_name)}</td><td class="num">${l.quantity}</td><td class="num">${money(l.price)}</td><td class="num">${money(Number(l.price) * Number(l.quantity))}</td></tr>`)) + `<div class="panel"><h2>Recebimentos e estornos</h2>` + table(['Data', 'Responsável', 'Tipo', 'Valor'], s.receipts.map(e => `<tr><td>${date(e.created_at)}</td><td>${esc(e.actor_name)}</td><td>${esc(cashLabel(e.category))}</td><td class="num">${money(e.amount)}</td></tr>`)) + '</div>';
}

async function cashForm() {
    modal('Lançamento no caixa', `<form class="grid">${select('Categoria *', 'category', [['EXPENSE', 'Despesa (ex.: frete)'], ['PURCHASE', 'Pagamento de compra'], ['CONTRIBUTION', 'Aporte'], ['WITHDRAWAL', 'Retirada'], ['OPENING', 'Saldo inicial (uma única vez)']])}${currencyInput('Valor *', 'amount', '', 'required placeholder="2.150,99"')}${input('Descrição / motivo *', 'description', '', 'text', 'required maxlength="500"')}<p class="form-hint span2">Para recebimentos de clientes, abra o pedido correspondente.</p>${formEnd('Registrar lançamento')}</form>`, v => command('/cash', {
        ...v,
        damageId: null
    }));
}

async function loginScreen(refreshCsrf = true) {
    if (refreshCsrf || !csrf) csrf = await api('/auth/csrf');
    user = null;
    if (dialog.open) dialog.close();
    $('#app').innerHTML = `<section class="login panel"><p class="eyebrow">Setor Headshop</p><h1>Acesse sua operação</h1><form id="login">${input('E-mail ou usuário', 'username', '', 'text', 'required autocomplete="username" maxlength="254"')}${input('Senha', 'password', '', 'password', 'required autocomplete="current-password" maxlength="72"')}<p class="error" role="alert"></p><button class="primary">Continuar</button></form></section>`;
    $('#login').onsubmit = async e => {e.preventDefault();
        const form = e.target, button = $('button', form);
        button.disabled = true;
        $('.error', form).textContent = '';
        try {
            const challenge = await api('/auth/login', {
                method: 'POST',
                body: JSON.stringify(Object.fromEntries(new FormData(form)))
            });
            form.reset();
            await authenticatorScreen(challenge.status === 'ENROLLMENT_REQUIRED');
        } catch (err) {
            $('.error', form).textContent = err.message;
        } finally {
            button.disabled = false;
        }
    };
}

async function authenticatorScreen(enrollment) {
    $('#app').innerHTML = `<section class="login panel"><p class="eyebrow">Verificação de acesso</p><h1>${enrollment ? 'Configure seu autenticador' : 'Confirme seu acesso'}</h1><div id="enrollment-content"></div><form id="confirm-auth">${input('Código de autenticação · 6 dígitos', 'otp', '', 'text', 'required inputmode="numeric" pattern="[0-9]{6}" autocomplete="one-time-code" maxlength="6"')}<p class="error" role="alert"></p><button class="primary">${enrollment ? 'Ativar autenticador e entrar' : 'Entrar com segurança'}</button></form><button class="link" id="back-login">Voltar ao login</button><p class="muted">Esta etapa expira em cinco minutos.</p></section>`;
    $('#back-login').onclick = async () => {
        await api('/auth/logout', {method: 'POST'});
        await loginScreen();
    };
    const form = $('#confirm-auth');
    if (enrollment) {
        try {
            const data = await api('/auth/enrollment');
            $('#enrollment-content').innerHTML = `<p class="subtitle">No aplicativo autenticador do seu celular, adicione uma conta escaneando este QR Code.</p><img class="enrollment-qr" alt="QR Code para configurar o autenticador" src="${esc(data.qrDataUrl)}"><details><summary>Não consigo escanear</summary><p class="muted">Insira esta chave no autenticador (TOTP, 6 dígitos, 30 segundos):</p><code class="enrollment-secret">${esc(data.secret)}</code></details><p class="subtitle">Digite abaixo o código gerado para concluir a configuração.</p>`;
        } catch (error) {
            $('.error', form).textContent = error.message;
            $('button', form).disabled = true;
            return;
        }
    } else $('#enrollment-content').innerHTML = '<p class="subtitle">Informe o código atual do aplicativo autenticador.</p>';
    form.onsubmit = async event => {
        event.preventDefault();
        const button = $('button', form);
        button.disabled = true;
        $('.error', form).textContent = '';
        try {
            user = await api('/auth/confirm', {method: 'POST', body: JSON.stringify({otp: form.elements.otp.value})});
            form.reset();
            csrf = await api('/auth/csrf');
            shell();
            await render();
        } catch (error) {
            $('.error', form).textContent = error.message;
        } finally {
            button.disabled = false;
        }
    };
}

document.addEventListener('click', async event => {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const action = button.dataset.action, id = button.dataset.id;
    try {
        if (action === 'theme') {
            const theme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
            document.documentElement.dataset.theme = theme;
            localStorage.setItem('theme', theme);
        } else if (action === 'menu') $('.sidebar').classList.toggle('open');
        else if (action === 'logout') {
            await api('/auth/logout', {method: 'POST'});
            sessionStorage.clear();
            await loginScreen();
        } else if (action === 'retry') await render();
        else if (action === 'export-order') await exportOrder(id, button);
        else if (action === 'clear-filters') {
            filters = {search: '', filter: '', sort: ''};
            page = 0;
            await render();
        } else if (action === 'customer-preview') await showCustomerPreview(id);
        else if (action === 'movement-menu') movementDrawer(records.find(m => m.id === id));
        else if (action === 'cash-menu') cashDrawer(records.find(e => e.id === id));
        else if (action === 'sale-menu') saleDrawer(records.find(s => s.id === id));
        else if (action.startsWith('dashboard-') && dashboardData && route === 'dashboard') {
            const value = button.dataset.value;
            if (action === 'dashboard-period' && ['today', 'week', 'month'].includes(value)) dashboardView.period = value;
            if (action === 'dashboard-status' && ['all', 'paid', 'delivery', 'debt'].includes(value)) {
                dashboardView.status = value;
                dashboardView.page = 0;
            }
            if (action === 'dashboard-page') dashboardView.page = Math.max(0, dashboardView.page + Number(value));
            renderOverview($('#main'), dashboardData);
            document.querySelector(`[data-action="${action}"][data-value="${value}"]`)?.focus({preventScroll: true});
        } else if (action === 'prev' || action === 'next') {
            page += action === 'prev' ? -1 : 1;
            await render();
        } else if (action === 'new-product') await productForm();
        else if (action === 'product-menu') productDrawer(records.find(p => p.id === id));
        else if (action === 'edit-product') {
            dialog.close();
            await productForm(records.find(p => p.id === id));
        } else if (action === 'product-details') {
            dialog.close();
            productDetails(records.find(p => p.id === id));
        } else if (action === 'customer-menu') customerDrawer(records.find(c => c.id === id));
        else if (action === 'view-customer') {
            dialog.close();
            location.hash = `customers/${id}`;
        } else if (action === 'sort-quantity') {
            filters.sort = filters.sort === 'quantity' ? 'quantity-asc' : 'quantity';
            page = 0;
            await render();
        } else if (action === 'toggle-product') {
            dialog.close();
            const p = records.find(p => p.id === id);
            const product = {
                id: p.id,
                name: p.name,
                sku: p.sku,
                description: '',
                categoryId: p.category_id,
                unitId: p.unit_id,
                price: p.price,
                purchasePrice: p.purchase_price,
                minimum: p.minimum || 0,
                active: !p.active,
                version: p.version
            };
            await command(`/products/${id}`, product, 'PUT');
            notify(p.active ? 'Produto ocultado.' : 'Produto exibido.');
            await render();
        } else if (action === 'delete-product') {
            const p = records.find(p => p.id === id);
            dialog.close();
            modal(`Excluir · ${esc(p.name)}`, `<form class="grid"><p class="span2">Para confirmar, digite o nome do produto exatamente como aparece abaixo.</p><p class="note span2">O produto sai do catálogo; saldo, pedidos e movimentações existentes continuam preservados no histórico.</p>${input(`Digite: ${esc(p.name)}`, 'confirmationName', '', 'text', 'required autocomplete="off"')}${formEnd('Excluir produto')}</form>`, async v => {
                if (v.confirmationName !== p.name) throw new Error('O nome digitado não corresponde ao produto.');
                await command(`/products/${id}`, {}, 'DELETE');
            });
        } else if (action === 'manage-categories') manageCategories();
        else if (action === 'edit-category') {
            const c = categories.find(c => c.id === id);
            modal('Editar categoria', `<form>${input('Nome da categoria *', 'name', c?.name || '', 'text', 'required maxlength="100"')}${formEnd()}</form>`, v => command(`/categories/${id}`, v, 'PUT'));
        } else if (action === 'new-stock' || action === 'product-stock') await stockForm(id);
        else if (action === 'new-customer') await customerForm();
        else if (action === 'edit-customer') {
            dialog.close();
            await customerForm(await api(`/customers/${id}`));
        } else if (action === 'new-sale' || action === 'customer-sale') await saleForm(id);
        else if (action === 'new-cash') await cashForm();
        else if (action === 'new-category') {
            modal('Nova categoria', `<form>${input('Nome da categoria *', 'name', '', 'text', 'required maxlength="100"')}${formEnd()}</form>`, v => command('/categories', v));
        } else if (action === 'payment') {
            const s = records.find(r => r.id === id);
            modal('Registrar recebimento', `<form><div class="purchase-summary"><span>Saldo a receber</span><strong>${money(Number(s.total) - Number(s.paid))}</strong></div>${currencyInput('Valor recebido *', 'amount', '', 'required placeholder="2.150,99"')}${formEnd('Confirmar recebimento')}</form>`, v => command(`/sales/${id}/payments`, v));
        } else if (action === 'deliver') {
            modal('Confirmar saída do almoxarifado', `<form><p>Confirme somente quando todos os produtos deste pedido saírem fisicamente do estoque.</p><p class="note">A operação baixa todos os itens. Se algum produto estiver sem saldo suficiente, nada será baixado.</p>${formEnd('Confirmar entrega integral')}</form>`, () => command(`/sales/${id}/delivery`, {}));
        } else if (action === 'reverse') {
            modal('Estornar lançamento', `<form>${input('Motivo do estorno *', 'reason', '', 'text', 'required maxlength="500"')}<p class="note">O histórico será preservado. Estornar um recebimento reabre o saldo devedor do pedido.</p>${formEnd('Confirmar estorno')}</form>`, v => command(`/cash/${id}/reverse`, v));
        }
    } catch (e) {
        notify(e.message);
    }
});
document.addEventListener('focusout', event => {
    const field = event.target;
    if (!field.matches?.('input[data-money]') || !field.value.trim()) return;
    try {
        field.value = localizedMoney(moneyCents(field.value, field.dataset.moneyLabel));
        field.removeAttribute('aria-invalid');
        field.dispatchEvent(new Event('input', {bubbles: true}));
    } catch {
        field.setAttribute('aria-invalid', 'true');
    }
});
document.addEventListener('pointerover', event => showChartTooltip(event.target.closest?.('[data-chart-tip]')));
document.addEventListener('pointerdown', event => showChartTooltip(event.target.closest?.('[data-chart-tip]')));
document.addEventListener('focusin', event => showChartTooltip(event.target.closest?.('[data-chart-tip]')));
document.addEventListener('pointerout', event => hideChartTooltip(event.target.closest?.('[data-chart-tip]'), event.relatedTarget));
document.addEventListener('focusout', event => hideChartTooltip(event.target.closest?.('[data-chart-tip]'), event.relatedTarget));
document.addEventListener('keydown', event => {
    if (event.key === 'Escape') document.querySelectorAll('.chart-tooltip').forEach(tooltip => tooltip.hidden = true);
});
window.addEventListener('hashchange', () => {
    if (user) render();
});
try {
    const session = await api('/auth/session');
    csrf = session.csrf;
    user = session.user;
    if (user) {
        shell();
        await render();
    } else await loginScreen(false);
} catch {
    await loginScreen();
}
