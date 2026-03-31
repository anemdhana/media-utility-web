export interface HealthResponse {
  status: string;
  service: string;
}

const API_BASE = (import.meta.env.VITE_API_BASE || '').replace(/\/+$/, '');

export const apiUrl = (path: string): string => {
  const normalized = path.startsWith('/') ? path : `/${path}`;
  return `${API_BASE}${normalized}`;
};

export const fetchHealth = async (): Promise<HealthResponse> => {
  const response = await fetch(apiUrl('/api/health'));
  if (!response.ok) {
    throw new Error(`Health check failed: HTTP ${response.status}`);
  }
  return response.json() as Promise<HealthResponse>;
};
