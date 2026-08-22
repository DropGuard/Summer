import { Outlet, Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';

export default function Layout() {
  const username = useAuthStore((s) => s.currentUsername);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();

  return (
    <div className="min-h-screen flex flex-col">
      <header className="bg-white border-b border-slate-200 px-6 py-3 flex items-center justify-between">
        <Link to="/" className="font-bold text-lg text-slate-800">
          Summer Issue Tracker
        </Link>
        <div className="flex items-center gap-4 text-sm">
          <span className="text-slate-600">{username}</span>
          <button
            className="text-slate-500 hover:text-slate-800"
            onClick={() => {
              logout();
              navigate('/login');
            }}
          >
            Sign out
          </button>
        </div>
      </header>
      <main className="flex-1 max-w-5xl w-full mx-auto p-6">
        <Outlet />
      </main>
    </div>
  );
}
