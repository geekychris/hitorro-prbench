import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, ReviewComment } from '../api/client';
import { useState } from 'react';

export default function ReplayPrDetail() {
  const { id } = useParams();
  const qc = useQueryClient();
  const [gradingComment, setGradingComment] = useState<ReviewComment | null>(null);
  const [verdict, setVerdict] = useState('VALID');
  const [severity, setSeverity] = useState('MINOR');

  const comments = useQuery({
    queryKey: ['replayComments', id],
    queryFn: () => api.get<ReviewComment[]>(`/replay-prs/${id}/comments`),
  });

  const gradeMut = useMutation({
    mutationFn: (data: { commentId: number; verdict: string; severity: string }) =>
      api.post('/gradings', { ...data, commentTableType: 'REVIEW', graderType: 'HUMAN' }),
    onSuccess: () => { setGradingComment(null); qc.invalidateQueries({ queryKey: ['replayComments', id] }); },
  });

  const promoteMut = useMutation({
    mutationFn: (commentId: number) =>
      api.post('/golden-dataset/promote', { commentId, commentTableType: 'REVIEW' }),
  });

  return (
    <div>
      <div className="page-header">
        <h1>Replay PR #{id} Comments</h1>
      </div>

      <div className="card">
        <h3>Comments ({comments.data?.length || 0})</h3>
        {!comments.data?.length ? (
          <div className="empty-state">No comments collected yet.</div>
        ) : (
          comments.data.map(c => (
            <div key={c.id} style={{border:'1px solid var(--border)',borderRadius:8,padding:12,marginBottom:12}}>
              <div style={{display:'flex',justifyContent:'space-between',marginBottom:8}}>
                <div>
                  <strong>{c.bot?.name || 'Unknown'}</strong>
                  <span style={{marginLeft:8,color:'var(--text-light)'}}>{c.commentType}</span>
                  {c.filePath && (
                    <span style={{marginLeft:8,fontFamily:'monospace',fontSize:12}}>
                      {c.filePath}{c.lineNumber ? `:${c.lineNumber}` : ''}
                    </span>
                  )}
                </div>
                <div style={{display:'flex',gap:4}}>
                  <button className="btn btn-sm btn-primary" onClick={() => setGradingComment(c)}>Grade</button>
                  <button className="btn btn-sm btn-success" onClick={() => promoteMut.mutate(c.id)}>Promote</button>
                </div>
              </div>
              <div className="comment-body">{c.body}</div>
            </div>
          ))
        )}
      </div>

      {gradingComment && (
        <div className="modal-overlay" onClick={() => setGradingComment(null)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h2>Grade Comment</h2>
            <div className="comment-body" style={{marginBottom:12}}>{gradingComment.body}</div>
            <div className="form-row">
              <div className="form-group">
                <label>Verdict</label>
                <select value={verdict} onChange={e => setVerdict(e.target.value)}>
                  <option value="VALID">Valid</option>
                  <option value="INVALID">Invalid</option>
                  <option value="DUPLICATE">Duplicate</option>
                  <option value="NEEDS_REVIEW">Needs Review</option>
                </select>
              </div>
              <div className="form-group">
                <label>Severity</label>
                <select value={severity} onChange={e => setSeverity(e.target.value)}>
                  <option value="CRITICAL">Critical</option>
                  <option value="MAJOR">Major</option>
                  <option value="MINOR">Minor</option>
                  <option value="NITPICK">Nitpick</option>
                </select>
              </div>
            </div>
            <div style={{display:'flex',gap:8,justifyContent:'flex-end',marginTop:16}}>
              <button className="btn" onClick={() => setGradingComment(null)}>Cancel</button>
              <button className="btn btn-primary"
                onClick={() => gradeMut.mutate({ commentId: gradingComment.id, verdict, severity })}>Save</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
