import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, Suite, SuitePr } from '../api/client';

export default function SuiteDetail() {
  const { id } = useParams();
  const qc = useQueryClient();
  const [prNumber, setPrNumber] = useState('');

  const suite = useQuery({ queryKey: ['suite', id], queryFn: () => api.get<Suite>(`/suites/${id}`) });
  const prs = useQuery({ queryKey: ['suitePrs', id], queryFn: () => api.get<SuitePr[]>(`/suites/${id}/prs`) });

  const addPr = useMutation({
    mutationFn: (num: number) => api.post(`/suites/${id}/prs`, { originalPrNumber: num }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['suitePrs', id] }); setPrNumber(''); },
  });

  const removePr = useMutation({
    mutationFn: (prId: number) => api.del(`/suites/${id}/prs/${prId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['suitePrs', id] }),
  });

  const collectComments = useMutation({
    mutationFn: (prId: number) => api.post(`/suite-prs/${prId}/collect-original-comments`, {}),
  });

  return (
    <div>
      <div className="page-header">
        <h1>{suite.data?.name || 'Suite'}</h1>
      </div>

      {suite.data && (
        <div className="card">
          <p><strong>Repository:</strong> {suite.data.exemplarRepo?.name}</p>
          {suite.data.description && <p><strong>Description:</strong> {suite.data.description}</p>}
        </div>
      )}

      <div className="card">
        <h3>Add PR to Suite</h3>
        <div style={{display:'flex',gap:8,alignItems:'flex-end'}}>
          <div className="form-group" style={{flex:1,marginBottom:0}}>
            <label>PR Number</label>
            <input type="number" value={prNumber} onChange={e => setPrNumber(e.target.value)}
              placeholder="e.g. 42" />
          </div>
          <button className="btn btn-primary" onClick={() => addPr.mutate(Number(prNumber))}
            disabled={!prNumber}>Add PR</button>
        </div>
      </div>

      <div className="card">
        <h3>Suite PRs ({prs.data?.length || 0})</h3>
        {!prs.data?.length ? (
          <div className="empty-state">No PRs added yet.</div>
        ) : (
          <table>
            <thead>
              <tr><th>#</th><th>Title</th><th>Author</th><th>Files</th><th>+/-</th><th></th></tr>
            </thead>
            <tbody>
              {prs.data.map(pr => (
                <tr key={pr.id}>
                  <td>#{pr.originalPrNumber}</td>
                  <td>{pr.title}</td>
                  <td>{pr.author}</td>
                  <td>{pr.filesChanged}</td>
                  <td><span style={{color:'green'}}>+{pr.additions}</span> / <span style={{color:'red'}}>-{pr.deletions}</span></td>
                  <td>
                    <button className="btn btn-sm btn-primary" style={{marginRight:4}}
                      onClick={() => collectComments.mutate(pr.id)}>Collect Comments</button>
                    <button className="btn btn-sm btn-danger"
                      onClick={() => { if (confirm('Remove?')) removePr.mutate(pr.id); }}>Remove</button>
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
