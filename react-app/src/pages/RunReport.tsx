import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';

export default function RunReport() {
  const { runId } = useParams();

  const report = useQuery({
    queryKey: ['report', runId],
    queryFn: () => api.get<any>(`/reports/runs/${runId}`),
  });

  const comparison = useQuery({
    queryKey: ['comparison', runId],
    queryFn: () => api.get<any>(`/reports/runs/${runId}/comparison`),
  });

  return (
    <div>
      <div className="page-header"><h1>Run Report</h1></div>

      {report.data && (
        <>
          <div className="stats-grid">
            <div className="stat-card">
              <div className="stat-value">{report.data.totalReplayPrs}</div>
              <div className="stat-label">Replay PRs</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{report.data.totalComments}</div>
              <div className="stat-label">Total Comments</div>
            </div>
          </div>

          <div className="card">
            <h3>Bot Statistics</h3>
            <table>
              <thead><tr><th>Bot</th><th>Comments</th><th>Graded</th><th>Verdicts</th></tr></thead>
              <tbody>
                {report.data.botStats?.map((bs: any) => (
                  <tr key={bs.botId}>
                    <td><strong>{bs.botName}</strong></td>
                    <td>{bs.totalComments}</td>
                    <td>{bs.gradedCount}</td>
                    <td>
                      {Object.entries(bs.verdicts || {}).map(([v, c]) => (
                        <span key={v} className={`badge badge-${v === 'VALID' ? 'completed' : v === 'INVALID' ? 'failed' : 'pending'}`}
                          style={{marginRight:4}}>{v}: {String(c)}</span>
                      ))}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {comparison.data && comparison.data.botComparisons?.length > 0 && (
        <div className="card">
          <h3>Golden Dataset Comparison</h3>
          <p style={{marginBottom:12,color:'var(--text-light)'}}>
            {comparison.data.goldenEntryCount} golden entries used for comparison
          </p>

          <div style={{height:300,marginBottom:20}}>
            <ResponsiveContainer>
              <BarChart data={comparison.data.botComparisons}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="botName" />
                <YAxis domain={[0, 1]} />
                <Tooltip />
                <Legend />
                <Bar dataKey="precision" fill="#3498db" />
                <Bar dataKey="recall" fill="#2ecc71" />
                <Bar dataKey="f1Score" fill="#e74c3c" name="F1" />
              </BarChart>
            </ResponsiveContainer>
          </div>

          <table>
            <thead><tr><th>Bot</th><th>TP</th><th>FP</th><th>FN</th><th>Precision</th><th>Recall</th><th>F1</th></tr></thead>
            <tbody>
              {comparison.data.botComparisons.map((bc: any) => (
                <tr key={bc.botId}>
                  <td><strong>{bc.botName}</strong></td>
                  <td>{bc.truePositives}</td>
                  <td>{bc.falsePositives}</td>
                  <td>{bc.falseNegatives}</td>
                  <td>{bc.precision}</td>
                  <td>{bc.recall}</td>
                  <td><strong>{bc.f1Score}</strong></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
