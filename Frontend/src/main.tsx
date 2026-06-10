import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import keycloak from './keycloak'

keycloak.init({ 
  onLoad: 'login-required',
  checkLoginIframe: false
}).then((authenticated) => {
  if (authenticated) {
    createRoot(document.getElementById('root')!).render(
      <StrictMode>
        <App />
      </StrictMode>,
    )
  } else {
    window.location.reload();
  }
}).catch((error) => {
  console.error("Keycloak initialization failed", error);
  createRoot(document.getElementById('root')!).render(
    <div style={{ 
      display: 'flex', 
      flexDirection: 'column', 
      alignItems: 'center', 
      justifyContent: 'center', 
      height: '100vh', 
      fontFamily: 'system-ui, sans-serif',
      backgroundColor: '#0f172a',
      color: '#f8fafc'
    }}>
      <h2 style={{ color: '#ef4444', marginBottom: '10px' }}>Lỗi xác thực hệ thống</h2>
      <p style={{ color: '#94a3b8' }}>Không thể kết nối với máy chủ xác thực Keycloak. Vui lòng kiểm tra cấu hình mạng hoặc CORS.</p>
      <button 
        onClick={() => window.location.reload()} 
        style={{
          marginTop: '20px',
          padding: '10px 20px',
          backgroundColor: '#3b82f6',
          color: 'white',
          border: 'none',
          borderRadius: '6px',
          cursor: 'pointer',
          fontWeight: '600'
        }}
      >
        Thử lại
      </button>
    </div>
  );
});
