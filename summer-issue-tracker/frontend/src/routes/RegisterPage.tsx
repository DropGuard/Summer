import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useRegister } from '@/api/auth';

export default function RegisterPage() {
  const [form, setForm] = useState({
    username: '',
    displayName: '',
    email: '',
    password: '',
    orgName: '',
    orgSlug: '',
  });
  const register = useRegister();
  const navigate = useNavigate();

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50">
      <form
        className="bg-white p-8 rounded-lg shadow w-96 space-y-3"
        onSubmit={(e) => {
          e.preventDefault();
          register.mutate(form, {
            onSuccess: () => navigate('/'),
          });
        }}
      >
        <h1 className="text-xl font-bold text-slate-800">Create account</h1>
        <p className="text-xs text-slate-500">
          Registration creates a new organization; you become its admin.
        </p>
        <input
          className="w-full border border-slate-300 rounded px-3 py-2"
          placeholder="Username"
          value={form.username}
          onChange={(e) => setForm({ ...form, username: e.target.value })}
        />
        <input
          className="w-full border border-slate-300 rounded px-3 py-2"
          placeholder="Display name"
          value={form.displayName}
          onChange={(e) => setForm({ ...form, displayName: e.target.value })}
        />
        <input
          className="w-full border border-slate-300 rounded px-3 py-2"
          placeholder="Email"
          value={form.email}
          onChange={(e) => setForm({ ...form, email: e.target.value })}
        />
        <input
          className="w-full border border-slate-300 rounded px-3 py-2"
          type="password"
          placeholder="Password"
          value={form.password}
          onChange={(e) => setForm({ ...form, password: e.target.value })}
        />
        <input
          className="w-full border border-slate-300 rounded px-3 py-2"
          placeholder="Organization name"
          value={form.orgName}
          onChange={(e) => setForm({ ...form, orgName: e.target.value })}
        />
        <input
          className="w-full border border-slate-300 rounded px-3 py-2"
          placeholder="Organization slug"
          value={form.orgSlug}
          onChange={(e) => setForm({ ...form, orgSlug: e.target.value })}
        />
        <button
          className="w-full bg-slate-800 text-white rounded py-2 disabled:opacity-50"
          disabled={register.isPending}
        >
          {register.isPending ? 'Creating…' : 'Register'}
        </button>
        {register.isError && (
          <p className="text-red-600 text-sm">Registration failed. Try a different username.</p>
        )}
        <p className="text-sm text-slate-500">
          Have an account?{' '}
          <Link to="/login" className="text-blue-600">
            Sign in
          </Link>
        </p>
      </form>
    </div>
  );
}
