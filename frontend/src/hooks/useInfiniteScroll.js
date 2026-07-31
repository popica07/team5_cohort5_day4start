// useInfiniteScroll: invokes the latest loadMore() when sentinel is visible.
import { useEffect, useRef } from 'react';

export function useInfiniteScroll(loadMore) {
  const sentinelRef = useRef(null);
  const loadMoreRef = useRef(loadMore);

  loadMoreRef.current = loadMore;

  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel) return undefined;

    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) loadMoreRef.current();
    }, { threshold: 0.1 });

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, []);

  return sentinelRef;
}