import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QuotePage } from './pages/QuotePage';
import './styles.css';

const root = document.getElementById('root');
if (!root) throw new Error('Missing #root element');

createRoot(root).render(
  <StrictMode>
    <QuotePage />
  </StrictMode>,
);
