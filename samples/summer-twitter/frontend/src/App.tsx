import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import EventsProvider from '@/components/EventsProvider';
import ErrorBoundary from '@/components/ErrorBoundary';
import Layout from '@/components/Layout';
import LoginPage from '@/routes/LoginPage';
import RegisterPage from '@/routes/RegisterPage';
import TimelinePage from '@/routes/TimelinePage';
import TweetPage from '@/routes/TweetPage';
import ProfilePage from '@/routes/ProfilePage';
import DmPage from '@/routes/DmPage';
import ExplorePage from '@/routes/ExplorePage';
import NotificationsPage from '@/routes/NotificationsPage';
import BookmarksPage from '@/routes/BookmarksPage';
import NotFoundPage from '@/routes/NotFoundPage';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = useAuthStore((s) => s.token);
  if (!token) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <ErrorBoundary>
      <EventsProvider>
        <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route
          element={
            <ProtectedRoute>
              <Layout />
            </ProtectedRoute>
          }
        >
          <Route index element={<TimelinePage />} />
          <Route path="/tweet/:id" element={<TweetPage />} />
          <Route path="/:username" element={<ProfilePage />} />
          <Route path="/dm" element={<DmPage />} />
          <Route path="/explore" element={<ExplorePage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/bookmarks" element={<BookmarksPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
        </Routes>
      </EventsProvider>
    </ErrorBoundary>
  );
}
