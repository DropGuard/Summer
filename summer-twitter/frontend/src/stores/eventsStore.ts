import { create } from 'zustand';
import { useNotificationStore } from '@/stores/notificationStore';
import { WS_MAX_RECONNECT, WS_RECONNECT_DELAY, wsUrl } from '@/lib/constants';

interface EventsState {
  connected: boolean;
  connect: (token: string) => void;
  disconnect: () => void;
}

let ws: WebSocket | null = null;
let reconnectAttempts = 0;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

function scheduleReconnect(token: string) {
  if (reconnectAttempts >= WS_MAX_RECONNECT) return;
  const delay = WS_RECONNECT_DELAY * (reconnectAttempts + 1);
  reconnectTimer = setTimeout(() => {
    reconnectAttempts++;
    doConnect(token);
  }, delay);
}

function doConnect(token: string) {
  if (ws) {
    ws.onclose = null;
    ws.onerror = null;
    ws.close();
    ws = null;
  }

  const url = wsUrl('/ws/events');
  const socket = new WebSocket(url, [token]);
  ws = socket;

  socket.onopen = () => {
    useEventsStore.setState({ connected: true });
    reconnectAttempts = 0;
  };

  socket.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data) as Record<string, unknown>;
      const type = data.type as string;
      if (type === 'new_tweet') {
        useNotificationStore.getState().add({
          type: 'new_tweet',
          tweetId: data.tweetId as string,
          authorUsername: data.authorUsername as string,
        });
      } else if (type === 'liked') {
        useNotificationStore.getState().add({
          type: 'liked',
          tweetId: data.tweetId as string,
          byUsername: data.byUsername as string,
        });
      } else if (type === 'mentioned') {
        useNotificationStore.getState().add({
          type: 'mentioned',
          tweetId: data.tweetId as string,
          byUsername: data.byUsername as string,
        });
      } else if (type === 'new_follower') {
        useNotificationStore.getState().add({
          type: 'new_follower',
          username: data.username as string,
        });
      }
    } catch (e) {
      console.warn('[Events] Malformed WS message:', e);
    }
  };

  socket.onclose = () => {
    ws = null;
    useEventsStore.setState({ connected: false });
    scheduleReconnect(token);
  };

  socket.onerror = () => {
    socket.close();
  };
}

export const useEventsStore = create<EventsState>(() => ({
  connected: false,
  connect: (token: string) => {
    reconnectAttempts = 0;
    doConnect(token);
  },
  disconnect: () => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    if (ws) {
      ws.onclose = null;
      ws.onerror = null;
      ws.close();
      ws = null;
    }
    useEventsStore.setState({ connected: false });
  },
}));
