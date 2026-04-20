import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, Bot } from '../api/client';

export default function Bots() {
  const qc = useQueryClient();
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState({
    name: '', description: '', workflowFileName: '', workflowContent: '',
    waitStrategy: 'BOTH', timeoutSeconds: 600,
  });

  const bots = useQuery({ queryKey: ['bots'], queryFn: () => api.get<Bot[]>('/bots') });

  const createMut = useMutation({
    mutationFn: (data: typeof form) => api.post('/bots', data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['bots'] }); setShowModal(false); },
  });

  const deleteMut = useMutation({
    mutationFn: (id: number) => api.del(`/bots/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['bots'] }),
  });

  return (
    <div>
      <div className="page-header">
        <h1>AI Review Bots</h1>
        <button className="btn btn-primary" onClick={() => setShowModal(true)}>Add Bot</button>
      </div>

      <div className="card">
        {!bots.data?.length ? (
          <div className="empty-state">No bots defined. Add a bot with its GitHub Actions workflow.</div>
        ) : (
          <table>
            <thead><tr><th>Name</th><th>Workflow</th><th>Wait Strategy</th><th>Timeout</th><th></th></tr></thead>
            <tbody>
              {bots.data.map(b => (
                <tr key={b.id}>
                  <td><strong>{b.name}</strong><br/><small>{b.description}</small></td>
                  <td>{b.workflowFileName || '-'}</td>
                  <td>{b.waitStrategy}</td>
                  <td>{b.timeoutSeconds}s</td>
                  <td>
                    <button className="btn btn-sm btn-danger" onClick={() => {
                      if (confirm('Delete bot?')) deleteMut.mutate(b.id);
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
            <h2>Add Bot</h2>
            <div className="form-group">
              <label>Name</label>
              <input value={form.name} onChange={e => setForm({...form, name: e.target.value})} />
            </div>
            <div className="form-group">
              <label>Description</label>
              <input value={form.description} onChange={e => setForm({...form, description: e.target.value})} />
            </div>
            <div className="form-row">
              <div className="form-group">
                <label>Workflow File Name</label>
                <input placeholder="review-bot.yml" value={form.workflowFileName}
                  onChange={e => setForm({...form, workflowFileName: e.target.value})} />
              </div>
              <div className="form-group">
                <label>Wait Strategy</label>
                <select value={form.waitStrategy} onChange={e => setForm({...form, waitStrategy: e.target.value})}>
                  <option value="BOTH">Both (Checks + Reviews)</option>
                  <option value="CHECKS">Checks Only</option>
                  <option value="REVIEWS">Reviews Only</option>
                </select>
              </div>
            </div>
            <div className="form-group">
              <label>Timeout (seconds)</label>
              <input type="number" value={form.timeoutSeconds}
                onChange={e => setForm({...form, timeoutSeconds: Number(e.target.value)})} />
            </div>
            <div className="form-group">
              <label>Workflow YAML Content</label>
              <textarea rows={10} value={form.workflowContent}
                onChange={e => setForm({...form, workflowContent: e.target.value})}
                placeholder="name: Review Bot&#10;on: [pull_request]&#10;..." />
            </div>
            <div style={{display:'flex',gap:8,justifyContent:'flex-end',marginTop:16}}>
              <button className="btn" onClick={() => setShowModal(false)}>Cancel</button>
              <button className="btn btn-primary" onClick={() => createMut.mutate(form)}
                disabled={!form.name}>Create</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
