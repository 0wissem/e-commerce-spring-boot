export const environment = {
  production: false,
  // Empty = same-origin: requests go to /api/... and the dev-server proxy
  // (proxy.conf.json) forwards them to the local gateway on :8090.
  apiUrl: ''
};
