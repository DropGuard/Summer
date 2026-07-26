import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMyProjects, useCreateProject } from '@/api/projects';

export default function ProjectsPage() {
  const { data: projects, isLoading } = useMyProjects();
  const createProject = useCreateProject();
  const [key, setKey] = useState('');
  const [name, setName] = useState('');

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Your projects</h1>
        <p className="text-sm text-slate-500">Creating a project makes you its manager and lead.</p>
      </div>

      <div className="bg-white p-4 rounded-lg shadow space-y-2">
        <h2 className="font-semibold text-slate-700">New project</h2>
        <div className="flex gap-2">
          <input
            className="border border-slate-300 rounded px-3 py-2 w-32"
            placeholder="KEY"
            value={key}
            onChange={(e) => setKey(e.target.value.toUpperCase())}
          />
          <input
            className="border border-slate-300 rounded px-3 py-2 flex-1"
            placeholder="Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <button
            className="bg-slate-800 text-white rounded px-4 py-2 disabled:opacity-50"
            disabled={!key || !name || createProject.isPending}
            onClick={() =>
              createProject.mutate(
                { projectKey: key, name },
                { onSuccess: () => { setKey(''); setName(''); } },
              )
            }
          >
            Create
          </button>
        </div>
      </div>

      {isLoading ? (
        <p className="text-slate-500">Loading…</p>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {projects?.map((p) => (
            <Link
              key={p.id}
              to={`/projects/${p.id}`}
              className="block bg-white p-4 rounded-lg shadow hover:shadow-md transition"
            >
              <div className="text-xs font-mono text-slate-400">{p.projectKey}</div>
              <div className="font-semibold text-slate-800">{p.name}</div>
            </Link>
          ))}
          {projects?.length === 0 && (
            <p className="text-slate-500">No projects yet. Create one above.</p>
          )}
        </div>
      )}
    </div>
  );
}
