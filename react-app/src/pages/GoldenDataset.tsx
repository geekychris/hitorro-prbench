import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, GoldenEntry } from '../api/client';

export default function GoldenDataset() {
  const qc = useQueryClient();
  const entries = useQuery({
    queryKey: ['golden'],
    queryFn: () => api.get<GoldenEntry[]>('/golden-dataset'),
  });

  const deactivate = useMutation({
    mutationFn: (id: number) => api.del(`/golden-dataset/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['golden'] }),
  });

  const exportDataset = async () => {
    const data = await api.get<GoldenEntry[]>('/golden-dataset/export');
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'golden-dataset.json';
    a.click();
  };

  return (
    <div>
      <div className="page-header">
        <h1>Golden Dataset</h1>
        <button className="btn btn-primary" onClick={exportDataset}>Export JSON</button>
      </div>

      <div className="card">
        <p style={{marginBottom:12,color:'var(--text-light)'}}>
          Ground truth issues that bots should find. Promote comments from replay PRs or original PRs to build this dataset.
        </p>
        {!entries.data?.length ? (
          <div className="empty-state">No golden dataset entries yet. Grade and promote comments to build your dataset.</div>
        ) : (
          <table>
            <thead><tr><th>File</th><th>Line</th><th>Issue Type</th><th>Description</th><th>Active</th><th></th></tr></thead>
            <tbody>
              {entries.data.map(e => (
                <tr key={e.id}>
                  <td style={{fontFamily:'monospace',fontSize:12}}>{e.filePath || '-'}</td>
                  <td>{e.lineNumber || '-'}</td>
                  <td>{e.issueType || '-'}</td>
                  <td>{e.description || e.canonicalBody?.substring(0, 80)}</td>
                  <td>{e.active ? 'Yes' : 'No'}</td>
                  <td>
                    <button className="btn btn-sm btn-danger" onClick={() => deactivate.mutate(e.id)}>
                      Deactivate
                    </button>
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
