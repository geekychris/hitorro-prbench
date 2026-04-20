import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import Repositories from './pages/Repositories';
import Suites from './pages/Suites';
import SuiteDetail from './pages/SuiteDetail';
import Bots from './pages/Bots';
import Runs from './pages/Runs';
import RunDetail from './pages/RunDetail';
import ReplayPrDetail from './pages/ReplayPrDetail';
import GoldenDataset from './pages/GoldenDataset';
import GradingQueue from './pages/GradingQueue';
import RunReport from './pages/RunReport';
import TrendReport from './pages/TrendReport';
import IssueTypes from './pages/IssueTypes';
import RepoReport from './pages/RepoReport';
import Settings from './pages/Settings';

const queryClient = new QueryClient({
  defaultOptions: { queries: { refetchOnWindowFocus: false, retry: 1 } },
});

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <div className="app-layout">
          <nav className="sidebar">
            <h2>PR Bench</h2>
            <NavLink to="/">Dashboard</NavLink>
            <NavLink to="/repos">Repositories</NavLink>
            <NavLink to="/repo-report">Report & Docs</NavLink>
            <NavLink to="/suites">Suites</NavLink>
            <NavLink to="/bots">Bots</NavLink>
            <NavLink to="/runs">Runs</NavLink>
            <NavLink to="/golden-dataset">Golden Dataset</NavLink>
            <NavLink to="/grading-queue">Grading Queue</NavLink>
            <NavLink to="/trend">Trends</NavLink>
            <NavLink to="/issue-types">Issue Types</NavLink>
            <NavLink to="/settings">Settings</NavLink>
          </nav>
          <main className="main-content">
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/repos" element={<Repositories />} />
              <Route path="/repo-report" element={<RepoReport />} />
              <Route path="/suites" element={<Suites />} />
              <Route path="/suites/:id" element={<SuiteDetail />} />
              <Route path="/bots" element={<Bots />} />
              <Route path="/runs" element={<Runs />} />
              <Route path="/runs/:id" element={<RunDetail />} />
              <Route path="/replay-prs/:id" element={<ReplayPrDetail />} />
              <Route path="/golden-dataset" element={<GoldenDataset />} />
              <Route path="/grading-queue" element={<GradingQueue />} />
              <Route path="/reports/:runId" element={<RunReport />} />
              <Route path="/trend" element={<TrendReport />} />
              <Route path="/issue-types" element={<IssueTypes />} />
              <Route path="/settings" element={<Settings />} />
            </Routes>
          </main>
        </div>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

export default App;
