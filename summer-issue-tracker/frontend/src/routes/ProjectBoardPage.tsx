import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useProject, useProjectMembers } from '@/api/projects';
import { useIssues, useCreateIssue } from '@/api/issues';
import {
  STATUSES,
  PRIORITIES,
  STATUS_COLORS,
  PRIORITY_COLORS,
  type IssueFilter,
} from '@/lib/types';

export default function ProjectBoardPage() {
  const { id } = useParams();
  const projectId = Number(id);
  const { data: project } = useProject(projectId);
  const { data: members } = useProjectMembers(projectId);
  const [filter, setFilter] = useState<IssueFilter>({});
  const [showCreate, setShowCreate] = useState(false);
  const [title, setTitle] = useState('');
  const [priority, setPriority] = useState('MEDIUM');
  const createIssue = useCreateIssue(projectId);

  const { data: issues, isLoading } = useIssues(projectId, filter);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/" className="text-sm text-blue-600">
            ← Projects
          </Link>
          <h1 className="text-2xl font-bold text-slate-800">
            <span className="font-mono text-slate-400">{project?.projectKey}</span>{' '}
            {project?.name}
          </h1>
        </div>
        <button
          className="bg-slate-800 text-white rounded px-4 py-2"
          onClick={() => setShowCreate((v) => !v)}
        >
          New issue
        </button>
      </div>

      {showCreate && (
        <div className="bg-white p-4 rounded-lg shadow space-y-2">
          <input
            className="w-full border border-slate-300 rounded px-3 py-2"
            placeholder="Issue title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
          <div className="flex gap-2">
            <select
              className="border border-slate-300 rounded px-3 py-2"
              value={priority}
              onChange={(e) => setPriority(e.target.value)}
            >
              {PRIORITIES.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
            <button
              className="bg-slate-800 text-white rounded px-4 py-2 disabled:opacity-50"
              disabled={!title || createIssue.isPending}
              onClick={() =>
                createIssue.mutate(
                  {
                    title,
                    description: '',
                    status: 'OPEN',
                    priority,
                    assigneeId: null,
                  },
                  { onSuccess: () => { setTitle(''); setShowCreate(false); } },
                )
              }
            >
              Create
            </button>
          </div>
        </div>
      )}

      {/* Dynamic filter bar — exercises the backend's QueryBuilder path */}
      <div className="bg-white p-4 rounded-lg shadow flex flex-wrap gap-2 items-center">
        <span className="text-sm text-slate-500">Filter:</span>
        <select
          className="border border-slate-300 rounded px-2 py-1 text-sm"
          value={filter.status ?? ''}
          onChange={(e) =>
            setFilter((f) => ({ ...f, status: e.target.value || undefined }))
          }
        >
          <option value="">Any status</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
        <select
          className="border border-slate-300 rounded px-2 py-1 text-sm"
          value={filter.priority ?? ''}
          onChange={(e) =>
            setFilter((f) => ({ ...f, priority: e.target.value || undefined }))
          }
        >
          <option value="">Any priority</option>
          {PRIORITIES.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
        <input
          className="border border-slate-300 rounded px-2 py-1 text-sm flex-1 min-w-[160px]"
          placeholder="Title contains…"
          value={filter.title ?? ''}
          onChange={(e) => setFilter((f) => ({ ...f, title: e.target.value || undefined }))}
        />
        <button
          className="text-sm text-slate-500 hover:text-slate-800"
          onClick={() => setFilter({})}
        >
          Clear
        </button>
      </div>

      {isLoading ? (
        <p className="text-slate-500">Loading…</p>
      ) : (
        <div className="bg-white rounded-lg shadow divide-y">
          {issues?.map((issue) => (
            <Link
              key={issue.id}
              to={`/issues/${issue.id}`}
              className="block px-4 py-3 hover:bg-slate-50"
            >
              <div className="flex items-center gap-3">
                <span className="font-mono text-xs text-slate-400">{issue.issueKey}</span>
                <span className="flex-1 text-slate-800">{issue.title}</span>
                <span className={`text-xs font-medium ${PRIORITY_COLORS[issue.priority]}`}>
                  {issue.priority}
                </span>
                <span
                  className={`text-xs px-2 py-0.5 rounded ${STATUS_COLORS[issue.status]}`}
                >
                  {issue.status}
                </span>
              </div>
            </Link>
          ))}
          {issues?.length === 0 && (
            <p className="px-4 py-6 text-slate-500">No issues match the filter.</p>
          )}
        </div>
      )}

      {members && members.length > 0 && (
        <div className="text-sm text-slate-500">
          {members.length} member(s) on this project.
        </div>
      )}
    </div>
  );
}
