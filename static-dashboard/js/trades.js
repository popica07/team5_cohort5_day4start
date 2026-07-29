// ============================================================================
// TICKET-ADV106 — Advanced data table: sortable, resizable, frozen header
//
// Three behaviours, no library:
//   1. Click-to-sort  — sorts the canonical `rows` array (never the DOM) and
//                       re-renders, so repeated sorts can't compound.
//   2. Drag-to-resize — mousemove/mouseup land on `document`, not the handle,
//                       so the drag survives the cursor leaving the handle.
//   3. Frozen header  — pure CSS (`position: sticky`), see style.css.
//
// The header row is <th>-only; the frozen behaviour needs no JS.
// ============================================================================
(function () {
  const table = document.getElementById('trades-table');
  const tbody = document.getElementById('trades-tbody');
  const statusEl = document.getElementById('trades-status');
  if (!table || !tbody) return;

  // Canonical data. Sorting mutates THIS, then re-renders — sorting the DOM
  // directly would make the order depend on the previous render.
  let rows = [];

  // Backend runs on 8081 with context-path /api (see application.yml), while
  // this page is served from a plain static server, so the origin must be
  // explicit. If it isn't reachable — backend down, CORS, or 401 now that
  // ADV074 secured GET /v1/trades — we fall back to demo rows, matching the
  // "no backend required" convention already used by js/sse.js.
  const API_URL = 'http://localhost:8081/api/v1/trades?size=200';

  const DEMO_ROWS = [
    { tradeRef: 'EQU-20260603-0001', symbol: 'SAP.DE',  quantity: 1000,    price: 125.50, status: 'MATCHED' },
    { tradeRef: 'FX-20260603-0001',  symbol: 'EUR/USD', quantity: 1000000, price: 1.0852, status: 'PENDING' },
    { tradeRef: 'EQU-20260603-0002', symbol: 'AAPL',    quantity: 500,     price: 178.20, status: 'BREAK'   },
    { tradeRef: 'BND-20260602-0007', symbol: 'DE0001',  quantity: 250,     price: 99.87,  status: 'MATCHED' },
    { tradeRef: 'EQU-20260601-0011', symbol: 'MSFT',    quantity: 75,      price: 402.11, status: 'PENDING' },
    { tradeRef: 'FX-20260601-0003',  symbol: 'GBP/USD', quantity: 2500000, price: 1.2711, status: 'BREAK'   },
  ];

  // ---------- rendering ----------
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, (c) => (
      { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
    ));
  }

  function renderRows() {
    tbody.innerHTML = rows.map((r) => `
      <tr>
        <td>${escapeHtml(r.tradeRef)}</td>
        <td>${escapeHtml(r.symbol)}</td>
        <td>${escapeHtml(r.quantity)}</td>
        <td>${escapeHtml(r.price)}</td>
        <td>${escapeHtml(r.status)}</td>
      </tr>`).join('');
  }

  // ---------- 1. sortable columns ----------
  function sortBy(th) {
    const col = th.dataset.col;
    const type = th.dataset.type || 'string';
    // Toggle: anything not currently ascending becomes ascending.
    const dir = th.getAttribute('aria-sort') === 'ascending' ? 'descending' : 'ascending';

    // Only one column may advertise a sort at a time.
    table.querySelectorAll('thead th').forEach((o) => o.removeAttribute('aria-sort'));
    th.setAttribute('aria-sort', dir);
    th.dataset.dir = dir === 'ascending' ? 'asc' : 'desc';

    const mult = dir === 'ascending' ? 1 : -1;
    rows.sort((a, b) => {
      const av = a[col];
      const bv = b[col];
      if (type === 'number') return (Number(av) - Number(bv)) * mult;
      return String(av).localeCompare(String(bv)) * mult;
    });
    renderRows();
  }

  table.querySelectorAll('thead th').forEach((th) => {
    th.addEventListener('click', (e) => {
      // A click that started on the resize handle is a drag, not a sort.
      if (e.target.classList.contains('resize-handle')) return;
      sortBy(th);
    });

    // Headers are focusable, so support keyboard activation too.
    th.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        sortBy(th);
      }
    });
  });

  // ---------- 2. resizable columns ----------
  table.querySelectorAll('.resize-handle').forEach((handle) => {
    handle.addEventListener('mousedown', (e) => {
      e.preventDefault();  // don't start a text selection
      e.stopPropagation(); // don't let the <th> click handler sort

      const th = handle.closest('th');
      const startX = e.clientX;
      const startWidth = th.offsetWidth;
      document.body.classList.add('is-resizing');

      // Listeners go on DOCUMENT, not the handle: the pointer routinely
      // outruns a 4px target mid-drag, and a handle-bound listener would
      // stop firing the moment that happened.
      function onMove(ev) {
        const next = startWidth + (ev.clientX - startX);
        th.style.width = Math.max(48, next) + 'px'; // floor: keep it grabbable
      }
      function onUp() {
        document.removeEventListener('mousemove', onMove);
        document.removeEventListener('mouseup', onUp);
        document.body.classList.remove('is-resizing');
      }

      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    });
  });

  // ---------- initial load ----------
  function setStatus(message, isError) {
    if (!statusEl) return;
    statusEl.textContent = message;
    statusEl.classList.toggle('table-status--error', Boolean(isError));
  }

  // The REST payload is a PagedResponse ({items:[...]}); `content` covers a
  // raw Spring Page and a bare array covers an unwrapped list.
  function extractRows(data) {
    const list = data.items || data.content || (Array.isArray(data) ? data : []);
    return list.map((t) => ({
      tradeRef: t.tradeRef,
      symbol:   t.instrumentSymbol || t.symbol,
      quantity: t.quantity,
      price:    t.price,
      status:   t.status,
    }));
  }

  fetch(API_URL)
    .then((r) => {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    })
    .then((data) => {
      rows = extractRows(data);
      renderRows();
      setStatus(rows.length + ' trades loaded from the API.', false);
    })
    .catch((err) => {
      rows = DEMO_ROWS.slice();
      renderRows();
      setStatus('Backend unavailable (' + err.message + ') — showing demo rows. '
              + 'Sorting, resizing and the frozen header all work regardless.', true);
    });
})();
