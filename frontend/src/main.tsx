import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { Buffer } from 'buffer';
import '@fontsource-variable/schibsted-grotesk';
import '@fontsource/ibm-plex-mono/500.css';
import '@fontsource/ibm-plex-mono/600.css';
import './services/axiosConfig';
import App from './App';

// Buffer polyfill for @react-pdf/renderer
if (typeof window !== 'undefined') {
  window.Buffer = Buffer;
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
