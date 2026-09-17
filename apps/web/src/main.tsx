import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from 'react-oidc-context';
import '@chessquery/ui-lib';
import './app.css';
import { oidcConfig } from './auth/oidc';
import { TokenSync } from './auth/guards';
import { App } from './App';

// Punto de entrada. Orden de providers: sesión OIDC → puente del token → datos (react-query) → rutas.
const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 30_000, retry: 1, refetchOnWindowFocus: false } },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AuthProvider {...oidcConfig}>
      <TokenSync>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </QueryClientProvider>
      </TokenSync>
    </AuthProvider>
  </React.StrictMode>,
);
