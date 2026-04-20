import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, Repo } from '../api/client';

export default function Repositories() {
  const qc = useQueryClient();
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState({ githubUrl: '', name: '', mirrorOrg: '', defaultBranch: 'main' });

  const repos = useQuery({ queryKey: ['repos'], queryFn: () => api.get<Repo[]>('/repos') });

  const createMut = useMutation({
    mutationFn: (data: typeof form) => api.post('/repos', data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['repos'] }); setShowModal(false); },
  });

  const deleteMut = useMutation({
    mutationFn: (id: number) => api.del(`/repos/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['repos'] }),
  });

  return (
    <div>
      <div className="page-header">
        <h1>Exemplar Repositories</h1>
        <button className="btn btn-primary" onClick={() => setShowModal(true)}>Add Repository</button>
      </div>

      <div className="card">
        {!repos.data?.length ? (
          <div className="empty-state">No repositories registered. Add a GitHub repository to get started.</div>
        ) : (
          <table>
            <thead><tr><th>Name</th><th>Owner / Repo</th><th>Branch</th><th>Mirror</th><th></th></tr></thead>
            <tbody>
              {repos.data.map(r => (
                <tr key={r.id}>
                  <td><strong>{r.name}</strong></td>
                  <td>{r.owner}/{r.repoName}</td>
                  <td>{r.defaultBranch}</td>
                  <td>{r.mirrorOrg ? `${r.mirrorOrg}/${r.mirrorRepoName || r.repoName + '-mirror'}` : '-'}</td>
                  <td>
                    <button className="btn btn-sm btn-danger" onClick={() => {
                      if (confirm('Delete this repository?')) deleteMut.mutate(r.id);
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
            <h2>Add Repository</h2>
            <div className="form-group">
              <label>GitHub URL</label>
              <input placeholder="https://github.com/owner/repo" value={form.githubUrl}
                onChange={e => setForm({...form, githubUrl: e.target.value})} />
            </div>
            <div className="form-row">
              <div className="form-group">
                <label>Display Name (optional)</label>
                <input value={form.name} onChange={e => setForm({...form, name: e.target.value})} />
              </div>
              <div className="form-group">
                <label>Default Branch</label>
                <input value={form.defaultBranch} onChange={e => setForm({...form, defaultBranch: e.target.value})} />
              </div>
            </div>
            <div className="form-group">
              <label>Mirror Organization (optional)</label>
              <input placeholder="your-org" value={form.mirrorOrg}
                onChange={e => setForm({...form, mirrorOrg: e.target.value})} />
            </div>
            <div style={{display:'flex',gap:8,justifyContent:'flex-end',marginTop:16}}>
              <button className="btn" onClick={() => setShowModal(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={() => createMut.mutate(form)}>Add</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
