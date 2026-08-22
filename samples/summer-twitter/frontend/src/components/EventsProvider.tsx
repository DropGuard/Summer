import { useEffect } from 'react';
import { useAuthStore } from '@/stores/authStore';
import { useEventsStore } from '@/stores/eventsStore';

export default function EventsProvider({ children }: { children: React.ReactNode }) {
  const token = useAuthStore((s) => s.token);
  const connect = useEventsStore((s) => s.connect);
  const disconnect = useEventsStore((s) => s.disconnect);

  useEffect(() => {
    if (!token) return;
    connect(token);
    return () => {
      disconnect();
    };
  }, [token, connect, disconnect]);

  return <>{children}</>;
}
