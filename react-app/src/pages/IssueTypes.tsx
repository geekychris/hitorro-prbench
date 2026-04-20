import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, IssueType } from '../api/client';

export default function IssueTypes() {
  const qc = useQueryClient();
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState({ code: '', name: '', description: '', category: '', severityHint: 'MINOR' });

  const types = useQuery({ queryKey: ['issueTypes'], queryFn: () => api.get<IssueType[]>('/issue-types?activeOnly=false') });

  const createMut = useMutation({
    mutationFn: (data: typeof form) => api.post('/issue-types', data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['issueTypes'] }); setShowModal(false); },
  });

  const deactivate = useMutation({
    mutationFn: (id: number) => api.del(`/issue-types/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['issueTypes'] }),
  });

  return (
    <div>
      <div className="page-header">
        <h1>Issue Types</h1>
        <button className="btn btn-primary" onClick={() => setShowModal(true)}>Add Type</button>
      </div>

      <div className="card">
        <table>
          <thead><tr><th>Code</th><th>Name</th><th>Category</th><th>Severity</th><th>Active</th><th></th></tr></thead>
          <tbody>
            {types.data?.map(t => (
              <tr key={t.id} style={{opacity: t.active ? 1 : 0.5}}>
                <td><code>{t.code}</code></td>
                <td><strong>{t.name}</strong><br/><small>{t.description}</small></td>
                <td>{t.category}</td>
                <td>{t.severityHint}</td>
                <td>{t.active ? 'Yes' : 'No'}</td>
                <td>
                  {t.active && (
                    <button className="btn btn-sm btn-danger" onClick={() => deactivate.mutate(t.id)}>
                      Deactivate
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h2>Add Issue Type</h2>
            <div className="form-row">
              <div className="form-group">
                <label>Code</label>
                <input placeholder="e.g. BUFFER_OVERFLOW" value={form.code}
                  onChange={e => setForm({...form, code: e.target.value})} />
              </div>
              <div className="form-group">
                <label>Name</label>
                <input value={form.name} onChange={e => setForm({...form, name: e.target.value})} />
              </div>
            </div>
            <div className="form-row">
              <div className="form-group">
                <label>Category</label>
                <input placeholder="e.g. BUG, SECURITY" value={form.category}
                  onChange={e => setForm({...form, category: e.target.value})} />
              </div>
              <div className="form-group">
                <label>Severity Hint</label>
                <select value={form.severityHint} onChange={e => setForm({...form, severityHint: e.target.value})}>
                  <option value="CRITICAL">Critical</option>
                  <option value="MAJOR">Major</option>
                  <option value="MINOR">Minor</option>
                  <option value="NITPICK">Nitpick</option>
                </select>
              </div>
            </div>
            <div className="form-group">
              <label>Description</label>
              <textarea value={form.description} onChange={e => setForm({...form, description: e.target.value})} />
            </div>
            <div style={{display:'flex',gap:8,justifyContent:'flex-end',marginTop:16}}>
              <button className="btn" onClick={() => setShowModal(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={() => createMut.mutate(form)}
                disabled={!form.code || !form.name}>Create</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
