const BASE_URL = '/api';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const resp = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  });
  if (!resp.ok) throw new Error(`HTTP ${resp.status}: ${resp.statusText}`);
  return resp.json();
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};

export interface Repo {
  id: number; name: string; githubUrl: string; owner: string; repoName: string;
  mirrorOrg: string; mirrorRepoName: string; defaultBranch: string;
  syncStatus: string; tags: string; notes: string;
  language: string; fork: boolean; isPrivate: boolean; stars: number;
  githubDescription: string; docsJson: string; docsScannedAt: string; createdAt: string;
}

export interface DocFile {
  path: string; url: string; name: string; category: string;
}

export interface Suite {
  id: number; name: string; description: string;
  exemplarRepo: Repo; createdAt: string;
}

export interface SuitePr {
  id: number; originalPrNumber: number; title: string; description: string;
  author: string; baseBranch: string; headBranch: string;
  baseCommitSha: string; headCommitSha: string;
  filesChanged: number; additions: number; deletions: number;
  mergedAt: string; createdAt: string;
}

export interface Bot {
  id: number; name: string; description: string;
  workflowFileName: string; workflowContent: string;
  waitStrategy: string; timeoutSeconds: number;
}

export interface BenchmarkRun {
  id: number; name: string; status: string; concurrency: number;
  goldenDatasetEnabled: boolean; suite: Suite; bots: Bot[];
  startedAt: string; completedAt: string; errorMessage: string; createdAt: string;
}

export interface ReplayPr {
  id: number; mirrorPrNumber: number; mirrorPrUrl: string;
  baseBranch: string; headBranch: string; status: string;
  errorMessage: string; suitePr: SuitePr; bot: Bot; createdAt: string;
}

export interface ReviewComment {
  id: number; source: string; commentType: string;
  filePath: string; lineNumber: number; body: string;
  bodyNormalized: string; bot: Bot; createdAt: string;
}

export interface GoldenEntry {
  id: number; filePath: string; lineNumber: number;
  issueType: string; description: string; canonicalBody: string;
  active: boolean; suitePr: SuitePr;
}

export interface Grading {
  id: number; commentId: number; commentTableType: string;
  graderType: string; verdict: string; severity: string;
  stars: number; flagged: boolean; notes: string;
}

export interface IssueType {
  id: number; code: string; name: string; description: string;
  category: string; severityHint: string; active: boolean;
}
