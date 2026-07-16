import { useState } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { useNotificationStore } from '@/stores/notificationStore';
import ToastContainer from '@/components/Toast';

type NavIcon = ({ active }: { active: boolean }) => React.JSX.Element;

// IMPORTANT: keep this as a function so currentUsername is always fresh
function navItems(currentUsername: string | null) {
  const items: Array<{ to: string; label: string; icon: NavIcon }> = [
    { to: '/', label: 'Home', icon: HomeIcon },
    { to: '/explore', label: 'Explore', icon: ExploreIcon },
    { to: '/notifications', label: 'Notifications', icon: NotificationIcon },
    { to: '/bookmarks', label: 'Bookmarks', icon: BookmarksIcon },
    { to: '/dm', label: 'Messages', icon: DmIcon },
  ];
  if (currentUsername) {
    items.push({ to: `/${currentUsername}`, label: 'Profile', icon: ProfileIcon });
  }
  return items;
}

export default function Layout() {
  const { currentUsername, logout } = useAuthStore();
  const navigate = useNavigate();
  const [showDropdown, setShowDropdown] = useState(false);

  const handleLogout = () => {
    logout();
    setShowDropdown(false);
    navigate('/login');
  };

  return (
    <div className="border-twitter-border mx-auto flex min-h-screen max-w-[600px] border-x md:max-w-[1000px] md:flex-row lg:max-w-[1200px]">
      {/* Left sidebar */}
      <aside className="border-twitter-border sticky top-0 hidden h-screen flex-col items-end border-r p-2 md:flex md:w-[88px] lg:w-[280px]">
        <nav className="flex flex-col items-start gap-1 lg:w-full lg:px-4">
          <NavLink
            to="/"
            className="hover:bg-twitter-light-gray mb-2 flex h-12 w-12 items-center justify-center rounded-full p-2 transition-colors"
          >
            <Logo />
          </NavLink>

          {navItems(currentUsername).map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `hover:bg-twitter-light-gray flex items-center gap-4 rounded-full px-3 py-2 text-lg transition-colors ${
                  isActive ? 'font-bold' : ''
                }`
              }
            >
              <span className="relative">
                <item.icon active={false} />
                {item.to === '/notifications' && <UnreadBadge />}
              </span>
              <span className="hidden lg:inline">{item.label}</span>
            </NavLink>
          ))}

          {/* Post button */}
          <button
            onClick={() => navigate('/')}
            className="bg-twitter-blue hover:bg-twitter-blue-hover mt-2 hidden w-full rounded-full px-4 py-3 font-bold text-white transition-colors lg:block"
          >
            Post
          </button>
          <button
            onClick={() => navigate('/')}
            className="bg-twitter-blue hover:bg-twitter-blue-hover mt-2 flex h-12 w-12 items-center justify-center rounded-full text-white transition-colors lg:hidden"
          >
            <FeatherIcon />
          </button>
        </nav>

        {/* Current user with dropdown */}
        {currentUsername && (
          <div className="relative mt-auto mb-4 w-full">
            <button
              onClick={() => setShowDropdown(!showDropdown)}
              className="hover:bg-twitter-light-gray flex w-full items-center gap-3 rounded-full px-3 py-2 transition-colors lg:px-4"
            >
              <div className="bg-twitter-blue flex h-10 w-10 shrink-0 items-center justify-center rounded-full font-bold text-white">
                {currentUsername[0]?.toUpperCase()}
              </div>
              <div className="hidden min-w-0 flex-1 text-left lg:block">
                <div className="truncate text-sm font-bold">{currentUsername}</div>
                <div className="text-twitter-gray truncate text-sm">@{currentUsername}</div>
              </div>
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth={2}
                className="text-twitter-gray hidden h-4 w-4 shrink-0 lg:block"
              >
                <path d="M19 9l-7 7-7-7" />
              </svg>
            </button>

            {/* Dropdown */}
            {showDropdown && (
              <>
                <div className="fixed inset-0 z-40" onClick={() => setShowDropdown(false)} />
                <div className="absolute bottom-full left-0 z-50 mb-2 w-full rounded-xl bg-white p-2 shadow-[0_0_10px_rgba(0,0,0,0.2)] lg:left-4 lg:w-[250px]">
                  <div className="flex items-center gap-3 px-4 py-3">
                    <div className="bg-twitter-blue flex h-10 w-10 shrink-0 items-center justify-center rounded-full font-bold text-white">
                      {currentUsername[0]?.toUpperCase()}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-sm font-bold">{currentUsername}</div>
                      <div className="text-twitter-gray truncate text-sm">@{currentUsername}</div>
                    </div>
                  </div>
                  <hr className="border-twitter-border my-1" />
                  <button
                    onClick={handleLogout}
                    className="hover:bg-twitter-light-gray flex w-full items-center gap-3 rounded-lg px-4 py-3 text-sm font-medium transition-colors"
                  >
                    <svg
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth={2}
                      className="h-5 w-5"
                    >
                      <path d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                    </svg>
                    Log out @{currentUsername}
                  </button>
                </div>
              </>
            )}
          </div>
        )}
      </aside>

      {/* Mobile bottom nav */}
      <div className="border-twitter-border fixed right-0 bottom-0 left-0 z-30 flex border-t bg-white md:hidden">
        {navItems(currentUsername)
          .slice(0, 4)
          .map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `flex flex-1 flex-col items-center py-2 ${isActive ? 'text-twitter-blue' : 'text-twitter-gray'}`
              }
            >
              <span className="relative">
                <item.icon active={false} />
                {item.to === '/notifications' && <UnreadBadge />}
              </span>
            </NavLink>
          ))}
      </div>

      {/* Main content */}
      <main className="border-twitter-border flex-1 border-r pb-16 md:pb-0">
        <Outlet />
      </main>

      {/* Toast container */}
      <ToastContainer />
    </div>
  );
}

/* ── Unread badge ── */

function UnreadBadge() {
  const unreadCount = useNotificationStore((s) => s.unreadCount);
  if (unreadCount === 0) return null;
  return (
    <span className="absolute -top-1.5 -right-2 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] leading-none font-bold text-white">
      {unreadCount > 9 ? '9+' : unreadCount}
    </span>
  );
}

/* ── Icons ── */

function Logo() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className="text-twitter-blue h-8 w-8">
      <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z" />
    </svg>
  );
}

function HomeIcon({ active }: { active: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill={active ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={active ? 0 : 2}
      className="h-6 w-6"
    >
      <path d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
    </svg>
  );
}

function DmIcon({ active }: { active: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill={active ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={active ? 0 : 2}
      className="h-6 w-6"
    >
      <path d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
    </svg>
  );
}

function ExploreIcon({ active }: { active: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill={active ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={active ? 0 : 2}
      className="h-6 w-6"
    >
      <circle cx="11" cy="11" r="8" />
      <path d="M21 21l-4.35-4.35" />
    </svg>
  );
}

function ProfileIcon({ active }: { active: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill={active ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={active ? 0 : 2}
      className="h-6 w-6"
    >
      <path d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
    </svg>
  );
}

function NotificationIcon({ active }: { active: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill={active ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={active ? 0 : 2}
      className="h-6 w-6"
    >
      <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.73 21a2 2 0 01-3.46 0" />
    </svg>
  );
}

function BookmarksIcon({ active }: { active: boolean }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill={active ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={active ? 0 : 2}
      className="h-6 w-6"
    >
      <path d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
    </svg>
  );
}

function FeatherIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className="h-6 w-6">
      <path d="M23 3c-6.62-.1-10.38 2.421-13.05 6.03C7.29 12.61 6 17.331 6 22h2c0-1.007.07-2.012.19-3H12c4.1 0 7.48-3.082 7.94-7.054C22.79 10.147 23.17 6.359 23 3zm-7 8h-1.5v2H16c.63-.016 1.2-.08 1.72-.188C16.95 15.24 14.68 17 12 17H8.55c.57-2.512 1.57-4.851 3-6.78 2.16-2.912 5.29-4.911 9.45-5.22C20.1 6.5 19.38 9.82 16 11z" />
    </svg>
  );
}
