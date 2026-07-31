// TICKET-ADV117 — useDebouncedSearch(query, delay).
import { useEffect, useState } from 'react';

export function useDebouncedSearch(query, delay = 300) {
  // TODO(TICKET-ADV117): hold a debounced copy of `query` in useState, then
  //                     useEffect with setTimeout(setDebounced, delay).
  //                     Remember to clearTimeout in the cleanup function.
   const [debounced, setDebounced] = useState(query);
   useEffect(() => {
     const id = setTimeout(() => setDebounced(query), delay);
     return () => clearTimeout(id);
   }, [query, delay]);
   return debounced;
 }

