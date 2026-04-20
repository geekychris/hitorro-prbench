import { useState, useEffect } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { api } from '../api/client';

export default function Settings() {
  const [token, setToken] = useState('');

  const status = useQuery({
    queryKey: ['setupStatus'],
    queryFn: () => api.get<{ githubTokenSet: boolean; ready: boolean }>('/setup/status'),
  });

  const rateLimit = useQuery({
    queryKey: ['rateLimit'],
    queryFn: () => api.get<any>('/setup/rate-limit'),
    enabled: status.data?.githubTokenSet === true,
  });

  const setTokenMut = useMutation({
    mutationFn: (t: string) => api.post('/setup/token', { token: t }),
    onSuccess: () => {
      status.refetch();
      rateLimit.refetch();
    },
  });

  return (
    <div>
      <div className="page-header"><h1>Settings</h1></div>

      <div className="card">
        <h3>GitHub Authentication</h3>
        <p style={{marginBottom:12,color:'var(--text-light)'}}>
          A GitHub personal access token is required for accessing repositories and creating PRs.
        </p>

        <div style={{marginBottom:12}}>
          <span style={{fontWeight:600}}>Status: </span>
          {status.data?.githubTokenSet ? (
            <span className="badge badge-completed">Token Set</span>
          ) : (
            <span className="badge badge-failed">No Token</span>
          )}
        </div>

        <div className="form-group">
          <label>GitHub Personal Access Token</label>
          <input type="password" value={token} onChange={e => setToken(e.target.value)}
            placeholder="ghp_..." />
        </div>
        <button className="btn btn-primary" onClick={() => setTokenMut.mutate(token)}
          disabled={!token}>Save Token</button>
      </div>

      {rateLimit.data && (
        <div className="card">
          <h3>API Rate Limit</h3>
          {rateLimit.data.resources && (
            <table>
              <thead><tr><th>Resource</th><th>Limit</th><th>Used</th><th>Remaining</th><th>Resets At</th></tr></thead>
              <tbody>
                {Object.entries(rateLimit.data.resources).map(([name, r]: [string, any]) => (
                  <tr key={name}>
                    <td>{name}</td>
                    <td>{r.limit}</td>
                    <td>{r.used}</td>
                    <td>{r.remaining}</td>
                    <td>{new Date(r.reset * 1000).toLocaleTimeString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      <div className="card">
        <h3>About</h3>
        <p>PR Bench v1.0.0</p>
        <p style={{color:'var(--text-light)'}}>
          AI PR review bot benchmarking tool. Powered by HiTorro GitTools.
        </p>
        <p style={{marginTop:8,color:'var(--text-light)'}}>
          Backend: Spring Boot 3.2 + H2 Database | Frontend: React + TypeScript + Vite
        </p>
      </div>
    </div>
  );
}
