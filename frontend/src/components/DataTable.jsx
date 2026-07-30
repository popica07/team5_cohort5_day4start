import {
  createContext,
  useContext,
  useMemo,
  useState,
} from 'react';

const DataTableContext = createContext(null);

function useDataTable() {
  const context = useContext(DataTableContext);

  if (!context) {
    throw new Error(
      'DataTable sub-components must be used inside <DataTable>.'
    );
  }

  return context;
}

function compareValues(left, right) {
  if (left == null && right == null) return 0;
  if (left == null) return 1;
  if (right == null) return -1;

  if (typeof left === 'number' && typeof right === 'number') {
    return left - right;
  }

  return String(left).localeCompare(String(right), undefined, {
    numeric: true,
    sensitivity: 'base',
  });
}

export default function DataTable({
  data = [],
  children,
  pageSize = 20,
  onSortChange,
}) {
  const [sortKey, setSortKey] = useState(null);
  const [sortDirection, setSortDirection] = useState('asc');
  const [page, setPage] = useState(0);

  const handleSort = (key) => {
    const nextDirection =
      sortKey === key && sortDirection === 'asc' ? 'desc' : 'asc';

    setSortKey(key);
    setSortDirection(nextDirection);
    setPage(0);
    onSortChange?.(key);
  };

  const sortedData = useMemo(() => {
    if (!sortKey) {
      return [...data];
    }

    return [...data].sort((left, right) => {
      const result = compareValues(left?.[sortKey], right?.[sortKey]);

      return sortDirection === 'asc' ? result : -result;
    });
  }, [data, sortKey, sortDirection]);

  const totalPages = Math.max(
    1,
    Math.ceil(sortedData.length / pageSize)
  );

  const visibleRows = useMemo(() => {
    const start = page * pageSize;

    return sortedData.slice(start, start + pageSize);
  }, [sortedData, page, pageSize]);

  const contextValue = {
    sortKey,
    sortDirection,
    handleSort,
    page,
    setPage,
    pageSize,
    totalPages,
    visibleRows,
  };

  return (
    <DataTableContext.Provider value={contextValue}>
      <div className="data-table">{children}</div>
    </DataTableContext.Provider>
  );
}

DataTable.Header = function Header({ columns = [] }) {
  const {
    sortKey,
    sortDirection,
    handleSort,
  } = useDataTable();

  return (
    <div className="data-table__header" role="row">
      {columns.map((column) => {
        const isActive = sortKey === column.key;

        return (
          <button
            key={column.key}
            type="button"
            className={
              isActive
                ? 'data-table__header-cell data-table__header-cell--active'
                : 'data-table__header-cell'
            }
            onClick={() => handleSort(column.key)}
            aria-sort={
              isActive
                ? sortDirection === 'asc'
                  ? 'ascending'
                  : 'descending'
                : 'none'
            }
          >
            {column.label}

            {isActive && (
              <span aria-hidden="true">
                {sortDirection === 'asc' ? ' ▲' : ' ▼'}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
};

DataTable.Body = function Body({
  rows,
  render,
  renderRow,
}) {
  const { visibleRows } = useDataTable();

  const rowsToRender = rows ?? visibleRows;
  const rowRenderer = renderRow ?? render;

  if (typeof rowRenderer !== 'function') {
    return null;
  }

  return (
    <div className="data-table__body">
      {rowsToRender.map((row, index) => (
        <div
          className="data-table__row"
          role="row"
          key={row?.id ?? row?.key ?? index}
        >
          {rowRenderer(row, index)}
        </div>
      ))}
    </div>
  );
};

DataTable.Pagination = function Pagination() {
  const {
    page,
    setPage,
    totalPages,
  } = useDataTable();

  return (
    <nav
      className="data-table__pagination"
      aria-label="Pagination"
    >
      <button
        type="button"
        disabled={page === 0}
        onClick={() =>
          setPage((currentPage) =>
            Math.max(0, currentPage - 1)
          )
        }
      >
        Previous
      </button>

      <span>
        {page + 1} / {totalPages}
      </span>

      <button
        type="button"
        disabled={page >= totalPages - 1}
        onClick={() =>
          setPage((currentPage) =>
            Math.min(totalPages - 1, currentPage + 1)
          )
        }
      >
        Next
      </button>
    </nav>
  );
};