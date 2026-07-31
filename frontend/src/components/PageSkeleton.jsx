function PageSkeleton() {
  return (
    <div className="page-skeleton" aria-label="Loading page">
      <div className="page-skeleton__title" />

      <div className="page-skeleton__cards">
        <div className="page-skeleton__card" />
        <div className="page-skeleton__card" />
        <div className="page-skeleton__card" />
      </div>

      <div className="page-skeleton__table">
        <div className="page-skeleton__row" />
        <div className="page-skeleton__row" />
        <div className="page-skeleton__row" />
        <div className="page-skeleton__row" />
      </div>
    </div>
  );
}

export default PageSkeleton;