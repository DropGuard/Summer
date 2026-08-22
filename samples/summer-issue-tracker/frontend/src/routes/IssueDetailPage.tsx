import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  useIssueDetail,
  useUpdateStatus,
  useChangePriority,
  useAssign,
  useDeleteIssue,
  useAddComment,
  useIssueTags,
  useAttachTag,
  useDetachTag,
} from '@/api/issues';
import { useProject, useProjectMembers } from '@/api/projects';
import { useOrgTags } from '@/api/tags';
import {
  STATUSES,
  PRIORITIES,
} from '@/lib/types';

export default function IssueDetailPage() {
  const { id } = useParams();
  const issueId = Number(id);
  const { data: detail, isLoading } = useIssueDetail(issueId);
  const { data: tags } = useIssueTags(issueId);
  const updateStatus = useUpdateStatus();
  const changePriority = useChangePriority();
  const assign = useAssign();
  const del = useDeleteIssue();
  const addComment = useAddComment();
  const attachTag = useAttachTag(issueId);
  const detachTag = useDetachTag(issueId);

  const [comment, setComment] = useState('');

  // Project + org context for member/tag pickers.
  const projectId = detail?.issue.projectId ?? 0;
  const { data: project } = useProject(projectId);
  const { data: members } = useProjectMembers(projectId);
  const { data: orgTags } = useOrgTags(project?.orgId ?? 0);

  if (isLoading) return <p className="text-slate-500">Loading…</p>;
  if (!detail) return <p className="text-red-600">Issue not found.</p>;

  const { issue, comments, history, assigneeName, reporterName } = detail;

  return (
    <div className="space-y-6">
      <div>
        <Link to={`/projects/${issue.projectId}`} className="text-sm text-blue-600">
          ← Board
        </Link>
        <div className="flex items-center gap-3 mt-1">
          <span className="font-mono text-slate-400">{issue.issueKey}</span>
          <h1 className="text-2xl font-bold text-slate-800">{issue.title}</h1>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main column */}
        <div className="lg:col-span-2 space-y-6">
          <div className="bg-white p-4 rounded-lg shadow">
            <p className="text-slate-600 whitespace-pre-wrap">
              {issue.description || <span className="text-slate-400">No description.</span>}
            </p>
          </div>

          <div className="bg-white p-4 rounded-lg shadow space-y-2">
            <h2 className="font-semibold text-slate-700">Comments ({comments.length})</h2>
            <div className="space-y-2">
              {comments.map((c) => (
                <div key={c.id} className="text-sm border-b border-slate-100 pb-2">
                  <div className="text-slate-400 text-xs">
                    {new Date(c.createdAt).toLocaleString()}
                  </div>
                  <div className="text-slate-700">{c.body}</div>
                </div>
              ))}
            </div>
            <div className="flex gap-2 pt-2">
              <input
                className="flex-1 border border-slate-300 rounded px-3 py-2"
                placeholder="Add a comment…"
                value={comment}
                onChange={(e) => setComment(e.target.value)}
              />
              <button
                className="bg-slate-800 text-white rounded px-4 py-2 disabled:opacity-50"
                disabled={!comment || addComment.isPending}
                onClick={() =>
                  addComment.mutate(
                    { id: issueId, body: comment },
                    { onSuccess: () => setComment('') },
                  )
                }
              >
                Send
              </button>
            </div>
          </div>
        </div>

        {/* Side column: metadata + actions + history */}
        <div className="space-y-6">
          <div className="bg-white p-4 rounded-lg shadow space-y-3">
            <h2 className="font-semibold text-slate-700">Details</h2>

            <div>
              <label className="text-xs text-slate-500">Status</label>
              <select
                className="w-full border border-slate-300 rounded px-2 py-1 mt-1"
                value={issue.status}
                onChange={(e) => updateStatus.mutate({ id: issueId, status: e.target.value })}
              >
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="text-xs text-slate-500">Priority</label>
              <select
                className="w-full border border-slate-300 rounded px-2 py-1 mt-1"
                value={issue.priority}
                onChange={(e) =>
                  changePriority.mutate({ id: issueId, priority: e.target.value })
                }
              >
                {PRIORITIES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="text-xs text-slate-500">Assignee</label>
              <select
                className="w-full border border-slate-300 rounded px-2 py-1 mt-1"
                value={issue.assigneeId ?? ''}
                onChange={(e) =>
                  assign.mutate({
                    id: issueId,
                    assigneeId: e.target.value ? Number(e.target.value) : null,
                  })
                }
              >
                <option value="">Unassigned</option>
                {members?.map((m) => (
                  <option key={m.userId} value={m.userId}>
                    User #{m.userId} ({m.role})
                  </option>
                ))}
              </select>
              {assigneeName && (
                <p className="text-xs text-slate-400 mt-1">Current: {assigneeName}</p>
              )}
            </div>

            <div className="text-xs text-slate-500">
              Reporter: {reporterName ?? `#${issue.reporterId}`}
            </div>

            <button
              className="w-full text-red-600 border border-red-200 rounded px-3 py-2 hover:bg-red-50"
              onClick={() => {
                if (confirm('Delete this issue?')) del.mutate(issueId);
              }}
            >
              Delete issue
            </button>
          </div>

          <div className="bg-white p-4 rounded-lg shadow space-y-2">
            <h2 className="font-semibold text-slate-700">Tags</h2>
            <div className="flex flex-wrap gap-2">
              {tags?.map((t) => (
                <span
                  key={t.id}
                  className="inline-flex items-center gap-1 text-xs px-2 py-1 rounded"
                  style={{ backgroundColor: t.color + '22', color: t.color }}
                >
                  {t.name}
                  <button
                    className="text-slate-400 hover:text-red-500"
                    onClick={() => detachTag.mutate(t.id)}
                  >
                    ×
                  </button>
                </span>
              ))}
              {tags?.length === 0 && <span className="text-xs text-slate-400">No tags.</span>}
            </div>
            <select
              className="w-full border border-slate-300 rounded px-2 py-1 text-sm"
              value=""
              onChange={(e) => {
                if (e.target.value) attachTag.mutate(Number(e.target.value));
              }}
            >
              <option value="">Attach tag…</option>
              {orgTags
                ?.filter((ot) => !tags?.some((t) => t.id === ot.id))
                .map((ot) => (
                  <option key={ot.id} value={ot.id}>
                    {ot.name}
                  </option>
                ))}
            </select>
          </div>

          <div className="bg-white p-4 rounded-lg shadow space-y-2">
            <h2 className="font-semibold text-slate-700">History</h2>
            <ul className="text-xs space-y-1">
              {history.map((h) => (
                <li key={h.id} className="text-slate-600">
                  <span className="font-medium">{h.action}</span>
                  {h.fromValue != null && (
                    <span className="text-slate-400">
                      {' '}
                      {h.fromValue} → {h.toValue}
                    </span>
                  )}
                  <span className="text-slate-400"> · {new Date(h.createdAt).toLocaleString()}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}
