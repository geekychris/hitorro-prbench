import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, Repo } from '../api/client';

interface GitHubRepo {
  fullName: string; owner: string; name: string; description: string;
  defaultBranch: string; private: boolean; fork: boolean;
  language: string; stars: number; updatedAt: string; htmlUrl: string;
  registered: boolean;
}

interface RepoStats {
  total: number;
  byOwner: Record<string, number>;
  byLanguage: Record<string, number>;
  byTag: Record<string, number>;
  withNotes: number;
  withoutNotes: number;
  forks: number;
  owned: number;
  ollamaAvailable: boolean;
}

type ExtRepo = Repo & {
  language: string; fork: boolean; isPrivate: boolean; stars: number; githubDescription: string;
};

export default function Repositories() {
  const qc = useQueryClient();
  const [search, setSearch] = useState('');
  const [tagFilter, setTagFilter] = useState('');
  const [langFilter, setLangFilter] = useState('');
  const [ownerFilter, setOwnerFilter] = useState('');
  const [forkFilter, setForkFilter] = useState<string>('');
  const [notesFilter, setNotesFilter] = useState<string>('');
  const [showBrowse, setShowBrowse] = useState(false);
  const [editingNotes, setEditingNotes] = useState<ExtRepo | null>(null);
  const [notesText, setNotesText] = useState('');
  const [newTagRepo, setNewTagRepo] = useState<ExtRepo | null>(null);
  const [newTagText, setNewTagText] = useState('');
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [bulkTag, setBulkTag] = useState('');
  const [generatingId, setGeneratingId] = useState<number | null>(null);
  const [fastTagMode, setFastTagMode] = useState(false);
  const [fastTag, setFastTag] = useState('');
  const [fastTagCustom, setFastTagCustom] = useState('');

  const buildQuery = () => {
    const params: string[] = [];
    if (search) params.push(`search=${encodeURIComponent(search)}`);
    if (tagFilter) params.push(`tag=${encodeURIComponent(tagFilter)}`);
    if (langFilter) params.push(`language=${encodeURIComponent(langFilter)}`);
    if (ownerFilter) params.push(`owner=${encodeURIComponent(ownerFilter)}`);
    if (forkFilter) params.push(`isFork=${forkFilter}`);
    if (notesFilter) params.push(`hasNotes=${notesFilter}`);
    return '/repos' + (params.length ? '?' + params.join('&') : '');
  };

  const repos = useQuery({
    queryKey: ['repos', search, tagFilter, langFilter, ownerFilter, forkFilter, notesFilter],
    queryFn: () => api.get<ExtRepo[]>(buildQuery()),
  });

  const stats = useQuery({ queryKey: ['repoStats'], queryFn: () => api.get<RepoStats>('/repos/meta/stats') });
  const allTags = useQuery({ queryKey: ['repoTags'], queryFn: () => api.get<string[]>('/repos/meta/tags') });

  const ghRepos = useQuery({
    queryKey: ['githubBrowse'],
    queryFn: () => api.get<GitHubRepo[]>('/repos/github/browse'),
    enabled: showBrowse,
  });

  const invalidateAll = () => {
    qc.invalidateQueries({ queryKey: ['repos'] });
    qc.invalidateQueries({ queryKey: ['repoStats'] });
    qc.invalidateQueries({ queryKey: ['repoTags'] });
  };

  const importMut = useMutation({
    mutationFn: (r: GitHubRepo) => api.post('/repos/import', {
      owner: r.owner, repoName: r.name, name: r.name,
      defaultBranch: r.defaultBranch, description: r.description,
      language: r.language || '', fork: String(r.fork),
      isPrivate: String(r.private), stars: String(r.stars),
    }),
    onSuccess: () => { invalidateAll(); qc.invalidateQueries({ queryKey: ['githubBrowse'] }); },
  });

  const importAllMut = useMutation({
    mutationFn: (list: GitHubRepo[]) => api.post('/repos/import-all',
      list.map(r => ({
        owner: r.owner, repoName: r.name, name: r.name,
        defaultBranch: r.defaultBranch, description: r.description,
        language: r.language || '', fork: String(r.fork),
        isPrivate: String(r.private), stars: String(r.stars),
      }))
    ),
    onSuccess: () => { invalidateAll(); qc.invalidateQueries({ queryKey: ['githubBrowse'] }); },
  });

  const deleteMut = useMutation({
    mutationFn: (id: number) => api.del(`/repos/${id}`),
    onSuccess: invalidateAll,
  });

  const addTagMut = useMutation({
    mutationFn: ({ id, tag }: { id: number; tag: string }) => api.post<ExtRepo>(`/repos/${id}/tags`, { tag }),
    onSuccess: (data) => {
      invalidateAll();
      if (newTagRepo && data.id === newTagRepo.id) setNewTagRepo(data);
    },
  });

  const removeTagMut = useMutation({
    mutationFn: ({ id, tag }: { id: number; tag: string }) => api.del<ExtRepo>(`/repos/${id}/tags/${tag}`),
    onSuccess: (data) => {
      invalidateAll();
      if (newTagRepo && data.id === newTagRepo.id) setNewTagRepo(data);
    },
  });

  const saveNotesMut = useMutation({
    mutationFn: ({ id, notes }: { id: number; notes: string }) => api.post(`/repos/${id}/notes`, { notes }),
    onSuccess: () => { invalidateAll(); setEditingNotes(null); },
  });

  const bulkTagMut = useMutation({
    mutationFn: ({ tag, repoIds }: { tag: string; repoIds: number[] }) =>
      api.post('/repos/bulk-tag', { tag, repoIds }),
    onSuccess: () => { invalidateAll(); setSelected(new Set()); setBulkTag(''); },
  });

  const generateDesc = async (repo: ExtRepo, autoSave = true) => {
    setGeneratingId(repo.id);
    try {
      const result = await api.post<{ description?: string; generated: boolean; saved?: boolean }>(
        `/repos/${repo.id}/generate-description?save=${autoSave}`, {});
      if (result.generated && result.description) {
        if (autoSave) {
          // Already saved on the backend, just refresh the list
          invalidateAll();
        } else {
          // Open the editor with the suggestion
          setEditingNotes(repo);
          setNotesText(result.description);
        }
      }
    } finally {
      setGeneratingId(null);
    }
  };

  const generateAllMut = useMutation({
    mutationFn: () => api.post<{ generated: number; failed: number }>('/repos/meta/generate-descriptions', {}),
    onSuccess: invalidateAll,
  });

  const toggleSelect = (id: number) => {
    setSelected(prev => { const s = new Set(prev); s.has(id) ? s.delete(id) : s.add(id); return s; });
  };

  const selectAll = () => {
    if (!repos.data) return;
    if (selected.size === repos.data.length) setSelected(new Set());
    else setSelected(new Set(repos.data.map(r => r.id)));
  };

  const s = stats.data;

  return (
    <div>
      <div className="page-header">
        <h1>Repositories {s ? `(${s.total})` : ''}</h1>
        <div style={{display:'flex',gap:8}}>
          <button className={`btn ${fastTagMode ? 'btn-warning' : ''}`}
            style={fastTagMode ? {} : {background:'#fff3e0',color:'#e65100'}}
            onClick={() => { setFastTagMode(!fastTagMode); setFastTag(''); setFastTagCustom(''); }}>
            {fastTagMode ? 'Exit Fast Tag' : 'Fast Tag'}
          </button>
          {s?.ollamaAvailable && s.withoutNotes > 0 && (
            <button className="btn btn-warning" onClick={() => generateAllMut.mutate()}
              disabled={generateAllMut.isPending}>
              {generateAllMut.isPending ? 'Generating...' : `AI Describe (${s.withoutNotes})`}
            </button>
          )}
          <button className="btn btn-primary" onClick={() => setShowBrowse(true)}>Browse GitHub</button>
        </div>
      </div>

      {/* Stats facets */}
      {s && (
        <div className="stats-grid">
          <div className="stat-card" style={{cursor:'pointer'}} onClick={() => { setForkFilter(''); setOwnerFilter(''); }}>
            <div className="stat-value">{s.total}</div>
            <div className="stat-label">Total Repos</div>
          </div>
          <div className="stat-card" style={{cursor:'pointer'}} onClick={() => setForkFilter('false')}>
            <div className="stat-value">{s.owned}</div>
            <div className="stat-label">Owned</div>
          </div>
          <div className="stat-card" style={{cursor:'pointer'}} onClick={() => setForkFilter('true')}>
            <div className="stat-value">{s.forks}</div>
            <div className="stat-label">Forks</div>
          </div>
          <div className="stat-card" style={{cursor:'pointer'}} onClick={() => setNotesFilter('false')}>
            <div className="stat-value">{s.withoutNotes}</div>
            <div className="stat-label">No Description</div>
          </div>
        </div>
      )}

      {/* Filters */}
      <div className="card">
        <div style={{display:'flex',gap:12,flexWrap:'wrap',alignItems:'flex-end'}}>
          <div className="form-group" style={{flex:2,marginBottom:0,minWidth:200}}>
            <label>Search (name, description, tags, language)</label>
            <input placeholder="Type to filter..." value={search}
              onChange={e => setSearch(e.target.value)} />
          </div>
          <div className="form-group" style={{flex:1,marginBottom:0,minWidth:120}}>
            <label>Owner</label>
            <select value={ownerFilter} onChange={e => setOwnerFilter(e.target.value)}>
              <option value="">All</option>
              {s && Object.entries(s.byOwner).sort((a,b) => b[1] - a[1]).map(([o, c]) => (
                <option key={o} value={o}>{o} ({c})</option>
              ))}
            </select>
          </div>
          <div className="form-group" style={{flex:1,marginBottom:0,minWidth:120}}>
            <label>Language</label>
            <select value={langFilter} onChange={e => setLangFilter(e.target.value)}>
              <option value="">All</option>
              {s && Object.entries(s.byLanguage).sort((a,b) => b[1] - a[1]).map(([l, c]) => (
                <option key={l} value={l}>{l} ({c})</option>
              ))}
            </select>
          </div>
          <div className="form-group" style={{flex:1,marginBottom:0,minWidth:120}}>
            <label>Tag</label>
            <select value={tagFilter} onChange={e => setTagFilter(e.target.value)}>
              <option value="">All</option>
              {(allTags.data || []).map(t => (
                <option key={t} value={t}>{t}{s?.byTag[t] ? ` (${s.byTag[t]})` : ''}</option>
              ))}
            </select>
          </div>
          <div className="form-group" style={{marginBottom:0,minWidth:80}}>
            <label>Type</label>
            <select value={forkFilter} onChange={e => setForkFilter(e.target.value)}>
              <option value="">All</option>
              <option value="false">Owned</option>
              <option value="true">Forks</option>
            </select>
          </div>
          <div className="form-group" style={{marginBottom:0,minWidth:100}}>
            <label>Description</label>
            <select value={notesFilter} onChange={e => setNotesFilter(e.target.value)}>
              <option value="">All</option>
              <option value="true">Has description</option>
              <option value="false">No description</option>
            </select>
          </div>
          {(search || tagFilter || langFilter || ownerFilter || forkFilter || notesFilter) && (
            <button className="btn btn-sm" onClick={() => {
              setSearch(''); setTagFilter(''); setLangFilter(''); setOwnerFilter(''); setForkFilter(''); setNotesFilter('');
            }}>Clear</button>
          )}
        </div>
      </div>

      {/* Fast Tag Mode */}
      {fastTagMode ? (
        <div className="card" style={{background:'#fff8e1',border:'2px solid var(--warning)'}}>
          <div style={{display:'flex',alignItems:'center',gap:12,marginBottom:10}}>
            <strong style={{color:'var(--warning)',fontSize:15}}>Fast Tag Mode</strong>
            <span style={{color:'var(--text-light)',fontSize:13}}>Click repos to toggle the tag on/off</span>
            <button className="btn btn-sm" style={{marginLeft:'auto'}}
              onClick={() => { setFastTagMode(false); setFastTag(''); setFastTagCustom(''); }}>Exit</button>
          </div>
          <div style={{display:'flex',gap:8,alignItems:'center',flexWrap:'wrap'}}>
            <label style={{fontSize:13,fontWeight:500}}>Tag:</label>
            {(allTags.data || []).map(t => (
              <span key={t} onClick={() => setFastTag(t)}
                style={{
                  padding:'4px 12px',borderRadius:12,fontSize:12,fontWeight:600,cursor:'pointer',
                  background: fastTag === t ? 'var(--primary)' : '#e0e0e0',
                  color: fastTag === t ? 'white' : 'var(--text)',
                }}>{t}</span>
            ))}
            <input placeholder="or type new..." value={fastTagCustom}
              onChange={e => { setFastTagCustom(e.target.value); setFastTag(''); }}
              onKeyDown={e => { if (e.key === 'Enter' && fastTagCustom.trim()) setFastTag(fastTagCustom.trim()); }}
              style={{padding:'4px 10px',border:'1px solid var(--border)',borderRadius:6,width:140,fontSize:12}} />
            {fastTagCustom.trim() && fastTag !== fastTagCustom.trim() && (
              <button className="btn btn-sm btn-primary" style={{padding:'2px 8px',fontSize:11}}
                onClick={() => setFastTag(fastTagCustom.trim())}>Use</button>
            )}
          </div>
          {fastTag && (
            <div style={{marginTop:8,fontSize:13}}>
              Active tag: <strong style={{color:'var(--primary)'}}>{fastTag}</strong>
              {' '} -- click a repo row to add, click again to remove
            </div>
          )}
        </div>
      ) : (
        <>
          {/* Bulk actions */}
          {selected.size > 0 && (
            <div className="card" style={{display:'flex',alignItems:'center',gap:12,background:'#eef2ff'}}>
              <strong>{selected.size} selected</strong>
              <input placeholder="Tag name" value={bulkTag} onChange={e => setBulkTag(e.target.value)}
                style={{padding:'6px 10px',border:'1px solid var(--border)',borderRadius:6,width:150}} />
              <button className="btn btn-sm btn-primary" disabled={!bulkTag.trim()}
                onClick={() => bulkTagMut.mutate({ tag: bulkTag.trim(), repoIds: Array.from(selected) })}>
                Tag Selected
              </button>
              <button className="btn btn-sm" onClick={() => setSelected(new Set())}>Clear Selection</button>
            </div>
          )}
        </>
      )}

      {/* Repo list */}
      <div className="card">
        {!repos.data?.length ? (
          <div className="empty-state">No repositories match your filters.</div>
        ) : (
          <table>
            <thead>
              <tr>
                <th style={{width:30}}><input type="checkbox" onChange={selectAll}
                  checked={selected.size === repos.data.length && repos.data.length > 0} /></th>
                <th>Repository</th>
                <th>Language</th>
                <th>Tags</th>
                <th>Description</th>
                <th style={{width:80}}></th>
              </tr>
            </thead>
            <tbody>
              {repos.data.map((r: ExtRepo) => {
                const repoTags = r.tags ? r.tags.split(',').filter(Boolean) : [];
                const hasFastTag = fastTagMode && fastTag && repoTags.includes(fastTag);
                return (
                <tr key={r.id}
                  style={{
                    background: fastTagMode && fastTag
                      ? (hasFastTag ? '#e8f5e9' : undefined)
                      : (selected.has(r.id) ? '#eef2ff' : undefined),
                    cursor: fastTagMode && fastTag ? 'pointer' : undefined,
                  }}
                  onClick={fastTagMode && fastTag ? () => {
                    if (hasFastTag) {
                      removeTagMut.mutate({id: r.id, tag: fastTag});
                    } else {
                      addTagMut.mutate({id: r.id, tag: fastTag});
                    }
                  } : undefined}>
                  <td>
                    {fastTagMode && fastTag ? (
                      <span style={{fontSize:16}}>{hasFastTag ? '\u2705' : '\u2B1C'}</span>
                    ) : (
                      <input type="checkbox" checked={selected.has(r.id)} onChange={() => toggleSelect(r.id)} />
                    )}
                  </td>
                  <td>
                    <a href={r.githubUrl || `https://github.com/${r.owner}/${r.repoName}`}
                      target="_blank" rel="noopener noreferrer"
                      style={{fontWeight:600,color:'var(--primary)',textDecoration:'none'}}>
                      {r.owner}/{r.repoName}
                    </a>
                    {r.fork && <span className="badge badge-pending" style={{marginLeft:6}}>fork</span>}
                    {r.isPrivate && <span className="badge badge-pending" style={{marginLeft:4}}>private</span>}
                    {r.stars > 0 && <span style={{marginLeft:6,color:'var(--text-light)',fontSize:12}}>
                      {'*'} {r.stars}
                    </span>}
                  </td>
                  <td>
                    {r.language ? (
                      <span className="badge badge-running" style={{cursor:'pointer'}}
                        onClick={() => setLangFilter(r.language)}>{r.language}</span>
                    ) : '-'}
                  </td>
                  <td style={{maxWidth:200}}>
                    <div style={{display:'flex',flexWrap:'wrap',gap:3,alignItems:'center'}}>
                      {(r.tags ? r.tags.split(',').filter(Boolean) : []).map((t: string) => (
                        <span key={t} className="badge badge-completed" style={{cursor:'pointer',fontSize:10}}
                          title="Click to remove"
                          onClick={() => removeTagMut.mutate({id: r.id, tag: t})}>{t} x</span>
                      ))}
                      <button style={{border:'none',background:'none',cursor:'pointer',color:'var(--primary)',
                        fontSize:16,lineHeight:1,padding:'0 2px'}}
                        onClick={() => { setNewTagRepo(r); setNewTagText(''); }}
                        title="Add tag">+</button>
                    </div>
                  </td>
                  <td style={{maxWidth:300}}>
                    {(() => {
                      const desc = r.notes || r.githubDescription;
                      const isCustom = !!r.notes;
                      if (desc) {
                        return (
                          <div style={{fontSize:12,cursor:'pointer',color:'var(--text)'}}
                            onClick={() => { setEditingNotes(r); setNotesText(r.notes || r.githubDescription || ''); }}
                            title="Click to edit">
                            {!isCustom && <span style={{color:'var(--text-light)',fontSize:10,marginRight:4}}>[github]</span>}
                            {desc.length > 100 ? desc.substring(0, 100) + '...' : desc}
                          </div>
                        );
                      }
                      return (
                        <div style={{display:'flex',gap:4,alignItems:'center'}}>
                          <span style={{fontSize:12,color:'var(--text-light)',cursor:'pointer'}}
                            onClick={() => { setEditingNotes(r); setNotesText(''); }}>Add description</span>
                          {s?.ollamaAvailable && (
                            <button className="btn btn-sm btn-warning" style={{padding:'1px 6px',fontSize:10}}
                              onClick={() => generateDesc(r)}
                              disabled={generatingId === r.id}>
                              {generatingId === r.id ? '...' : 'AI'}
                            </button>
                          )}
                        </div>
                      );
                    })()}
                  </td>
                  <td>
                    <button className="btn btn-sm btn-danger" style={{padding:'2px 8px',fontSize:11}}
                      onClick={() => { if (confirm('Delete ' + r.repoName + '?')) deleteMut.mutate(r.id); }}>
                      Del
                    </button>
                  </td>
                </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      {/* Browse GitHub Modal */}
      {showBrowse && <BrowseModal ghRepos={ghRepos} importMut={importMut}
        importAllMut={importAllMut} onClose={() => setShowBrowse(false)} />}

      {/* Edit Notes Modal */}
      {editingNotes && (
        <div className="modal-overlay" onClick={() => setEditingNotes(null)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h2>Description: {editingNotes.owner}/{editingNotes.repoName}</h2>
            {editingNotes.githubDescription && (
              <p style={{marginBottom:8,color:'var(--text-light)',fontSize:13}}>
                <strong>GitHub:</strong> {editingNotes.githubDescription}
              </p>
            )}
            <div className="form-group">
              <label>Description / Notes</label>
              <textarea rows={6} value={notesText} onChange={e => setNotesText(e.target.value)}
                placeholder="Describe what this repo does, its purpose, tech stack..." />
            </div>
            {s?.ollamaAvailable && (
              <button className="btn btn-sm btn-warning" style={{marginBottom:12}}
                onClick={async () => {
                  setGeneratingId(editingNotes.id);
                  try {
                    const result = await api.post<{description?: string; generated: boolean}>(
                      `/repos/${editingNotes.id}/generate-description?save=false`, {});
                    if (result.generated && result.description) setNotesText(result.description);
                  } finally { setGeneratingId(null); }
                }}
                disabled={generatingId === editingNotes.id}>
                {generatingId === editingNotes.id ? 'Generating...' : 'Generate with AI'}
              </button>
            )}
            <div style={{display:'flex',gap:8,justifyContent:'flex-end'}}>
              <button className="btn" onClick={() => setEditingNotes(null)}>Cancel</button>
              <button className="btn btn-primary"
                onClick={() => saveNotesMut.mutate({id: editingNotes.id, notes: notesText})}>Save</button>
            </div>
          </div>
        </div>
      )}

      {/* Tag Modal */}
      {newTagRepo && <TagModal
        repo={newTagRepo}
        allTags={allTags.data || []}
        tagText={newTagText}
        onTagTextChange={setNewTagText}
        onAdd={(tag) => { addTagMut.mutate({id: newTagRepo.id, tag}); setNewTagText(''); }}
        onRemove={(tag) => removeTagMut.mutate({id: newTagRepo.id, tag})}
        onClose={() => setNewTagRepo(null)}
      />}
    </div>
  );
}

function TagModal({ repo, allTags, tagText, onTagTextChange, onAdd, onRemove, onClose }: {
  repo: ExtRepo;
  allTags: string[];
  tagText: string;
  onTagTextChange: (v: string) => void;
  onAdd: (tag: string) => void;
  onRemove: (tag: string) => void;
  onClose: () => void;
}) {
  const currentTags = repo.tags ? repo.tags.split(',').filter(Boolean) : [];
  const suggestions = allTags.filter(t => !currentTags.includes(t));

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" style={{maxWidth:450}} onClick={e => e.stopPropagation()}>
        <h2>Tags: {repo.owner}/{repo.repoName}</h2>

        {/* Current tags */}
        <div style={{marginBottom:16}}>
          <label style={{fontSize:13,fontWeight:600,display:'block',marginBottom:6}}>Current Tags</label>
          {currentTags.length === 0 ? (
            <span style={{fontSize:12,color:'var(--text-light)'}}>No tags yet</span>
          ) : (
            <div style={{display:'flex',flexWrap:'wrap',gap:6}}>
              {currentTags.map(t => (
                <span key={t} style={{
                  display:'inline-flex',alignItems:'center',gap:4,
                  background:'#e8f5e9',color:'#2e7d32',borderRadius:12,
                  padding:'4px 10px',fontSize:12,fontWeight:500,
                }}>
                  {t}
                  <span style={{cursor:'pointer',fontWeight:700,marginLeft:2}}
                    onClick={() => onRemove(t)} title="Remove tag">x</span>
                </span>
              ))}
            </div>
          )}
        </div>

        {/* Add new tag */}
        <div className="form-group">
          <label>Add Tag</label>
          <div style={{display:'flex',gap:6}}>
            <input value={tagText} onChange={e => onTagTextChange(e.target.value)}
              placeholder="Type a new tag name..."
              autoFocus
              style={{flex:1}}
              onKeyDown={e => {
                if (e.key === 'Enter' && tagText.trim()) onAdd(tagText.trim());
              }} />
            <button className="btn btn-primary" disabled={!tagText.trim()}
              onClick={() => onAdd(tagText.trim())}>Add</button>
          </div>
        </div>

        {/* Suggestions from other repos */}
        {suggestions.length > 0 && (
          <div style={{marginBottom:16}}>
            <label style={{fontSize:12,color:'var(--text-light)',display:'block',marginBottom:4}}>
              Suggestions (used on other repos):
            </label>
            <div style={{display:'flex',flexWrap:'wrap',gap:4}}>
              {suggestions.map(t => (
                <span key={t} className="badge badge-running"
                  style={{cursor:'pointer',fontSize:11}}
                  onClick={() => onAdd(t)}>{t}</span>
              ))}
            </div>
          </div>
        )}

        <div style={{textAlign:'right',marginTop:12}}>
          <button className="btn" onClick={onClose}>Done</button>
        </div>
      </div>
    </div>
  );
}

function BrowseModal({ ghRepos, importMut, importAllMut, onClose }: {
  ghRepos: any; importMut: any; importAllMut: any; onClose: () => void;
}) {
  const allGh: GitHubRepo[] = Array.isArray(ghRepos.data) ? ghRepos.data : [];
  const isError = !Array.isArray(ghRepos.data) && (ghRepos.data as any)?.error;
  const importedCount = allGh.filter(r => r.registered).length;
  const notImported = allGh.filter(r => !r.registered);
  const [browseSearch, setBrowseSearch] = useState('');

  const filtered = browseSearch
    ? allGh.filter(r => r.fullName.toLowerCase().includes(browseSearch.toLowerCase()) ||
        (r.description && r.description.toLowerCase().includes(browseSearch.toLowerCase())) ||
        (r.language && r.language.toLowerCase().includes(browseSearch.toLowerCase())))
    : allGh;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" style={{maxWidth:850}} onClick={e => e.stopPropagation()}>
        <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:12}}>
          <h2 style={{margin:0}}>Browse GitHub Repositories</h2>
          {!ghRepos.isLoading && !isError && notImported.length > 0 && (
            <button className="btn btn-success"
              onClick={() => importAllMut.mutate(notImported)}
              disabled={importAllMut.isPending}>
              {importAllMut.isPending ? 'Importing...' : `Import All (${notImported.length})`}
            </button>
          )}
        </div>

        {!ghRepos.isLoading && !isError && allGh.length > 0 && (
          <div style={{display:'flex',gap:16,marginBottom:8,alignItems:'center'}}>
            <span><strong>{allGh.length}</strong> found</span>
            <span style={{color:'var(--success)'}}><strong>{importedCount}</strong> imported</span>
            <span style={{color:'var(--text-light)'}}><strong>{notImported.length}</strong> not imported</span>
            <input placeholder="Filter..." value={browseSearch}
              onChange={e => setBrowseSearch(e.target.value)}
              style={{marginLeft:'auto',padding:'4px 10px',border:'1px solid var(--border)',borderRadius:6,width:200}} />
          </div>
        )}

        {ghRepos.isLoading ? (
          <div className="empty-state">Loading repos from GitHub...</div>
        ) : isError ? (
          <div className="empty-state" style={{color:'var(--danger)'}}>
            Error: {(ghRepos.data as any).error}<br/>
            Make sure your GitHub token is set in Settings.
          </div>
        ) : (
          <div style={{maxHeight:'60vh',overflowY:'auto'}}>
            <table>
              <thead><tr><th>Repository</th><th>Language</th><th>Updated</th><th>Status</th></tr></thead>
              <tbody>
                {filtered.map(r => (
                  <tr key={r.fullName} style={{background: r.registered ? '#f0faf0' : undefined}}>
                    <td>
                      <strong>{r.fullName}</strong>
                      {r.private && <span className="badge badge-pending" style={{marginLeft:4}}>private</span>}
                      {r.fork && <span className="badge badge-pending" style={{marginLeft:4}}>fork</span>}
                      {r.description && <br/>}
                      {r.description && <small style={{color:'var(--text-light)'}}>{r.description}</small>}
                    </td>
                    <td>{r.language || '-'}</td>
                    <td>{r.updatedAt ? new Date(r.updatedAt).toLocaleDateString() : '-'}</td>
                    <td>
                      {r.registered ? (
                        <span className="badge badge-completed">Imported</span>
                      ) : (
                        <button className="btn btn-sm btn-primary"
                          onClick={() => importMut.mutate(r)}>Import</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div style={{textAlign:'right',marginTop:12}}>
          <button className="btn" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  );
}
