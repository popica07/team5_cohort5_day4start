// TICKET-ADV114 — Compound DataTable.
// TICKET-ADV117 — useDebouncedSearch.
import React, { useEffect, useState } from 'react';
import { withAuth } from '@components/withAuth.jsx';
import DataTable from '@components/DataTable.jsx';
import { useDebouncedSearch } from '@hooks/useDebouncedSearch.js';
import { api } from '@services/apiService.js';

// Column key -> sortable property on the Trade JPA entity. The backend sorts
// through tradeRepo.findAll(spec, pageable), so these must be entity property
// names, not the JSON field names the DTO happens to expose.
const SORT_FIELDS = {
    tradeRef: 'tradeRef',
    symbol:   'instrument.symbol',
    qty:      'quantity',
    price:    'price',
    status:   'status',
};

function Trades() {
    const [search, setSearch] = useState('');
    const debounced = useDebouncedSearch(search, 300);
    const [page, setPage] = useState(0);
    const [sort, setSort] = useState(null);   // { key, dir } — null uses the backend default
    const [data, setData] = useState({ items: [], totalPages: 0 });

    function handleSortChange(key, dir) {
        if (!SORT_FIELDS[key]) return;        // column the backend can't sort on
        setSort({ key, dir });
        setPage(0);                            // re-sorting reshuffles every page
    }

    useEffect(() => {
        let cancelled = false;
        const params = new URLSearchParams();
        params.set('page', String(page));
        if (debounced) params.set('status', debounced);
        if (sort) params.set('sort', `${SORT_FIELDS[sort.key]},${sort.dir}`);

        api.listTrades(params.toString())
            .then((res) => {
                if (cancelled) return;
                if (res && Array.isArray(res.items)) {
                    setData({ items: res.items, totalPages: res.totalPages ?? 0 });
                } else if (Array.isArray(res)) {
                    setData({ items: res, totalPages: 1 });
                } else {
                    setData({ items: [], totalPages: 0 });
                }
            })
            .catch(() => {
                if (!cancelled) setData({ items: [], totalPages: 0 });
            });

        return () => { cancelled = true; };
    }, [page, debounced, sort]);

    const totalPages = Math.max(1, data.totalPages);

    return (
        <section>
            <h2>Trades</h2>
            <input
                aria-label="Filter by status"
                placeholder="status filter (PENDING/MATCHED/…)"
                value={search}
                onChange={(e) => {
                    setSearch(e.target.value.toUpperCase());
                    // A new filter shrinks the result set — staying on page 3
                    // would ask the server for a page that no longer exists.
                    setPage(0);
                }}
            />
            <DataTable onSortChange={handleSortChange}>
                <DataTable.Header columns={[
                    { key: 'tradeRef', label: 'Ref' },
                    { key: 'symbol',   label: 'Symbol' },
                    { key: 'qty',      label: 'Qty' },
                    { key: 'price',    label: 'Price' },
                    { key: 'status',   label: 'Status' },
                ]} />
                <DataTable.Body
                    rows={data.items}
                    render={(t) => (
                        <>
                            <span>{t.tradeRef}</span>
                            <span>{t.symbol ?? t.instrument}</span>
                            <span>{t.qty ?? t.quantity}</span>
                            <span>{t.price}</span>
                            <span>{t.status}</span>
                        </>
                    )}
                />
            </DataTable>

            {/* Pagination is server-side here (the fetch above keys off `page`),
                so we drive our own state rather than DataTable.Pagination, which
                paginates client-side over the `data` prop we don't pass. */}
            <nav className="data-table__pagination" aria-label="Pagination">
                <button
                    type="button"
                    disabled={page === 0}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                    Previous
                </button>
                <span>{page + 1} / {totalPages}</span>
                <button
                    type="button"
                    disabled={page >= totalPages - 1}
                    onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                >
                    Next
                </button>
            </nav>
        </section>
    );
}

export default withAuth(Trades);

