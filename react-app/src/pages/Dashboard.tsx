import { useQuery } from '@tanstack/react-query';
import { api, Repo, Suite, BenchmarkRun, Bot } from '../api/client';
import { Link } from 'react-router-dom';

export default function Dashboard() {
  const repos = useQuery({ queryKey: ['repos'], queryFn: () => api.get<Repo[]>('/repos') });
  const suites = useQuery({ queryKey: ['suites'], queryFn: () => api.get<Suite[]>('/suites') });
  const runs = useQuery({ queryKey: ['runs'], queryFn: () => api.get<BenchmarkRun[]>('/runs') });
  const bots = useQuery({ queryKey: ['bots'], queryFn: () => api.get<Bot[]>('/bots') });

  const recentRuns = (runs.data || []).slice(-5).reverse();

  return (
    <div>
      <div className="page-header"><h1>Dashboard</h1></div>

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-value">{repos.data?.length ?? 0}</div>
          <div className="stat-label">Repositories</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">{suites.data?.length ?? 0}</div>
          <div className="stat-label">Benchmark Suites</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">{bots.data?.length ?? 0}</div>
          <div className="stat-label">Bots</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">{runs.data?.length ?? 0}</div>
          <div className="stat-label">Total Runs</div>
        </div>
      </div>

      <div className="card">
        <h3>Recent Runs</h3>
        {recentRuns.length === 0 ? (
          <div className="empty-state">No runs yet. Create a suite and start benchmarking!</div>
        ) : (
          <table>
            <thead><tr><th>Name</th><th>Suite</th><th>Status</th><th>Created</th><th></th></tr></thead>
            <tbody>
              {recentRuns.map(r => (
                <tr key={r.id}>
                  <td>{r.name}</td>
                  <td>{r.suite?.name}</td>
                  <td><span className={`badge badge-${r.status.toLowerCase()}`}>{r.status}</span></td>
                  <td>{new Date(r.createdAt).toLocaleDateString()}</td>
                  <td><Link to={`/runs/${r.id}`} className="btn btn-sm btn-primary">View</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
