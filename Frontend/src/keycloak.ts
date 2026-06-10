import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
  url: 'https://keycloak.hiendev.online/',
  realm: 'hienbx',
  clientId: 'hienbx_fe',
});

export default keycloak;
