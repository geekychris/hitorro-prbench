import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { marked } from 'marked';
import { api, Repo, DocFile } from '../api/client';

type ExtRepo = Repo & { language: string; fork: boolean; isPrivate: boolean; stars: number; githubDescription: string };

export default function RepoReport() {
  const qc = useQueryClient();
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [report, setReport] = useState<string | null>(null);
  const [includeDocs, setIncludeDocs] = useState(true);
  const [includeTags, setIncludeTags] = useState(true);
  const [title, setTitle] = useState('Repository Report');
  const [expandedDocs, setExpandedDocs] = useState<Set<number>>(new Set());
  const [scanningId, setScanningId] = useState<number | null>(null);
  const [showRawMarkdown, setShowRawMarkdown] = useState(false);

  const renderedHtml = useMemo(() => {
    if (!report) return '';
    return marked(report, { async: false }) as string;
  }, [report]);

  const repos = useQuery({
    queryKey: ['repos'],
    queryFn: () => api.get<ExtRepo[]>('/repos'),
  });

  const scanMut = useMutation({
    mutationFn: (id: number) => {
      setScanningId(id);
      return api.post<{ docs: DocFile[]; count: number }>(`/repos/${id}/scan-docs`, {});
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['repos'] }); setScanningId(null); },
    onError: () => setScanningId(null),
  });

  const bulkScanMut = useMutation({
    mutationFn: (ids: number[]) => api.post<{ scanned: number }>('/repos/meta/scan-docs', { repoIds: ids }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['repos'] }),
  });

  const scanAllMut = useMutation({
    mutationFn: () => api.post<{ scanned: number }>('/repos/meta/scan-docs', {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['repos'] }),
  });

  const reportMut = useMutation({
    mutationFn: () => api.post<{ markdown: string; repoCount: number }>('/repos/meta/report', {
      repoIds: selected.size > 0 ? Array.from(selected) : null,
      title, includeDocs, includeTags,
    }),
    onSuccess: (data) => setReport(data.markdown),
  });

  const toggleSelect = (id: number) => {
    setSelected(prev => { const s = new Set(prev); s.has(id) ? s.delete(id) : s.add(id); return s; });
  };

  const selectAll = () => {
    if (!repos.data) return;
    setSelected(prev => prev.size === repos.data!.length ? new Set() : new Set(repos.data!.map(r => r.id)));
  };

  const toggleDocs = (id: number) => {
    setExpandedDocs(prev => { const s = new Set(prev); s.has(id) ? s.delete(id) : s.add(id); return s; });
  };

  const parseDocs = (repo: ExtRepo): DocFile[] => {
    if (!repo.docsJson) return [];
    try { return JSON.parse(repo.docsJson); } catch { return []; }
  };

  const downloadReport = () => {
    if (!report) return;
    const blob = new Blob([report], { type: 'text/markdown' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = title.toLowerCase().replace(/\s+/g, '-') + '.md';
    a.click();
    URL.revokeObjectURL(url);
  };

  const copyReport = () => {
    if (report) navigator.clipboard.writeText(report);
  };

  const downloadPdf = () => {
    if (!renderedHtml) return;
    // Open a new window with styled HTML and trigger browser print-to-PDF
    const win = window.open('', '_blank');
    if (!win) return;
    win.document.write(`<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>${title}</title>
<style>
  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; font-size: 13px; line-height: 1.6; color: #333; max-width: 800px; margin: 0 auto; padding: 20px; }
  h1 { font-size: 24px; border-bottom: 2px solid #ddd; padding-bottom: 8px; }
  h2 { font-size: 20px; border-bottom: 1px solid #eee; padding-bottom: 6px; margin-top: 24px; }
  h3 { font-size: 16px; margin-top: 20px; }
  table { width: 100%; border-collapse: collapse; margin: 12px 0; font-size: 12px; }
  th, td { border: 1px solid #ddd; padding: 6px 10px; text-align: left; vertical-align: top; }
  th { background: #f5f5f5; font-weight: 600; }
  tr:nth-child(even) { background: #fafafa; }
  a { color: #0f3460; text-decoration: none; }
  hr { border: none; border-top: 1px solid #ddd; margin: 16px 0; }
  ul, ol { padding-left: 24px; }
  li { margin: 4px 0; }
  code { background: #f0f0f0; padding: 1px 4px; border-radius: 3px; font-size: 11px; }
  @media print { body { margin: 0; padding: 10px; } }
</style></head><body>${renderedHtml}</body></html>`);
    win.document.close();
    // Wait for rendering then trigger print dialog (Save as PDF)
    setTimeout(() => { win.print(); }, 300);
  };

  const unscannedCount = (repos.data || []).filter(r => !r.docsScannedAt).length;
  const selectedUnscanned = Array.from(selected).filter(id =>
    repos.data?.find(r => r.id === id && !r.docsScannedAt));

  const categoryIcon = (cat: string) => {
    switch (cat) {
      case 'readme': return '\u{1F4D6}';
      case 'docs': return '\u{1F4C4}';
      case 'changelog': return '\u{1F4CB}';
      case 'contributing': return '\u{1F91D}';
      case 'license': return '\u2696\uFE0F';
      default: return '\u{1F4DD}';
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1>Report & Docs</h1>
        <div style={{display:'flex',gap:8}}>
          {unscannedCount > 0 && (
            <button className="btn btn-warning" onClick={() => scanAllMut.mutate()}
              disabled={scanAllMut.isPending}>
              {scanAllMut.isPending ? 'Scanning...' : `Scan All Unscanned (${unscannedCount})`}
            </button>
          )}
          {selectedUnscanned.length > 0 && (
            <button className="btn btn-warning" onClick={() => bulkScanMut.mutate(selectedUnscanned)}
              disabled={bulkScanMut.isPending}>
              Scan Selected ({selectedUnscanned.length})
            </button>
          )}
        </div>
      </div>

      {/* Report config */}
      <div className="card">
        <h3>Generate Report</h3>
        <div style={{display:'flex',gap:12,alignItems:'flex-end',flexWrap:'wrap'}}>
          <div className="form-group" style={{flex:2,marginBottom:0,minWidth:200}}>
            <label>Report Title</label>
            <input value={title} onChange={e => setTitle(e.target.value)} />
          </div>
          <label style={{display:'flex',alignItems:'center',gap:4,fontSize:13,cursor:'pointer'}}>
            <input type="checkbox" checked={includeDocs} onChange={e => setIncludeDocs(e.target.checked)} />
            Include docs
          </label>
          <label style={{display:'flex',alignItems:'center',gap:4,fontSize:13,cursor:'pointer'}}>
            <input type="checkbox" checked={includeTags} onChange={e => setIncludeTags(e.target.checked)} />
            Include tags
          </label>
          <button className="btn btn-primary" onClick={() => reportMut.mutate()}
            disabled={reportMut.isPending}>
            {reportMut.isPending ? 'Generating...' :
              selected.size > 0 ? `Generate (${selected.size} repos)` : 'Generate (all repos)'}
          </button>
        </div>
      </div>

      {/* Repo selection table */}
      <div className="card">
        <h3>Repositories ({repos.data?.length || 0}) - select repos for report</h3>
        <table>
          <thead>
            <tr>
              <th style={{width:30}}>
                <input type="checkbox" onChange={selectAll}
                  checked={repos.data ? selected.size === repos.data.length && repos.data.length > 0 : false} />
              </th>
              <th>Repository</th>
              <th>Language</th>
              <th>Tags</th>
              <th>Docs</th>
              <th style={{width:60}}>Scan</th>
            </tr>
          </thead>
          <tbody>
            {(repos.data || []).map((r: ExtRepo) => {
              const docs = parseDocs(r);
              const isExpanded = expandedDocs.has(r.id);
              return (
                <tr key={r.id} style={{background: selected.has(r.id) ? '#eef2ff' : undefined, verticalAlign:'top'}}>
                  <td><input type="checkbox" checked={selected.has(r.id)} onChange={() => toggleSelect(r.id)} /></td>
                  <td>
                    <a href={r.githubUrl || `https://github.com/${r.owner}/${r.repoName}`}
                      target="_blank" rel="noopener noreferrer"
                      style={{fontWeight:600,color:'var(--primary)',textDecoration:'none'}}>
                      {r.owner}/{r.repoName}
                    </a>
                    {r.fork && <span className="badge badge-pending" style={{marginLeft:4}}>fork</span>}
                    {(r.notes || r.githubDescription) && (
                      <div style={{fontSize:11,color:'var(--text-light)',marginTop:2}}>
                        {(r.notes || r.githubDescription || '').substring(0, 80)}
                      </div>
                    )}
                  </td>
                  <td>{r.language || '-'}</td>
                  <td style={{fontSize:11}}>
                    {r.tags ? r.tags.split(',').filter(Boolean).map(t => (
                      <span key={t} className="badge badge-completed" style={{fontSize:9,marginRight:2}}>{t}</span>
                    )) : '-'}
                  </td>
                  <td>
                    {!r.docsScannedAt ? (
                      <span style={{fontSize:11,color:'var(--text-light)'}}>Not scanned</span>
                    ) : docs.length === 0 ? (
                      <span style={{fontSize:11,color:'var(--text-light)'}}>No docs</span>
                    ) : (
                      <div>
                        <span style={{fontSize:12,cursor:'pointer',color:'var(--primary)'}}
                          onClick={() => toggleDocs(r.id)}>
                          {docs.length} file{docs.length !== 1 ? 's' : ''} {isExpanded ? '\u25B2' : '\u25BC'}
                        </span>
                        {isExpanded && (
                          <div style={{marginTop:4}}>
                            {docs.map((d, i) => (
                              <div key={i} style={{fontSize:11,marginBottom:2}}>
                                <span>{categoryIcon(d.category)} </span>
                                <a href={d.url} target="_blank" rel="noopener noreferrer"
                                  style={{color:'var(--primary)'}}>{d.path}</a>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    )}
                  </td>
                  <td>
                    <button className="btn btn-sm" style={{padding:'2px 6px',fontSize:10}}
                      onClick={() => scanMut.mutate(r.id)}
                      disabled={scanningId === r.id}>
                      {scanningId === r.id ? '...' : r.docsScannedAt ? 'Rescan' : 'Scan'}
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Report output */}
      {report && (
        <div className="card">
          <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:12}}>
            <h3 style={{margin:0}}>Generated Report</h3>
            <div style={{display:'flex',gap:8,alignItems:'center'}}>
              <label style={{display:'flex',alignItems:'center',gap:4,fontSize:12,cursor:'pointer'}}>
                <input type="checkbox" checked={showRawMarkdown}
                  onChange={e => setShowRawMarkdown(e.target.checked)} />
                Raw
              </label>
              <button className="btn btn-sm btn-primary" onClick={copyReport}>Copy MD</button>
              <button className="btn btn-sm btn-success" onClick={downloadReport}>Download .md</button>
              <button className="btn btn-sm" style={{background:'#c0392b',color:'white'}} onClick={downloadPdf}>Download PDF</button>
              <button className="btn btn-sm" onClick={() => setReport(null)}>Close</button>
            </div>
          </div>
          {showRawMarkdown ? (
            <div style={{
              background:'#f8f9fa',border:'1px solid var(--border)',borderRadius:6,
              padding:16,maxHeight:'60vh',overflowY:'auto',
              fontFamily:'monospace',fontSize:12,whiteSpace:'pre-wrap',lineHeight:1.6,
            }}>
              {report}
            </div>
          ) : (
            <div className="markdown-report" style={{
              background:'#fff',border:'1px solid var(--border)',borderRadius:6,
              padding:24,maxHeight:'70vh',overflowY:'auto',
            }} dangerouslySetInnerHTML={{ __html: renderedHtml }} />
          )}
        </div>
      )}
    </div>
  );
}
