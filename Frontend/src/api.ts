import axios from 'axios';
import keycloak from './keycloak';

const api = axios.create({
  baseURL: 'http://localhost:8080',
});

api.interceptors.request.use(
  async (config) => {
    if (keycloak.token) {
      try {
        // Refresh token if it will expire in 30 seconds
        await keycloak.updateToken(30);
        config.headers.Authorization = `Bearer ${keycloak.token}`;
      } catch (err) {
        console.error('Failed to refresh Keycloak token, redirecting to login', err);
        keycloak.login();
      }
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export default api;
