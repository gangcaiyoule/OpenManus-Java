import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './styles.css';

// SockJS/STOMP dependencies may reference Node's global in browser runtime.
if ((globalThis as Record<string, unknown>).global === undefined) {
  (globalThis as Record<string, unknown>).global = globalThis;
}

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
