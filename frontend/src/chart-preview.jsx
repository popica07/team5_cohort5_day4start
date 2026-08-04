// TEMPORARY — visual check for MonthlyTradesChart. Delete after review.
import React from 'react';
import { createRoot } from 'react-dom/client';
import MonthlyTradesChart from '@components/MonthlyTradesChart.jsx';
import './styles/global.css';

const LABELS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
const COUNTS = [42, 61, 128, 97, 110, 143, 189, 156, 132, 174, 88, 63];
const months = LABELS.map((label, i) => ({ month: i + 1, label, count: COUNTS[i] }));
const empty  = LABELS.map((label, i) => ({ month: i + 1, label, count: 0 }));

function Preview() {
  return (
    <main className="layout__main" style={{ maxWidth: 900 }}>
      <MonthlyTradesChart year={2026} months={months} total={COUNTS.reduce((a, b) => a + b, 0)} />
      <MonthlyTradesChart year={2019} months={empty} total={0} />
    </main>
  );
}

document.documentElement.dataset.theme =
  new URLSearchParams(location.search).get('theme') === 'dark' ? 'dark' : 'light';

createRoot(document.getElementById('root')).render(<Preview />);
