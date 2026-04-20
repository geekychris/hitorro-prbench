import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ReviewComment, BenchmarkRun } from '../api/client';

export default function GradingQueue() {
  const qc = useQueryClient();
  const [runId, setRunId] = useState<number>(0);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [bulkVerdict, setBulkVerdict] = useState('VALID');

  const runs = useQuery({ queryKey: ['runs'], queryFn: () => api.get<BenchmarkRun[]>('/runs') });
  const queue = useQuery({
    queryKey: ['gradingQueue', runId],
    queryFn: () => api.get<ReviewComment[]>(`/grading-queue?runId=${runId}&limit=50`),
    enabled: runId > 0,
  });
  const progress = useQuery({
    queryKey: ['gradingProgress', runId],
    queryFn: () => api.get<any>(`/grading-progress?runId=${runId}`),
    enabled: runId > 0,
  });

  const gradeMut = useMutation({
    mutationFn: (data: { commentId: number; verdict: string }) =>
      api.post('/gradings', { ...data, commentTableType: 'REVIEW', graderType: 'HUMAN' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['gradingQueue', runId] }),
  });

  const bulkMut = useMutation({
    mutationFn: () => api.post('/gradings/bulk', {
      commentIds: Array.from(selected),
      commentTableType: 'REVIEW',
      verdict: bulkVerdict,
    }),
    onSuccess: () => {
      setSelected(new Set());
      qc.invalidateQueries({ queryKey: ['gradingQueue', runId] });
      qc.invalidateQueries({ queryKey: ['gradingProgress', runId] });
    },
  });

  const toggleSelect = (id: number) => {
    setSelected(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  return (
    <div>
      <div className="page-header"><h1>Grading Queue</h1></div>

      <div className="card">
        <div className="form-group">
          <label>Select Run</label>
          <select value={runId} onChange={e => { setRunId(Number(e.target.value)); setSelected(new Set()); }}>
            <option value={0}>Select a run...</option>
            {runs.data?.map(r => <option key={r.id} value={r.id}>{r.name} ({r.status})</option>)}
          </select>
        </div>
      </div>

      {progress.data && (
        <div className="stats-grid">
          <div className="stat-card">
            <div className="stat-value">{progress.data.totalComments}</div>
            <div className="stat-label">Total Comments</div>
          </div>
          <div className="stat-card">
            <div className="stat-value">{progress.data.gradedCount}</div>
            <div className="stat-label">Graded</div>
          </div>
          <div className="stat-card">
            <div className="stat-value">{progress.data.ungradedCount}</div>
            <div className="stat-label">Remaining</div>
          </div>
        </div>
      )}

      {selected.size > 0 && (
        <div className="card" style={{display:'flex',alignItems:'center',gap:12}}>
          <strong>{selected.size} selected</strong>
          <select value={bulkVerdict} onChange={e => setBulkVerdict(e.target.value)}>
            <option value="VALID">Valid</option>
            <option value="INVALID">Invalid</option>
            <option value="DUPLICATE">Duplicate</option>
          </select>
          <button className="btn btn-primary" onClick={() => bulkMut.mutate()}>Bulk Grade</button>
        </div>
      )}

      {queue.data && (
        <div className="card">
          <h3>Ungraded Comments</h3>
          {!queue.data.length ? (
            <div className="empty-state">All comments graded!</div>
          ) : (
            queue.data.map(c => (
              <div key={c.id} style={{border:'1px solid var(--border)',borderRadius:8,padding:12,marginBottom:8}}>
                <div style={{display:'flex',justifyContent:'space-between',marginBottom:6}}>
                  <div style={{display:'flex',alignItems:'center',gap:8}}>
                    <input type="checkbox" checked={selected.has(c.id)} onChange={() => toggleSelect(c.id)} />
                    <strong>{c.bot?.name}</strong>
                    {c.filePath && <code style={{fontSize:11}}>{c.filePath}:{c.lineNumber}</code>}
                  </div>
                  <div style={{display:'flex',gap:4}}>
                    {['VALID','INVALID','DUPLICATE'].map(v => (
                      <button key={v} className={`btn btn-sm btn-${v==='VALID'?'success':v==='INVALID'?'danger':'warning'}`}
                        onClick={() => gradeMut.mutate({commentId:c.id, verdict:v})}>{v}</button>
                    ))}
                  </div>
                </div>
                <div className="comment-body">{c.body}</div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
