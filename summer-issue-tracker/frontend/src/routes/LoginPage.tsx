import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useLogin } from '@/api/auth';
import { useAuthStore } from '@/stores/authStore';

export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const login = useLogin();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const navigate = useNavigate();

  if (isAuthenticated()) {
    navigate('/', { replace: true });
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50">
      <form
        className="bg-white p-8 rounded-lg shadow w-96 space-y-4"
        onSubmit={(e) => {
          e.preventDefault();
          login.mutate(
            { username, password },
            { onSuccess: () => navigate('/') },
          );
        }}
      >
        <h1 className="text-xl font-bold text-slate-800">Sign in</h1>
        <input
          className="w-full border border-slate-300 rounded px-3 py-2"
          placeholder="Username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
        />
        <input
          className="w-full border border-slate-300 rounded px-3 py-2"
          type="password"
          placeholder="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <button
          className="w-full bg-slate-800 text-white rounded py-2 disabled:opacity-50"
          disabled={login.isPending}
        >
          {login.isPending ? 'Signing in…' : 'Sign in'}
        </button>
        {login.isError && (
          <p className="text-red-600 text-sm">Invalid username or password.</p>
        )}
        <p className="text-sm text-slate-500">
          No account?{' '}
          <Link to="/register" className="text-blue-600">
            Register
          </Link>
        </p>
      </form>
    </div>
  );
}
