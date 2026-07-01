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

setTimeout(() => {
  const token = localStorage.getItem('access_token');
  console.log('DEBUG_ACCESS_TOKEN_RAW:', token);
  if (token) {
    fetch('http://localhost:8081/api/users/me', { headers: { 'Authorization': 'Bearer ' + token } })
      .then(r => r.json())
      .then(data => console.log('DEBUG_ME_RESPONSE:', JSON.stringify(data)))
      .catch(err => console.error('DEBUG_ME_ERROR:', err));
  } else {
    console.log('DEBUG_ME_RESPONSE: No access token found');
  }
}, 1000);

