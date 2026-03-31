import React, { useCallback, useEffect, useState } from 'react';
import { fetchHealth, HealthResponse } from './api';

const App: React.FC = () => {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string>('');
  const [lastCheckedAt, setLastCheckedAt] = useState<Date | null>(null);
  const [autoRefreshEnabled, setAutoRefreshEnabled] = useState<boolean>(true);

  const loadHealth = useCallback(async (options?: { silent?: boolean }) => {
    const silent = options?.silent ?? false;
    if (!silent) {
      setLoading(true);
    }
    setError('');
    try {
      const result = await fetchHealth();
      setHealth(result);
    } catch (err) {
      setHealth(null);
      setError(err instanceof Error ? err.message : 'Unable to reach backend');
    } finally {
      if (!silent) {
        setLoading(false);
      }
      setLastCheckedAt(new Date());
    }
  }, []);

  useEffect(() => {
    loadHealth();
  }, [loadHealth]);

  useEffect(() => {
    if (!autoRefreshEnabled) {
      return;
    }
    const intervalId = window.setInterval(() => {
      loadHealth({ silent: true });
    }, 30000);

    return () => window.clearInterval(intervalId);
  }, [autoRefreshEnabled, loadHealth]);

  const formattedLastCheckedAt = lastCheckedAt
    ? lastCheckedAt.toLocaleTimeString()
    : 'Never';

  const isHealthy = health?.status?.toUpperCase() === 'UP';
  const statusPillStyle = {
    display: 'inline-block',
    padding: '0.2rem 0.55rem',
    borderRadius: 999,
    fontSize: '0.8rem',
    fontWeight: 700,
    background: isHealthy ? '#e8f7ed' : '#fff3e0',
    color: isHealthy ? '#1b5e20' : '#8a5200',
    border: `1px solid ${isHealthy ? '#7bcf92' : '#f3c27a'}`
  } as const;

  return (
    <main style={{ maxWidth: 720, margin: '2rem auto', fontFamily: 'Segoe UI, sans-serif', padding: '0 1rem' }}>
      <h1>Media Utility Web</h1>
      <p>Frontend baseline is ready. Current backend connectivity status:</p>
      <div style={{ display: 'flex', gap: '0.6rem', alignItems: 'center', marginBottom: '0.75rem' }}>
        <p style={{ color: '#666', fontSize: '0.9rem', margin: 0 }}>
          Auto-refresh every 30 seconds: <strong>{autoRefreshEnabled ? 'ON' : 'PAUSED'}</strong>. Last checked: {formattedLastCheckedAt}
        </p>
        <button
          onClick={() => setAutoRefreshEnabled(prev => !prev)}
          style={{ padding: '0.35rem 0.7rem', cursor: 'pointer' }}
        >
          {autoRefreshEnabled ? 'Pause' : 'Resume'}
        </button>
      </div>

      {loading && <p>Checking backend health...</p>}

      {!loading && error && (
        <div style={{ border: '1px solid #d9534f', padding: '0.75rem', borderRadius: 8, background: '#fff5f5' }}>
          <p style={{ marginTop: 0, color: '#a94442' }}>Health check failed.</p>
          <p style={{ marginBottom: '0.75rem' }}>{error}</p>
          <button onClick={loadHealth} style={{ padding: '0.4rem 0.75rem', cursor: 'pointer' }}>Retry</button>
        </div>
      )}

      {!loading && health && (
        <div style={{ border: `1px solid ${isHealthy ? '#2e7d32' : '#d28a22'}`, padding: '0.75rem', borderRadius: 8, background: isHealthy ? '#f1fff3' : '#fffbf2' }}>
          <p style={{ marginTop: 0, marginBottom: '0.4rem' }}>
            <strong>Status:</strong> <span style={statusPillStyle}>{health.status}</span>
          </p>
          <p style={{ margin: 0 }}>
            <strong>Service:</strong> {health.service}
          </p>
        </div>
      )}
    </main>
  );
};

export default App;
