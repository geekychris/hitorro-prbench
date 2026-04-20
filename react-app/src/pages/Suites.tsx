import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, Suite, Repo } from '../api/client';
import { Link } from 'react-router-dom';

export default function Suites() {
  const qc = useQueryClient();
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState({ exemplarRepoId: 0, name: '', description: '' });

  const suites = useQuery({ queryKey: ['suites'], queryFn: () => api.get<Suite[]>('/suites') });
  const repos = useQuery({ queryKey: ['repos'], queryFn: () => api.get<Repo[]>('/repos') });

  const createMut = useMutation({
    mutationFn: (data: typeof form) => api.post('/suites', data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['suites'] }); setShowModal(false); },
  });

  const deleteMut = useMutation({
    mutationFn: (id: number) => api.del(`/suites/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['suites'] }),
  });

  return (
    <div>
      <div className="page-header">
        <h1>Benchmark Suites</h1>
        <button className="btn btn-primary" onClick={() => setShowModal(true)}>Create Suite</button>
      </div>

      <div className="card">
        {!suites.data?.length ? (
          <div className="empty-state">No suites yet. Create one to group PRs for benchmarking.</div>
        ) : (
          <table>
            <thead><tr><th>Name</th><th>Repository</th><th>Description</th><th></th></tr></thead>
            <tbody>
              {suites.data.map(s => (
                <tr key={s.id}>
                  <td><Link to={`/suites/${s.id}`}><strong>{s.name}</strong></Link></td>
                  <td>{s.exemplarRepo?.name}</td>
                  <td>{s.description || '-'}</td>
                  <td>
                    <Link to={`/suites/${s.id}`} className="btn btn-sm btn-primary" style={{marginRight:4}}>Manage</Link>
                    <button className="btn btn-sm btn-danger" onClick={() => {
                      if (confirm('Delete this suite?')) deleteMut.mutate(s.id);
                    }}>Delete</button>
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
            <h2>Create Suite</h2>
            <div className="form-group">
              <label>Repository</label>
              <select value={form.exemplarRepoId}
                onChange={e => setForm({...form, exemplarRepoId: Number(e.target.value)})}>
                <option value={0}>Select...</option>
                {repos.data?.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
              </select>
            </div>
            <div className="form-group">
              <label>Suite Name</label>
              <input value={form.name} onChange={e => setForm({...form, name: e.target.value})} />
            </div>
            <div className="form-group">
              <label>Description</label>
              <textarea value={form.description} onChange={e => setForm({...form, description: e.target.value})} />
            </div>
            <div style={{display:'flex',gap:8,justifyContent:'flex-end',marginTop:16}}>
              <button className="btn" onClick={() => setShowModal(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={() => createMut.mutate(form)}
                disabled={!form.exemplarRepoId || !form.name}>Create</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
