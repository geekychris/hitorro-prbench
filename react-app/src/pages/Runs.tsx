import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, BenchmarkRun, Suite, Bot } from '../api/client';
import { Link } from 'react-router-dom';

export default function Runs() {
  const qc = useQueryClient();
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState({ suiteId: 0, name: '', botIds: [] as number[], concurrency: 2 });

  const runs = useQuery({ queryKey: ['runs'], queryFn: () => api.get<BenchmarkRun[]>('/runs') });
  const suites = useQuery({ queryKey: ['suites'], queryFn: () => api.get<Suite[]>('/suites') });
  const bots = useQuery({ queryKey: ['bots'], queryFn: () => api.get<Bot[]>('/bots') });

  const createMut = useMutation({
    mutationFn: (data: typeof form) => api.post('/runs', data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['runs'] }); setShowModal(false); },
  });

  const toggleBot = (id: number) => {
    setForm(f => ({
      ...f,
      botIds: f.botIds.includes(id) ? f.botIds.filter(b => b !== id) : [...f.botIds, id],
    }));
  };

  return (
    <div>
      <div className="page-header">
        <h1>Benchmark Runs</h1>
        <button className="btn btn-primary" onClick={() => setShowModal(true)}>New Run</button>
      </div>

      <div className="card">
        {!runs.data?.length ? (
          <div className="empty-state">No runs yet.</div>
        ) : (
          <table>
            <thead><tr><th>Name</th><th>Suite</th><th>Status</th><th>Bots</th><th>Created</th><th></th></tr></thead>
            <tbody>
              {[...(runs.data || [])].reverse().map(r => (
                <tr key={r.id}>
                  <td><Link to={`/runs/${r.id}`}><strong>{r.name}</strong></Link></td>
                  <td>{r.suite?.name}</td>
                  <td><span className={`badge badge-${r.status.toLowerCase()}`}>{r.status}</span></td>
                  <td>{r.bots?.map(b => b.name).join(', ')}</td>
                  <td>{new Date(r.createdAt).toLocaleString()}</td>
                  <td>
                    <Link to={`/runs/${r.id}`} className="btn btn-sm btn-primary" style={{marginRight:4}}>Details</Link>
                    <Link to={`/reports/${r.id}`} className="btn btn-sm btn-success">Report</Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h2>Start Benchmark Run</h2>
            <div className="form-group">
              <label>Suite</label>
              <select value={form.suiteId} onChange={e => setForm({...form, suiteId: Number(e.target.value)})}>
                <option value={0}>Select...</option>
                {suites.data?.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </div>
            <div className="form-group">
              <label>Run Name</label>
              <input value={form.name} onChange={e => setForm({...form, name: e.target.value})}
                placeholder="e.g. Baseline comparison" />
            </div>
            <div className="form-group">
              <label>Bots</label>
              <div style={{display:'flex',flexWrap:'wrap',gap:8}}>
                {bots.data?.map(b => (
                  <label key={b.id} style={{display:'flex',alignItems:'center',gap:4,cursor:'pointer'}}>
                    <input type="checkbox" checked={form.botIds.includes(b.id)}
                      onChange={() => toggleBot(b.id)} />
                    {b.name}
                  </label>
                ))}
              </div>
            </div>
            <div className="form-group">
              <label>Concurrency</label>
              <input type="number" min={1} max={10} value={form.concurrency}
                onChange={e => setForm({...form, concurrency: Number(e.target.value)})} />
            </div>
            <div style={{display:'flex',gap:8,justifyContent:'flex-end',marginTop:16}}>
              <button className="btn" onClick={() => setShowModal(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={() => createMut.mutate(form)}
                disabled={!form.suiteId || !form.botIds.length}>Start Run</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
