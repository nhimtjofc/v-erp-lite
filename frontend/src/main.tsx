import React from 'react'
import ReactDOM from 'react-dom/client'
import { Providers } from './app/providers'
import './index.css'

// Register hey-api interceptors (401 auto-refresh)
import './shared/api/interceptors'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Providers />
  </React.StrictMode>
)

