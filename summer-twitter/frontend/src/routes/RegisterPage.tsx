import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useRegister } from '@/api/auth';

export default function RegisterPage() {
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const navigate = useNavigate();
  const register = useRegister();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (register.isPending) return;
    register.mutate(
      { username, displayName, email, password },
      { onSuccess: () => navigate('/login', { replace: true }) },
    );
  };

  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="w-full max-w-sm px-6">
        <svg viewBox="0 0 24 24" fill="#1d9bf0" className="mx-auto mb-8 h-10 w-10">
          <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z" />
        </svg>

        <h1 className="mb-6 text-2xl font-bold">Create your account</h1>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <input
            type="text"
            placeholder="Username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="border-twitter-border focus:border-twitter-blue w-full rounded-md border px-3 py-3 text-lg outline-none"
            required
          />
          <input
            type="text"
            placeholder="Display name"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            className="border-twitter-border focus:border-twitter-blue w-full rounded-md border px-3 py-3 text-lg outline-none"
            required
          />
          <input
            type="email"
            placeholder="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="border-twitter-border focus:border-twitter-blue w-full rounded-md border px-3 py-3 text-lg outline-none"
            required
          />
          <input
            type="password"
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="border-twitter-border focus:border-twitter-blue w-full rounded-md border px-3 py-3 text-lg outline-none"
            required
          />

          {register.error && (
            <p className="text-sm text-red-500">
              Registration failed. Username or email may already be taken.
            </p>
          )}

          <button
            type="submit"
            disabled={register.isPending}
            className="bg-twitter-blue hover:bg-twitter-blue-hover w-full rounded-full py-3 font-bold text-white transition-colors disabled:opacity-50"
          >
            {register.isPending ? 'Creating account...' : 'Sign up'}
          </button>
        </form>

        <p className="text-twitter-gray mt-6 text-center">
          Already have an account?{' '}
          <Link to="/login" className="text-twitter-blue hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}
