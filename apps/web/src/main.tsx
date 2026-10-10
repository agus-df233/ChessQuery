import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from 'react-oidc-context';
import { ToastProvider } from '@chessquery/ui-lib';
import './app.css';
import { oidcConfig } from './auth/oidc';
import { TokenSync } from './auth/guards';
import { SessionManager } from './auth/session';
import { App } from './App';

// Punto de entrada. Orden de providers: sesión OIDC → avisos → puente del token y manejo de sesión → datos → rutas.
const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 30_000, retry: 1, refetchOnWindowFocus: false } },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AuthProvider {...oidcConfig}>
      <ToastProvider>
        <TokenSync>
          <SessionManager>
            <QueryClientProvider client={queryClient}>
              <BrowserRouter>
                <App />
              </BrowserRouter>
            </QueryClientProvider>
          </SessionManager>
        </TokenSync>
      </ToastProvider>
    </AuthProvider>
  </React.StrictMode>,
);
