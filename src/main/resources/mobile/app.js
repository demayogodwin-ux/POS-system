// My Store phone app: browse prices and shelf locations, order for delivery or in store,
// track the order on Google Maps and find other branches. Talks to the POS at /api/*.
'use strict';

const POLL_MS = 5000;
const RIDER_SEND_MS = 5000;
const SIMULATED_TRIP_MIN = 15;
const RIDER_SPEED_KMH = 20;

const state = {
    config: { storeName: 'My Store', mapsKey: '', currency: 'PHP', taxRate: '0', pricesIncludeTax: 'false' },
    products: [],
    stores: [],
    cart: load('cart', {}),
    token: load('token', null),
    customer: load('customer', null),
    checkout: load('checkout', { type: 'DELIVERY', storeId: null, payment: 'CASH', spot: '', note: '', address: null, lat: null, lng: null }),
    category: 'All',
    query: '',
    position: null,
    cleanups: []
};

// ------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------

function load(key, fallback) {
    try {
        const raw = localStorage.getItem('ms.' + key);
        return raw == null ? fallback : JSON.parse(raw);
    } catch (e) {
        return fallback;
    }
}

function save(key, value) {
    try {
        if (value == null) localStorage.removeItem('ms.' + key);
        else localStorage.setItem('ms.' + key, JSON.stringify(value));
    } catch (e) {
        // Private mode or blocked storage: the app still works for this visit.
    }
}

function esc(value) {
    return String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

let moneyFormat;
function money(value) {
    if (!moneyFormat) {
        try {
            moneyFormat = new Intl.NumberFormat(undefined, { style: 'currency', currency: state.config.currency });
        } catch (e) {
            moneyFormat = new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        }
    }
    return moneyFormat.format(Number(value));
}

function when(text) {
    if (!text) return '';
    const date = new Date(text);
    return date.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
}

function km(a, b) {
    const rad = d => d * Math.PI / 180;
    const dLat = rad(b.lat - a.lat);
    const dLng = rad(b.lng - a.lng);
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
    return 6371 * 2 * Math.asin(Math.sqrt(h));
}

function point(lat, lng) {
    return lat == null || lng == null ? null : { lat: Number(lat), lng: Number(lng) };
}

let toastTimer;
function toast(message) {
    const el = document.getElementById('toast');
    el.textContent = message;
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.hidden = true; }, 3200);
}

class ApiError extends Error {
    constructor(message, status, field) {
        super(message);
        this.status = status;
        this.field = field;
    }
}

async function api(method, path, body) {
    const headers = { 'Accept': 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (state.token) headers['Authorization'] = 'Bearer ' + state.token;
    let response;
    try {
        response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    } catch (e) {
        throw new ApiError('Cannot reach the store. Check that you are on the store Wi-Fi.', 0);
    }
    let data = null;
    try {
        data = await response.json();
    } catch (e) {
        // Empty or non-JSON body; handled below.
    }
    if (!response.ok) {
        if (response.status === 401 && state.token) signOutLocally();
        throw new ApiError((data && data.error) || 'Request failed (' + response.status + ')', response.status, data && data.field);
    }
    return data;
}

function signIn(session) {
    state.token = session.token;
    state.customer = session.customer;
    save('token', state.token);
    save('customer', state.customer);
}

function signOutLocally() {
    state.token = null;
    state.customer = null;
    save('token', null);
    save('customer', null);
}

function onCleanup(fn) {
    state.cleanups.push(fn);
}

function every(ms, fn) {
    const id = setInterval(fn, ms);
    onCleanup(() => clearInterval(id));
}

function view() {
    return document.getElementById('view');
}

function showFormError(form, error) {
    form.querySelectorAll('.invalid').forEach(el => el.classList.remove('invalid'));
    const box = form.querySelector('.form-error');
    box.textContent = error.message;
    box.hidden = false;
    if (error.field) {
        const input = form.querySelector('[name="' + error.field + '"]');
        if (input) {
            input.classList.add('invalid');
            input.focus();
        }
    }
}

// ------------------------------------------------------------------
// Location
// ------------------------------------------------------------------

function locate() {
    return new Promise((resolve, reject) => {
        if (!window.isSecureContext) {
            reject(new Error('Your browser only shares GPS location on a secure (https) connection. Tap the map to drop a pin instead.'));
        } else if (!navigator.geolocation) {
            reject(new Error('This browser cannot share your location.'));
        } else {
            navigator.geolocation.getCurrentPosition(
                pos => {
                    state.position = { lat: pos.coords.latitude, lng: pos.coords.longitude };
                    resolve(state.position);
                },
                err => reject(new Error(err.code === 1 ? 'Location permission was denied.' : 'Your location could not be found.')),
                { enableHighAccuracy: true, timeout: 15000, maximumAge: 30000 }
            );
        }
    });
}

// ------------------------------------------------------------------
// Google Maps (live map with a key, simple embed without one)
// ------------------------------------------------------------------

let mapsPromise;
function loadMaps() {
    if (!state.config.mapsKey || state.mapsFailed) return Promise.resolve(false);
    if (window.google && window.google.maps) return Promise.resolve(true);
    if (!mapsPromise) {
        mapsPromise = new Promise(resolve => {
            window.__msMapsReady = () => resolve(true);
            const script = document.createElement('script');
            script.src = 'https://maps.googleapis.com/maps/api/js?key=' + encodeURIComponent(state.config.mapsKey) + '&callback=__msMapsReady';
            script.async = true;
            script.onerror = () => resolve(false);
            document.head.appendChild(script);
        });
    }
    return mapsPromise;
}

// Google calls this when the key is wrong or the Maps JavaScript API is not enabled.
window.gm_authFailure = () => {
    state.mapsFailed = true;
    toast('The Google Maps key was rejected. Ask the store to check it in POS Settings.');
};

const MARKER_COLORS = { store: '#F5A524', home: '#2563EB', rider: '#0F9F6E', me: '#7C3AED' };

/**
 * Draws points on a map inside el. Returns { set(key, point, label), fit(), onClick(fn) }.
 * points: { key: { lat, lng, label, kind } }
 */
async function drawMap(el, points, options = {}) {
    const live = await loadMaps();
    if (live && !state.mapsFailed) return liveMap(el, points, options);
    return embedMap(el, points, options);
}

function liveMap(el, points, options) {
    const g = window.google.maps;
    const map = new g.Map(el, { center: firstPoint(points) || { lat: 10.7, lng: 122.56 }, zoom: 14, disableDefaultUI: true, zoomControl: true, gestureHandling: 'greedy' });
    const markers = {};
    const info = new g.InfoWindow();

    function icon(kind) {
        return { path: g.SymbolPath.CIRCLE, scale: kind === 'rider' ? 9 : 8, fillColor: MARKER_COLORS[kind] || '#F5A524', fillOpacity: 1, strokeColor: '#FFFFFF', strokeWeight: 3 };
    }

    const handle = {
        set(key, p) {
            if (!p) {
                if (markers[key]) markers[key].setMap(null);
                delete markers[key];
                return;
            }
            if (markers[key]) {
                markers[key].setPosition(p);
            } else {
                markers[key] = new g.Marker({ map, position: p, title: p.label || '', icon: icon(p.kind) });
                markers[key].addListener('click', () => {
                    if (p.label) {
                        info.setContent(esc(p.label));
                        info.open({ map, anchor: markers[key] });
                    }
                });
            }
        },
        fit() {
            const list = Object.values(markers);
            if (list.length === 1) {
                map.setCenter(list[0].getPosition());
                map.setZoom(15);
            } else if (list.length > 1) {
                const bounds = new g.LatLngBounds();
                list.forEach(m => bounds.extend(m.getPosition()));
                map.fitBounds(bounds, 48);
            }
        },
        onClick(fn) {
            map.addListener('click', e => fn({ lat: e.latLng.lat(), lng: e.latLng.lng() }));
        },
        focusOn(p) {
            map.panTo(p);
            map.setZoom(16);
        },
        live: true
    };
    Object.entries(points).forEach(([key, p]) => handle.set(key, p));
    handle.fit();
    return handle;
}

// Without an API key: a plain Google Maps embed centred on the most important point.
function embedMap(el, points, options) {
    const current = Object.assign({}, points);
    let shown = null;
    const frame = document.createElement('iframe');
    frame.className = el.className;
    frame.loading = 'lazy';
    frame.referrerPolicy = 'no-referrer-when-downgrade';
    frame.title = 'Map';
    frame.id = el.id;
    el.replaceWith(frame);

    function refresh() {
        const focus = (options.focus || []).map(k => current[k]).find(Boolean) || firstPoint(current);
        if (!focus) return;
        // Reloading the embed flickers, so only move it when the point really moved (about 30 m).
        if (shown && km(shown, focus) < 0.03) return;
        shown = focus;
        frame.src = 'https://maps.google.com/maps?q=' + focus.lat + ',' + focus.lng + '&z=15&output=embed';
    }

    refresh();
    return {
        set(key, p) {
            if (p) current[key] = p;
            else delete current[key];
            refresh();
        },
        fit() {},
        onClick() {},
        focusOn(p) {
            current.__focus = p;
            options.focus = ['__focus'];
            refresh();
        },
        live: false
    };
}

function firstPoint(points) {
    return Object.values(points).find(Boolean) || null;
}

function directionsUrl(p) {
    return 'https://www.google.com/maps/dir/?api=1&destination=' + p.lat + ',' + p.lng;
}

// ------------------------------------------------------------------
// Data
// ------------------------------------------------------------------

async function loadConfig() {
    try {
        state.config = await api('GET', '/api/config');
        document.getElementById('storeName').textContent = state.config.storeName;
        document.title = state.config.storeName;
    } catch (e) {
        toast(e.message);
    }
}

async function loadProducts() {
    state.products = await api('GET', '/api/products');
    // Drop cart lines for products that are no longer sold.
    const ids = new Set(state.products.map(p => String(p.id)));
    Object.keys(state.cart).forEach(id => { if (!ids.has(id)) delete state.cart[id]; });
    saveCart();
}

async function loadStores() {
    if (!state.stores.length) state.stores = await api('GET', '/api/stores');
    return state.stores;
}

function product(id) {
    return state.products.find(p => p.id === Number(id));
}

// ------------------------------------------------------------------
// Cart
// ------------------------------------------------------------------

function saveCart() {
    save('cart', state.cart);
    const count = Object.values(state.cart).reduce((a, b) => a + b, 0);
    const badge = document.getElementById('cartBadge');
    badge.textContent = count;
    badge.hidden = count === 0;
}

function setQty(id, qty) {
    const p = product(id);
    const max = p ? Math.min(p.stock, 99) : 99;
    if (qty > max) {
        toast(p && p.stock === 0 ? 'Out of stock' : 'Only ' + max + ' available');
        qty = max;
    }
    if (qty <= 0) delete state.cart[id];
    else state.cart[id] = qty;
    saveCart();
}

function cartLines() {
    return Object.entries(state.cart)
        .map(([id, quantity]) => ({ product: product(id), quantity }))
        .filter(line => line.product);
}

// Mirrors the POS tax rules; the store recalculates the real total when the order is placed.
function cartTotals() {
    const sum = cartLines().reduce((a, l) => a + Math.round(l.product.price * 100) * l.quantity, 0) / 100;
    let rate = Number(state.config.taxRate) || 0;
    if (rate > 1) rate = rate / 100;
    const round = n => Math.round(n * 100) / 100;
    if (String(state.config.pricesIncludeTax) === 'true') {
        const tax = round(sum - round(sum / (1 + rate)));
        return { subtotal: round(sum - tax), tax, total: round(sum) };
    }
    const tax = round(sum * rate);
    return { subtotal: round(sum), tax, total: round(sum + tax) };
}

function stepper(id, qty) {
    return '<div class="stepper" data-id="' + id + '">' +
        '<button type="button" data-step="-1" aria-label="Less">&minus;</button>' +
        '<span>' + qty + '</span>' +
        '<button type="button" data-step="1" aria-label="More">+</button></div>';
}

// ------------------------------------------------------------------
// Shop: prices and where each item is in the store
// ------------------------------------------------------------------

const PIN_ICON = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 21s-7-6.2-7-12a7 7 0 0 1 14 0c0 5.8-7 12-7 12z M12 11.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z"/></svg>';

async function renderShop() {
    view().innerHTML =
        '<div class="page-head"><div><h1>Shop</h1><p class="muted small" style="margin:4px 0 0">Prices, stock and where to find each item.</p></div></div>' +
        '<div class="stack">' +
        '<label class="search"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z M20 20l-3.5-3.5"/></svg>' +
        '<input id="q" type="search" placeholder="Search item, brand or barcode" autocomplete="off" value="' + esc(state.query) + '"></label>' +
        '<div class="chips" id="cats"></div>' +
        '<div class="stack" id="list"><p class="muted">Loading products...</p></div>' +
        '</div><a href="#/cart" class="cart-bar" id="cartBar" hidden></a>';

    try {
        await loadProducts();
    } catch (e) {
        document.getElementById('list').innerHTML = '<div class="empty">' + esc(e.message) + '</div>';
        return;
    }

    const categories = ['All', ...new Set(state.products.map(p => p.category || 'Other'))];
    if (!categories.includes(state.category)) state.category = 'All';
    const cats = document.getElementById('cats');
    cats.innerHTML = categories.map(c => '<button type="button" class="chip-btn" aria-pressed="' + (c === state.category) + '">' + esc(c) + '</button>').join('');
    cats.addEventListener('click', e => {
        const btn = e.target.closest('button');
        if (!btn) return;
        state.category = btn.textContent;
        cats.querySelectorAll('button').forEach(b => b.setAttribute('aria-pressed', String(b === btn)));
        drawList();
    });

    document.getElementById('q').addEventListener('input', e => {
        state.query = e.target.value;
        drawList();
    });

    document.getElementById('list').addEventListener('click', e => {
        const add = e.target.closest('[data-add]');
        const step = e.target.closest('[data-step]');
        if (add) {
            setQty(add.dataset.add, (state.cart[add.dataset.add] || 0) + 1);
        } else if (step) {
            const id = step.closest('.stepper').dataset.id;
            setQty(id, (state.cart[id] || 0) + Number(step.dataset.step));
        } else {
            return;
        }
        drawList();
    });

    drawList();
}

function drawList() {
    const q = state.query.trim().toLowerCase();
    const items = state.products.filter(p =>
        (state.category === 'All' || (p.category || 'Other') === state.category) &&
        (!q || p.name.toLowerCase().includes(q) || p.code.toLowerCase().includes(q) || (p.location || '').toLowerCase().includes(q)));

    document.getElementById('list').innerHTML = items.length ? items.map(p => {
        const qty = state.cart[p.id] || 0;
        const stock = p.stock === 0 ? '<span class="stock out">Out of stock</span>'
            : p.lowStock ? '<span class="stock low">Only ' + p.stock + ' left</span>'
            : '<span class="stock muted">In stock</span>';
        const action = p.stock === 0 ? ''
            : qty ? stepper(p.id, qty)
            : '<button type="button" class="btn btn-primary btn-sm" data-add="' + p.id + '">Add</button>';
        const photo = p.image
            ? '<img class="product-img" src="' + esc(p.image) + '" alt="" loading="lazy" decoding="async">'
            : '<div class="product-img no-photo" aria-hidden="true">' + esc(p.name.charAt(0).toUpperCase()) + '</div>';
        return '<article class="card product">' + photo +
            '<div><h3>' + esc(p.name) + '</h3><div class="muted small">' + esc(p.category || 'Other') + ' &middot; ' + esc(p.code) + '</div></div>' +
            '<div class="product-actions">' + action + '</div>' +
            '<div class="price">' + money(p.price) + '</div>' +
            '<div class="row" style="flex-wrap:wrap;gap:8px">' +
            '<span class="where">' + PIN_ICON + esc(p.location || 'Ask staff') + '</span>' + stock + '</div>' +
            '</article>';
    }).join('') : '<div class="empty">No items match your search.</div>';

    const bar = document.getElementById('cartBar');
    const lines = cartLines();
    const count = lines.reduce((a, l) => a + l.quantity, 0);
    bar.hidden = count === 0;
    view().classList.toggle('has-cart-bar', count > 0);
    if (count) bar.innerHTML = '<span>' + count + (count === 1 ? ' item' : ' items') + ' &middot; ' + money(cartTotals().total) + '</span><span>View cart &rarr;</span>';
}

// ------------------------------------------------------------------
// Cart and checkout
// ------------------------------------------------------------------

async function renderCart() {
    view().innerHTML = '<p class="muted">Loading cart...</p>';
    try {
        await Promise.all([loadProducts(), loadStores()]);
    } catch (e) {
        view().innerHTML = '<div class="empty">' + esc(e.message) + '</div>';
        return;
    }

    if (!cartLines().length) {
        view().innerHTML = '<div class="empty"><svg viewBox="0 0 24 24"><path d="M6 6h15l-1.5 9h-12z M6 6L5 3H2 M9 20a1 1 0 1 0 0-2 1 1 0 0 0 0 2z M18 20a1 1 0 1 0 0-2 1 1 0 0 0 0 2z"/></svg>' +
            '<p>Your cart is empty.</p><a class="btn btn-primary" href="#/">Browse items</a></div>';
        return;
    }

    const co = state.checkout;
    if (!state.stores.some(s => s.id === co.storeId)) co.storeId = state.stores.length ? state.stores[0].id : null;
    if (co.address == null && state.customer) co.address = state.customer.address || '';

    view().innerHTML =
        '<div class="page-head"><h1>Your cart</h1><button class="btn btn-ghost btn-sm btn-danger" id="clear">Clear</button></div>' +
        '<form id="checkout" class="stack" novalidate>' +
        '<section class="card" id="lines"></section>' +

        '<section class="card stack"><h2>How do you want it?</h2>' +
        '<div class="segmented" id="type">' +
        '<button type="button" data-type="DELIVERY"><strong>Deliver to me</strong><span>We bring it to your address</span></button>' +
        '<button type="button" data-type="IN_STORE"><strong>I\'m in the store</strong><span>Order here, we bring it to you</span></button>' +
        '</div>' +
        '<div class="field"><label for="storeId">Store</label><select id="storeId" name="storeId">' +
        state.stores.map(s => '<option value="' + s.id + '">' + esc(s.name) + '</option>').join('') + '</select>' +
        '<button type="button" class="link-btn small" id="nearest" style="justify-self:start">Pick the store nearest me</button></div>' +
        '<div id="deliveryBox" class="stack">' +
        '<div class="field"><label for="address">Delivery address</label><textarea id="address" name="address" placeholder="House no., street, barangay, city, landmark">' + esc(co.address || '') + '</textarea></div>' +
        '<div><div id="pickMap" class="map"></div><p class="map-note" id="pinNote"></p>' +
        '<button type="button" class="btn btn-sm" id="useLocation">Use my current location</button></div>' +
        '</div>' +
        '<div id="storeBox" class="field"><label for="spot">Where are you in the store?</label>' +
        '<input id="spot" name="spot" maxlength="100" placeholder="e.g. Table 4, near Aisle 2 - or leave empty to pick up at the counter" value="' + esc(co.spot) + '"></div>' +
        '</section>' +

        '<section class="card stack"><h2>Payment</h2>' +
        '<div class="segmented three" id="payment">' +
        '<button type="button" data-pay="CASH">Cash</button><button type="button" data-pay="EWALLET">E-wallet</button><button type="button" data-pay="CARD">Card</button>' +
        '</div><p class="muted small" style="margin:0">You pay when you receive your order.</p>' +
        '<div class="field"><label for="note">Note for the store (optional)</label><input id="note" name="note" maxlength="255" value="' + esc(co.note) + '" placeholder="e.g. call when outside"></div>' +
        '</section>' +

        '<section class="card totals" id="totals"></section>' +
        '<p class="form-error" hidden></p>' +
        '<button class="btn btn-primary btn-block" id="place" type="submit"></button>' +
        '</form>';

    const form = document.getElementById('checkout');
    document.getElementById('storeId').value = co.storeId;
    let pickMap = null;

    function persist() {
        co.address = form.address.value;
        co.spot = form.spot.value;
        co.note = form.note.value;
        co.storeId = Number(form.storeId.value);
        save('checkout', co);
    }

    function drawLines() {
        const lines = cartLines();
        document.getElementById('lines').innerHTML = lines.map(l =>
            '<div class="line"><div class="name"><strong>' + esc(l.product.name) + '</strong>' +
            '<div class="muted small">' + money(l.product.price) + ' each &middot; ' + esc(l.product.location || 'Ask staff') + '</div></div>' +
            stepper(l.product.id, l.quantity) + '</div>').join('');
        const t = cartTotals();
        document.getElementById('totals').innerHTML =
            '<div class="spread"><span class="muted">Subtotal</span><span class="mono">' + money(t.subtotal) + '</span></div>' +
            '<div class="spread"><span class="muted">Tax</span><span class="mono">' + money(t.tax) + '</span></div>' +
            '<div class="spread total"><span>Total</span><span class="mono">' + money(t.total) + '</span></div>';
        document.getElementById('place').textContent = state.token ? 'Place order - ' + money(t.total) : 'Sign in to place your order';
        if (!lines.length) renderCart();
    }

    function drawType() {
        form.querySelectorAll('#type button').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.type === co.type)));
        form.querySelectorAll('#payment button').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.pay === co.payment)));
        document.getElementById('deliveryBox').hidden = co.type !== 'DELIVERY';
        document.getElementById('storeBox').hidden = co.type !== 'IN_STORE';
        if (co.type === 'DELIVERY' && !pickMap) startPickMap();
    }

    function storePoint() {
        const s = state.stores.find(x => x.id === co.storeId);
        return s ? Object.assign(point(s.lat, s.lng), { label: s.name, kind: 'store' }) : null;
    }

    function pinNote() {
        document.getElementById('pinNote').textContent = co.lat != null
            ? 'Pin set. The rider will navigate to this spot.' + (pickMap && pickMap.live ? ' Tap the map to move it.' : '')
            : (pickMap && pickMap.live ? 'Tap the map to drop a pin on your exact location (optional).' : 'Optional: share your location so the rider finds you faster.');
    }

    async function startPickMap() {
        const home = co.lat != null ? Object.assign(point(co.lat, co.lng), { label: 'Deliver here', kind: 'home' }) : null;
        pickMap = await drawMap(document.getElementById('pickMap'), { store: storePoint(), home }, { focus: ['home', 'store'] });
        pickMap.onClick(p => {
            co.lat = p.lat;
            co.lng = p.lng;
            save('checkout', co);
            pickMap.set('home', Object.assign(p, { label: 'Deliver here', kind: 'home' }));
            pinNote();
        });
        pinNote();
    }

    form.addEventListener('click', async e => {
        const typeBtn = e.target.closest('[data-type]');
        const payBtn = e.target.closest('[data-pay]');
        const step = e.target.closest('[data-step]');
        if (typeBtn) {
            co.type = typeBtn.dataset.type;
            persist();
            drawType();
        } else if (payBtn) {
            co.payment = payBtn.dataset.pay;
            persist();
            drawType();
        } else if (step) {
            const id = step.closest('.stepper').dataset.id;
            setQty(id, (state.cart[id] || 0) + Number(step.dataset.step));
            drawLines();
        } else if (e.target.id === 'useLocation') {
            try {
                const me = await locate();
                co.lat = me.lat;
                co.lng = me.lng;
                save('checkout', co);
                if (pickMap) {
                    pickMap.set('home', Object.assign({}, me, { label: 'Deliver here', kind: 'home' }));
                    pickMap.fit();
                }
                pinNote();
            } catch (err) {
                toast(err.message);
            }
        } else if (e.target.id === 'nearest') {
            try {
                const me = await locate();
                const best = state.stores.slice().sort((a, b) => km(me, point(a.lat, a.lng)) - km(me, point(b.lat, b.lng)))[0];
                form.storeId.value = best.id;
                persist();
                if (pickMap) pickMap.set('store', storePoint());
                toast('Nearest: ' + best.name + ' (' + km(me, point(best.lat, best.lng)).toFixed(1) + ' km)');
            } catch (err) {
                toast(err.message);
            }
        }
    });

    form.addEventListener('input', persist);
    form.storeId.addEventListener('change', () => {
        persist();
        if (pickMap) {
            pickMap.set('store', storePoint());
            pickMap.fit();
        }
    });

    document.getElementById('clear').addEventListener('click', () => {
        state.cart = {};
        saveCart();
        renderCart();
    });

    form.addEventListener('submit', async e => {
        e.preventDefault();
        persist();
        if (!state.token) {
            location.hash = '#/account?next=cart';
            return;
        }
        const button = document.getElementById('place');
        button.disabled = true;
        button.textContent = 'Placing order...';
        try {
            const order = await api('POST', '/api/orders', {
                type: co.type,
                storeId: co.storeId,
                payment: co.payment,
                address: co.type === 'DELIVERY' ? co.address : '',
                lat: co.type === 'DELIVERY' ? co.lat : null,
                lng: co.type === 'DELIVERY' ? co.lng : null,
                spot: co.type === 'IN_STORE' ? co.spot : '',
                note: co.note,
                items: cartLines().map(l => ({ productId: l.product.id, quantity: l.quantity }))
            });
            state.cart = {};
            saveCart();
            co.note = '';
            save('checkout', co);
            location.hash = '#/orders/' + order.id;
            toast('Order ' + order.orderNo + ' sent to the store');
        } catch (err) {
            showFormError(form, err);
            button.disabled = false;
            drawLines();
        }
    });

    drawLines();
    drawType();
}

// ------------------------------------------------------------------
// Orders and live tracking
// ------------------------------------------------------------------

function needSignIn(next) {
    view().innerHTML = '<div class="empty"><p>Sign in to see your orders.</p><a class="btn btn-primary" href="#/account?next=' + next + '">Sign in or create account</a></div>';
}

async function renderOrders() {
    if (!state.token) return needSignIn('orders');
    view().innerHTML = '<div class="page-head"><h1>My orders</h1></div><div class="stack" id="orders"><p class="muted">Loading...</p></div>';

    async function refresh() {
        try {
            const orders = await api('GET', '/api/orders');
            document.getElementById('orders').innerHTML = orders.length ? orders.map(o =>
                '<a class="card spread" style="text-decoration:none" href="#/orders/' + o.id + '">' +
                '<div><h3>Order ' + esc(o.orderNo) + '</h3>' +
                '<div class="muted small">' + when(o.createdAt) + ' &middot; ' + (o.type === 'DELIVERY' ? 'Delivery' : 'In store') + ' &middot; ' + o.itemCount + ' items</div>' +
                '<div class="small">' + esc(o.storeName) + '</div>' +
                (o.unreadMessages ? '<div class="chat-unread">' + o.unreadMessages + ' new message' + (o.unreadMessages > 1 ? 's' : '') + ' from the store</div>' : '') +
                '</div>' +
                '<div style="text-align:right"><span class="status status-' + o.status + '">' + esc(o.statusLabel) + '</span>' +
                '<div class="price" style="margin-top:6px">' + money(o.total) + '</div></div></a>').join('')
                : '<div class="empty"><p>No orders yet.</p><a class="btn btn-primary" href="#/">Start shopping</a></div>';
        } catch (e) {
            if (!state.token) return needSignIn('orders');
            document.getElementById('orders').innerHTML = '<div class="empty">' + esc(e.message) + '</div>';
        }
    }

    await refresh();
    every(POLL_MS * 2, refresh);
}

const STEPS = {
    DELIVERY: [['PENDING', 'Order placed', 'Waiting for the store to accept'], ['PREPARING', 'Preparing', 'Staff are picking your items'],
        ['OUT_FOR_DELIVERY', 'On the way', 'Your rider is heading to you'], ['COMPLETED', 'Delivered', 'Enjoy!']],
    IN_STORE: [['PENDING', 'Order placed', 'Waiting for staff to accept'], ['PREPARING', 'Preparing', 'Staff are picking your items'],
        ['READY', 'Ready', 'A staff member is bringing it to you'], ['COMPLETED', 'Received', 'Thank you for shopping!']]
};

function stepsHtml(order) {
    if (order.status === 'CANCELLED') return '<div class="notice"><strong>This order was cancelled.</strong> Nothing will be charged.</div>';
    const steps = STEPS[order.type];
    const at = steps.findIndex(s => s[0] === order.status);
    return '<ol class="steps">' + steps.map((s, i) =>
        '<li class="' + (i < at || order.status === 'COMPLETED' ? 'done' : i === at ? 'current' : '') + '">' +
        esc(s[1]) + (i === at && order.status !== 'COMPLETED' ? '<div class="small muted" style="font-weight:400">' + esc(s[2]) + '</div>' : '') + '</li>').join('') + '</ol>';
}

// Where the rider is: real GPS from the rider page, or an estimate along the route when the rider is not sharing.
function riderPosition(order) {
    if (order.status !== 'OUT_FOR_DELIVERY') return null;
    if (order.rider) return Object.assign(point(order.rider.lat, order.rider.lng), { label: 'Rider', kind: 'rider', real: true });
    const from = order.store && point(order.store.lat, order.store.lng);
    const to = point(order.lat, order.lng);
    if (!from || !to || !order.dispatchedAt) return null;
    const t = Math.min(0.95, Math.max(0, (Date.now() - new Date(order.dispatchedAt).getTime()) / (SIMULATED_TRIP_MIN * 60000)));
    return { lat: from.lat + (to.lat - from.lat) * t, lng: from.lng + (to.lng - from.lng) * t, label: 'Rider (estimated)', kind: 'rider', real: false };
}

async function renderOrder(id) {
    if (!state.token) return needSignIn('orders');
    view().innerHTML = '<p class="muted">Loading order...</p>';
    let order;
    try {
        order = await api('GET', '/api/orders/' + id);
    } catch (e) {
        view().innerHTML = '<div class="empty">' + esc(e.message) + '</div>';
        return;
    }

    const store = order.store;
    view().innerHTML =
        '<div class="page-head"><div><a href="#/orders" class="muted small">&larr; My orders</a><h1>Order ' + esc(order.orderNo) + '</h1>' +
        '<div class="muted small">' + when(order.createdAt) + '</div></div><span id="chip"></span></div>' +
        '<div class="stack">' +
        '<section class="card" id="steps"></section>' +
        '<section class="card stack"><div class="spread"><h2 id="mapTitle"></h2><span class="small muted" id="eta"></span></div>' +
        '<div id="trackMap" class="map tall"></div><p class="map-note" id="trackNote" style="margin:0"></p>' +
        (store ? '<div class="row"><a class="btn btn-sm" href="tel:' + esc(store.phone) + '">Call store</a>' +
            '<a class="btn btn-sm" target="_blank" rel="noopener" href="' + directionsUrl(store) + '">Directions to store</a></div>' : '') +
        '</section>' +
        '<section class="card stack" id="chat"><div><h2>Chat with the store</h2>' +
        '<div class="muted small">A problem with your order or the delivery? Message the staff here.</div></div>' +
        '<div class="chat-log" id="chatLog"><p class="muted small">Loading messages...</p></div>' +
        '<form class="chat-form" id="chatForm" novalidate><input id="chatText" name="text" maxlength="500" autocomplete="off" placeholder="Write a message">' +
        '<button class="btn btn-primary" type="submit">Send</button></form></section>' +
        '<section class="card"><h2 style="margin-bottom:6px">' + (order.type === 'DELIVERY' ? 'Delivery to' : 'In store at ' + esc(order.storeName)) + '</h2>' +
        '<div>' + esc(order.type === 'DELIVERY' ? order.address : order.spot) + '</div>' +
        (order.note ? '<div class="muted small" style="margin-top:4px">Note: ' + esc(order.note) + '</div>' : '') + '</section>' +
        '<section class="card">' + (order.items || []).map(i =>
            '<div class="line"><span class="mono"><strong>' + i.quantity + 'x</strong></span><span class="name">' + esc(i.name) + '</span><span class="mono">' + money(i.lineTotal) + '</span></div>').join('') +
        '<div class="totals" style="margin-top:8px">' +
        '<div class="spread"><span class="muted">Subtotal</span><span class="mono">' + money(order.subtotal) + '</span></div>' +
        '<div class="spread"><span class="muted">Tax</span><span class="mono">' + money(order.tax) + '</span></div>' +
        '<div class="spread total"><span>Total</span><span class="mono">' + money(order.total) + '</span></div>' +
        '<div class="muted small">Pay by ' + ({ CASH: 'cash', CARD: 'card', EWALLET: 'e-wallet' }[order.payment]) + ' on hand-over</div></div></section>' +
        '<button class="btn btn-block btn-danger" id="cancel" hidden>Cancel order</button>' +
        '</div>';

    const points = {
        store: store ? Object.assign(point(store.lat, store.lng), { label: store.name, kind: 'store' }) : null,
        home: order.type === 'DELIVERY' && order.lat != null ? Object.assign(point(order.lat, order.lng), { label: 'You', kind: 'home' }) : null,
        rider: riderPosition(order)
    };
    const map = await drawMap(document.getElementById('trackMap'), points, { focus: ['rider', 'home', 'store'] });
    let lastStatus = null;

    function update(o) {
        order = o;
        document.getElementById('chip').innerHTML = '<span class="status status-' + o.status + '">' + esc(o.statusLabel) + '</span>';
        document.getElementById('steps').innerHTML = stepsHtml(o);
        document.getElementById('cancel').hidden = o.status !== 'PENDING';
        const rider = riderPosition(o);
        map.set('rider', rider);
        const home = points.home;
        document.getElementById('mapTitle').textContent = o.type === 'IN_STORE' ? 'Your store' : rider ? 'Track your rider' : 'Delivery route';
        let note = '';
        let eta = '';
        if (rider && home) {
            const left = km(rider, home);
            eta = left < 0.1 ? 'Arriving now' : 'About ' + Math.max(1, Math.round(left / RIDER_SPEED_KMH * 60)) + ' min away';
            note = rider.real ? 'Live rider location, updated ' + when(o.rider.updatedAt) + '.' : 'Estimated position - the rider is not sharing GPS right now.';
        } else if (o.type === 'DELIVERY' && !home) {
            note = 'No map pin was set for this order, so only the store is shown.';
        } else if (o.type === 'DELIVERY' && o.status !== 'COMPLETED' && o.status !== 'CANCELLED') {
            note = 'The rider appears here once your order is on the way.';
        }
        if (!map.live && note) note += ' (Simple map: the store has not set a Google Maps key.)';
        document.getElementById('eta').textContent = eta;
        document.getElementById('trackNote').textContent = note;
        if (lastStatus !== o.status) {
            if (lastStatus) toast('Order ' + o.orderNo + ': ' + o.statusLabel);
            if (rider) map.fit();
            lastStatus = o.status;
        }
    }

    document.getElementById('cancel').addEventListener('click', async () => {
        if (!confirm('Cancel order ' + order.orderNo + '?')) return;
        try {
            update(await api('POST', '/api/orders/' + order.id + '/cancel', {}));
        } catch (e) {
            toast(e.message);
        }
    });

    let lastMessageId = null;
    async function loadChat() {
        let messages;
        try {
            messages = await api('GET', '/api/orders/' + id + '/messages');
        } catch (e) {
            messages = null;
        }
        // The customer may have left this page while the request was on its way.
        const log = document.getElementById('chatLog');
        if (!log) return;
        if (!messages) {
            if (lastMessageId == null) log.innerHTML = '<p class="muted small">Messages could not be loaded.</p>';
            return;
        }
        const newest = messages.length ? messages[messages.length - 1].id : 0;
        if (newest === lastMessageId) return;
        if (lastMessageId != null && messages.some(m => m.id > lastMessageId && m.fromStore)) toast('New message from the store');
        lastMessageId = newest;
        log.innerHTML = messages.length ? messages.map(m =>
            '<div class="chat-msg ' + (m.fromStore ? 'from-store' : 'from-me') + '"><div class="chat-bubble">' + esc(m.text) + '</div>' +
            '<div class="chat-meta">' + (m.fromStore ? esc(m.staffName || 'Store') : 'You') + ' &middot; ' + when(m.createdAt) + '</div></div>').join('')
            : '<p class="muted small" style="margin:0">No messages yet.</p>';
        log.scrollTop = log.scrollHeight;
    }

    document.getElementById('chatForm').addEventListener('submit', async e => {
        e.preventDefault();
        const input = document.getElementById('chatText');
        const text = input.value.trim();
        if (!text) return;
        const button = e.target.querySelector('button');
        button.disabled = true;
        try {
            await api('POST', '/api/orders/' + id + '/messages', { text });
            input.value = '';
            await loadChat();
        } catch (err) {
            toast(err.message);
        }
        button.disabled = false;
    });

    loadChat();
    every(POLL_MS, loadChat);

    update(order);
    every(POLL_MS, async () => {
        if (order.status === 'COMPLETED' || order.status === 'CANCELLED') return;
        try {
            update(await api('GET', '/api/orders/' + id));
        } catch (e) {
            // Keep showing the last known state; the next poll will try again.
        }
    });
}

// ------------------------------------------------------------------
// Store locator
// ------------------------------------------------------------------

async function renderStores() {
    view().innerHTML = '<div class="page-head"><h1>Our stores</h1><button class="btn btn-sm" id="near">Nearest to me</button></div>' +
        '<div class="stack"><div id="storesMap" class="map tall"></div><div class="stack" id="storeList"><p class="muted">Loading stores...</p></div></div>';
    try {
        await loadStores();
    } catch (e) {
        document.getElementById('storeList').innerHTML = '<div class="empty">' + esc(e.message) + '</div>';
        return;
    }

    const points = {};
    state.stores.forEach(s => { points['s' + s.id] = Object.assign(point(s.lat, s.lng), { label: s.name + ' - ' + s.address, kind: 'store' }); });
    const map = await drawMap(document.getElementById('storesMap'), points, {});

    function drawList() {
        const me = state.position;
        const stores = state.stores.slice();
        if (me) stores.sort((a, b) => km(me, point(a.lat, a.lng)) - km(me, point(b.lat, b.lng)));
        document.getElementById('storeList').innerHTML = stores.map(s =>
            '<article class="card stack" style="gap:8px"><div class="spread"><h3>' + esc(s.name) + '</h3>' +
            (me ? '<span class="small muted">' + km(me, point(s.lat, s.lng)).toFixed(1) + ' km</span>' : '') + '</div>' +
            '<div class="muted small">' + esc(s.address) + '<br>' + esc(s.hours) + '</div>' +
            '<div class="row" style="flex-wrap:wrap">' +
            '<button class="btn btn-sm" data-show="' + s.id + '">Show on map</button>' +
            '<a class="btn btn-sm" target="_blank" rel="noopener" href="' + directionsUrl(s) + '">Directions</a>' +
            (s.phone ? '<a class="btn btn-sm" href="tel:' + esc(s.phone) + '">Call</a>' : '') +
            '<button class="btn btn-sm btn-primary" data-order="' + s.id + '">Order here</button></div></article>').join('');
    }

    document.getElementById('storeList').addEventListener('click', e => {
        const show = e.target.closest('[data-show]');
        const order = e.target.closest('[data-order]');
        if (show) {
            const s = state.stores.find(x => x.id === Number(show.dataset.show));
            map.focusOn(point(s.lat, s.lng));
            document.getElementById('storesMap').scrollIntoView({ behavior: 'smooth' });
        } else if (order) {
            state.checkout.storeId = Number(order.dataset.order);
            save('checkout', state.checkout);
            location.hash = '#/';
            toast('Ordering from ' + state.stores.find(x => x.id === state.checkout.storeId).name);
        }
    });

    document.getElementById('near').addEventListener('click', async () => {
        try {
            const me = await locate();
            map.set('me', Object.assign({}, me, { label: 'You', kind: 'me' }));
            map.fit();
            drawList();
        } catch (err) {
            toast(err.message);
        }
    });

    drawList();
}

// ------------------------------------------------------------------
// Account: sign up with your details, sign in, edit profile
// ------------------------------------------------------------------

function nextFromHash() {
    const match = location.hash.match(/next=([a-z]+)/);
    return match ? '#/' + match[1] : '#/';
}

function renderAccount() {
    if (state.token && state.customer) return renderProfile();
    let mode = 'signin';

    function draw() {
        const signup = mode === 'signup';
        view().innerHTML =
            '<div class="page-head"><h1>' + (signup ? 'Create your account' : 'Sign in') + '</h1></div>' +
            '<form class="card stack" id="auth" novalidate>' +
            (signup ? field('fullName', 'Full name', 'text', 'Juan Dela Cruz', 'name') : '') +
            (signup ? field('phone', 'Mobile number', 'tel', '09171234567', 'tel')
                : field('login', 'Mobile number or e-mail', 'text', '09171234567', 'username')) +
            (signup ? field('email', 'E-mail (optional)', 'email', 'juan@example.com', 'email') : '') +
            (signup ? '<div class="field"><label for="address">Delivery address</label><textarea id="address" name="address" autocomplete="street-address" placeholder="House no., street, barangay, city"></textarea></div>' : '') +
            field('password', 'Password', 'password', signup ? 'At least 6 characters' : '', signup ? 'new-password' : 'current-password') +
            '<p class="form-error" hidden></p>' +
            '<button class="btn btn-primary btn-block" type="submit">' + (signup ? 'Create account' : 'Sign in') + '</button>' +
            '<p class="small muted" style="margin:0;text-align:center">' + (signup ? 'Already have an account? ' : 'New here? ') +
            '<button type="button" class="link-btn" id="switch">' + (signup ? 'Sign in' : 'Create an account') + '</button></p>' +
            '</form>' +
            '<p class="muted small" style="text-align:center">Browsing prices and store locations does not need an account.</p>';

        document.getElementById('switch').addEventListener('click', () => {
            mode = signup ? 'signin' : 'signup';
            draw();
        });

        const form = document.getElementById('auth');
        form.addEventListener('submit', async e => {
            e.preventDefault();
            const data = Object.fromEntries(new FormData(form));
            const button = form.querySelector('button[type=submit]');
            button.disabled = true;
            try {
                signIn(await api('POST', signup ? '/api/register' : '/api/login', data));
                toast(signup ? 'Welcome, ' + state.customer.fullName + '!' : 'Signed in');
                if (state.checkout.address == null || state.checkout.address === '') {
                    state.checkout.address = state.customer.address;
                    save('checkout', state.checkout);
                }
                location.hash = nextFromHash();
            } catch (err) {
                showFormError(form, err);
                button.disabled = false;
            }
        });
    }

    draw();
}

function field(name, label, type, placeholder, autocomplete, value) {
    return '<div class="field"><label for="' + name + '">' + label + '</label>' +
        '<input id="' + name + '" name="' + name + '" type="' + type + '" placeholder="' + esc(placeholder) + '" autocomplete="' + autocomplete + '" value="' + esc(value || '') + '"></div>';
}

function renderProfile() {
    const c = state.customer;
    view().innerHTML =
        '<div class="page-head"><div><h1>Hi, ' + esc(c.fullName.split(' ')[0]) + '</h1><div class="muted small">Your details for delivery</div></div></div>' +
        '<form class="card stack" id="profile" novalidate>' +
        field('fullName', 'Full name', 'text', '', 'name', c.fullName) +
        field('phone', 'Mobile number', 'tel', '', 'tel', c.phone) +
        field('email', 'E-mail (optional)', 'email', '', 'email', c.email) +
        '<div class="field"><label for="address">Delivery address</label><textarea id="address" name="address" autocomplete="street-address">' + esc(c.address) + '</textarea></div>' +
        '<p class="form-error" hidden></p>' +
        '<button class="btn btn-primary btn-block" type="submit">Save details</button>' +
        '</form>' +
        '<div class="stack" style="margin-top:12px"><a class="btn btn-block" href="#/orders">My orders</a>' +
        '<button class="btn btn-block btn-danger" id="signout">Sign out</button></div>';

    const form = document.getElementById('profile');
    form.addEventListener('submit', async e => {
        e.preventDefault();
        try {
            state.customer = await api('PUT', '/api/me', Object.fromEntries(new FormData(form)));
            save('customer', state.customer);
            form.querySelector('.form-error').hidden = true;
            toast('Details saved');
        } catch (err) {
            showFormError(form, err);
        }
    });

    document.getElementById('signout').addEventListener('click', async () => {
        try {
            await api('POST', '/api/logout', {});
        } catch (e) {
            // Signing out locally is enough if the store cannot be reached.
        }
        signOutLocally();
        renderAccount();
    });
}

// ------------------------------------------------------------------
// Rider page: opened from the link the POS copies; shares the rider's GPS
// ------------------------------------------------------------------

async function renderRider(code) {
    document.getElementById('tabbar').hidden = true;
    onCleanup(() => { document.getElementById('tabbar').hidden = false; });
    view().innerHTML = '<p class="muted">Loading delivery...</p>';
    let order;
    try {
        order = await api('GET', '/api/rider/' + encodeURIComponent(code));
    } catch (e) {
        view().innerHTML = '<div class="empty">' + esc(e.message) + '</div>';
        return;
    }

    const dest = point(order.lat, order.lng);
    view().innerHTML =
        '<div class="page-head"><div><div class="muted small">Rider</div><h1>Deliver ' + esc(order.orderNo) + '</h1></div><span id="chip"></span></div>' +
        '<div class="stack">' +
        '<section class="card stack" style="gap:6px"><h2>' + esc(order.customerName) + '</h2>' +
        '<div>' + esc(order.address) + '</div>' + (order.note ? '<div class="muted small">Note: ' + esc(order.note) + '</div>' : '') +
        '<div class="row" style="margin-top:6px"><a class="btn btn-sm" href="tel:' + esc(order.customerPhone) + '">Call customer</a>' +
        (dest ? '<a class="btn btn-sm btn-primary" target="_blank" rel="noopener" href="' + directionsUrl(dest) + '">Navigate</a>' : '') + '</div></section>' +
        '<section class="card stack"><div id="riderMap" class="map"></div>' +
        '<button class="btn btn-primary btn-block" id="share">Start sharing my location</button>' +
        '<p class="small muted" id="shareNote" style="margin:0">The customer sees your position on their map while you deliver.</p></section>' +
        '<section class="card"><div class="spread total"><span>Collect</span><span class="price">' + money(order.total) + '</span></div>' +
        '<div class="muted small">' + order.itemCount + ' items &middot; pays by ' + ({ CASH: 'cash', CARD: 'card', EWALLET: 'e-wallet' }[order.payment]) + '</div></section>' +
        '</div>';
    document.getElementById('chip').innerHTML = '<span class="status status-' + order.status + '">' + esc(order.statusLabel) + '</span>';

    const store = order.store;
    const map = await drawMap(document.getElementById('riderMap'), {
        store: store ? Object.assign(point(store.lat, store.lng), { label: store.name, kind: 'store' }) : null,
        home: dest ? Object.assign(dest, { label: order.customerName, kind: 'home' }) : null
    }, { focus: ['rider', 'home', 'store'] });

    let watchId = null;
    let lastSent = 0;
    const button = document.getElementById('share');
    const note = document.getElementById('shareNote');

    function stop() {
        if (watchId != null) navigator.geolocation.clearWatch(watchId);
        watchId = null;
        button.textContent = 'Start sharing my location';
    }
    onCleanup(stop);

    button.addEventListener('click', () => {
        if (watchId != null) return stop();
        if (!window.isSecureContext || !navigator.geolocation) {
            note.textContent = 'This browser only shares GPS on a secure (https) connection. The customer will see an estimated position instead.';
            return;
        }
        button.textContent = 'Stop sharing';
        note.textContent = 'Waiting for GPS...';
        watchId = navigator.geolocation.watchPosition(async pos => {
            const me = { lat: pos.coords.latitude, lng: pos.coords.longitude };
            map.set('rider', Object.assign({}, me, { label: 'You', kind: 'rider' }));
            if (Date.now() - lastSent < RIDER_SEND_MS) return;
            lastSent = Date.now();
            try {
                const result = await api('POST', '/api/rider/' + encodeURIComponent(code) + '/location', me);
                note.textContent = result.tracking
                    ? 'Sharing live location' + (dest ? ' - ' + km(me, dest).toFixed(1) + ' km to go' : '') + '.'
                    : 'The order is not out for delivery (yet), so the customer cannot see you.';
            } catch (e) {
                note.textContent = e.message;
            }
        }, err => {
            note.textContent = err.code === 1 ? 'Location permission was denied.' : 'GPS is not available right now.';
            stop();
        }, { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 });
    });
}

// ------------------------------------------------------------------
// Install on the home screen
// ------------------------------------------------------------------

const INSTALL_SNOOZE_MS = 7 * 24 * 60 * 60 * 1000;
let installPrompt = null;

function isInstalled() {
    return window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
}

function phoneKind() {
    const ua = navigator.userAgent;
    if (/iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1)) return 'ios';
    return /Android/.test(ua) ? 'android' : 'other';
}

function installBar() {
    return document.getElementById('installBar');
}

function showInstallBar() {
    const bar = installBar();
    if (isInstalled() || Date.now() - load('installSnoozed', 0) < INSTALL_SNOOZE_MS) {
        bar.hidden = true;
        return;
    }

    bar.innerHTML =
        '<img src="icon-192.png" alt="" class="install-icon">' +
        '<div class="install-text"><strong>Get the ' + esc(state.config.storeName) + ' app</strong>' +
        '<span class="muted small">Add it to your home screen for quick ordering.</span></div>' +
        '<button type="button" class="btn btn-primary btn-sm" id="installBtn">Install</button>' +
        '<button type="button" class="install-close" id="installClose" aria-label="Not now">&times;</button>';
    bar.hidden = false;

    document.getElementById('installClose').addEventListener('click', snoozeInstall);
    document.getElementById('installBtn').addEventListener('click', async () => {
        if (!installPrompt) {
            showInstallSteps();
            return;
        }
        installPrompt.prompt();
        const choice = await installPrompt.userChoice;
        installPrompt = null;
        if (choice.outcome === 'accepted') bar.hidden = true;
    });
}

// Plain http pages, iPhones and some browsers never offer the one-tap install, so explain the menu steps.
function showInstallSteps() {
    const kind = phoneKind();
    const steps = kind === 'ios'
        ? ['Open this page in <strong>Safari</strong>.',
           'Tap the <strong>Share</strong> button (square with an arrow) at the bottom.',
           'Scroll down and tap <strong>Add to Home Screen</strong>, then <strong>Add</strong>.']
        : kind === 'android'
        ? ['Tap the <strong>&#8942;</strong> menu at the top right of Chrome.',
           'Tap <strong>Add to Home screen</strong> (or <strong>Install app</strong>).',
           'Tap <strong>Add</strong> or <strong>Install</strong>.']
        : ['Open your browser menu.', 'Choose <strong>Add to Home screen</strong> or <strong>Install app</strong>.'];

    const bar = installBar();
    bar.innerHTML =
        '<div class="install-text"><strong>Add the app to your home screen</strong>' +
        '<ol class="install-steps">' + steps.map(s => '<li>' + s + '</li>').join('') + '</ol>' +
        '<span class="muted small">The app then opens from its own icon. Your phone still needs the store Wi-Fi to order.</span></div>' +
        '<button type="button" class="install-close" id="installClose" aria-label="Close">&times;</button>';
    document.getElementById('installClose').addEventListener('click', snoozeInstall);
}

function snoozeInstall() {
    save('installSnoozed', Date.now());
    installBar().hidden = true;
}

window.addEventListener('beforeinstallprompt', e => {
    e.preventDefault();
    installPrompt = e;
    showInstallBar();
});

window.addEventListener('appinstalled', () => {
    installPrompt = null;
    installBar().hidden = true;
    toast('Installed. Open ' + state.config.storeName + ' from your home screen.');
});

// Service workers only run on https or localhost; on the plain store Wi-Fi address this is skipped.
if ('serviceWorker' in navigator && window.isSecureContext) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
}

// ------------------------------------------------------------------
// Router
// ------------------------------------------------------------------

async function route() {
    state.cleanups.splice(0).forEach(fn => fn());
    view().classList.remove('has-cart-bar');
    const hash = location.hash.replace(/^#/, '') || '/';
    const path = hash.split('?')[0];
    const tab = path.startsWith('/stores') ? 'stores' : path.startsWith('/orders') ? 'orders' : path.startsWith('/account') ? 'account' : 'shop';
    document.querySelectorAll('.tabbar a').forEach(a => a.classList.toggle('active', a.dataset.tab === tab));
    window.scrollTo(0, 0);

    let m;
    if (path === '/' || path === '') await renderShop();
    else if (path === '/cart') await renderCart();
    else if (path === '/stores') await renderStores();
    else if (path === '/orders') await renderOrders();
    else if ((m = path.match(/^\/orders\/(\d+)$/))) await renderOrder(m[1]);
    else if (path === '/account') renderAccount();
    else if ((m = path.match(/^\/rider\/([A-Z0-9]{8})$/))) await renderRider(m[1]);
    else location.hash = '#/';
}

window.addEventListener('hashchange', route);
saveCart();
loadConfig().then(() => {
    showInstallBar();
    return route();
});
