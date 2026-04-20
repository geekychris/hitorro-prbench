import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, BenchmarkRun, ReplayPr } from '../api/client';

export default function RunDetail() {
  const { id } = useParams();
  const qc = useQueryClient();

  const run = useQuery({ queryKey: ['run', id], queryFn: () => api.get<BenchmarkRun>(`/runs/${id}`) });
  const replays = useQuery({ queryKey: ['replays', id], queryFn: () => api.get<ReplayPr[]>(`/runs/${id}/replay-prs`) });
  const progress = useQuery({
    queryKey: ['progress', id],
    queryFn: () => api.get<Record<string, number>>(`/runs/${id}/progress`),
    refetchInterval: run.data?.status === 'RUNNING' ? 5000 : false,
  });

  const cancelMut = useMutation({
    mutationFn: () => api.post(`/runs/${id}/cancel`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['run', id] }),
  });

  const cleanupMut = useMutation({
    mutationFn: () => api.post(`/runs/${id}/cleanup`, {}),
  });

  const r = run.data;

  return (
    <div>
      <div className="page-header">
        <h1>{r?.name || 'Run'}</h1>
        <div style={{display:'flex',gap:8}}>
          {r?.status === 'RUNNING' && (
            <button className="btn btn-danger" onClick={() => cancelMut.mutate()}>Cancel</button>
          )}
          {r?.status === 'COMPLETED' && (
            <>
              <Link to={`/reports/${id}`} className="btn btn-success">View Report</Link>
              <button className="btn btn-warning" onClick={() => cleanupMut.mutate()}>Cleanup Mirror</button>
            </>
          )}
        </div>
      </div>

      {r && (
        <div className="stats-grid">
          <div className="stat-card">
            <div className="stat-value"><span className={`badge badge-${r.status.toLowerCase()}`}>{r.status}</span></div>
            <div className="stat-label">Status</div>
          </div>
          <div className="stat-card">
            <div className="stat-value">{replays.data?.length || 0}</div>
            <div className="stat-label">Replay PRs</div>
          </div>
          <div className="stat-card">
            <div className="stat-value">{r.concurrency}</div>
            <div className="stat-label">Concurrency</div>
          </div>
        </div>
      )}

      {progress.data && Object.keys(progress.data).length > 0 && (
        <div className="card">
          <h3>Progress</h3>
          <div style={{display:'flex',gap:16,flexWrap:'wrap'}}>
            {Object.entries(progress.data).map(([status, count]) => (
              <div key={status}>
                <span className={`badge badge-${status.toLowerCase()}`}>{status}</span>: {count}
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="card">
        <h3>Replay PRs</h3>
        {!replays.data?.length ? (
          <div className="empty-state">No replay PRs created yet.</div>
        ) : (
          <table>
            <thead><tr><th>PR</th><th>Bot</th><th>Mirror PR</th><th>Status</th><th></th></tr></thead>
            <tbody>
              {replays.data.map(rp => (
                <tr key={rp.id}>
                  <td>#{rp.suitePr?.originalPrNumber}: {rp.suitePr?.title}</td>
                  <td>{rp.bot?.name}</td>
                  <td>{rp.mirrorPrNumber ? `#${rp.mirrorPrNumber}` : '-'}</td>
                  <td><span className={`badge badge-${rp.status.toLowerCase()}`}>{rp.status}</span></td>
                  <td>
                    <Link to={`/replay-prs/${rp.id}`} className="btn btn-sm btn-primary">Comments</Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
