import { useState, useEffect, useRef, useCallback } from 'react';
import { useAuthStore } from '@/stores/authStore';
import { WS_MAX_RECONNECT, WS_RECONNECT_DELAY } from '@/lib/constants';

interface DmMessage {
  type: 'receive';
  messageId: string;
  from: string;
  text: string;
  timestamp: string;
}

interface SendMessage {
  type: 'send';
  to: string;
  text: string;
}

interface Conversation {
  username: string;
  messages: Array<{ text: string; from: string; timestamp: string }>;
}

const MAX_RECONNECT_ATTEMPTS = WS_MAX_RECONNECT;
const RECONNECT_DELAY_MS = WS_RECONNECT_DELAY;

export default function DmPage() {
  const { token, currentUsername } = useAuthStore();
  const [conversations, setConversations] = useState<Record<string, Conversation>>({});
  const [activeUser, setActiveUser] = useState('');
  const [input, setInput] = useState('');
  const [recipient, setRecipient] = useState('');
  const [connected, setConnected] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const reconnectRef = useRef(0);
  const timerRef = useRef<ReturnType<typeof setTimeout>>();
  const [reconnectTick, setReconnectTick] = useState(0);

  useEffect(() => {
    if (!token) return;

    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${wsProtocol}//${window.location.host}/ws/dm`;
    const ws = new WebSocket(wsUrl, [token]);
    wsRef.current = ws;

    ws.onopen = () => {
      setConnected(true);
      reconnectRef.current = 0;
    };

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data) as DmMessage & { type: string };
        if (msg.type === 'receive') {
          const dm = msg as DmMessage;
          setConversations((prev) => {
            const key = dm.from;
            const existing = prev[key];
            const messages = existing
              ? [...existing.messages, { text: dm.text, from: dm.from, timestamp: dm.timestamp }]
              : [{ text: dm.text, from: dm.from, timestamp: dm.timestamp }];
            return { ...prev, [key]: { username: dm.from, messages } };
          });
        }
    } catch (e) {
      console.warn('[DM] Malformed WS message:', e);
    }
    };

    ws.onclose = () => {
      wsRef.current = null;
      setConnected(false);
      reconnectRef.current++;
      if (reconnectRef.current < MAX_RECONNECT_ATTEMPTS) {
        timerRef.current = setTimeout(() => {
          setReconnectTick((k) => k + 1);
        }, RECONNECT_DELAY_MS * reconnectRef.current);
      }
    };

    ws.onerror = () => {
      ws.close();
    };

    return () => {
      clearTimeout(timerRef.current);
      ws.close();
    };
  }, [token, reconnectTick]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [conversations, activeUser]);

  const activeConv = activeUser ? conversations[activeUser] : undefined;
  const displayMessages = activeConv?.messages ?? [];

  const sendMessage = useCallback(() => {
    if (!input.trim() || !activeUser || !wsRef.current) return;
    const msg: SendMessage = { type: 'send', to: activeUser, text: input.trim() };
    wsRef.current.send(JSON.stringify(msg));

    // Optimistically add to local state
    setConversations((prev) => {
      const existing = prev[activeUser];
      const messages = existing
        ? [...existing.messages, { text: input.trim(), from: currentUsername ?? '', timestamp: new Date().toISOString() }]
        : [{ text: input.trim(), from: currentUsername ?? '', timestamp: new Date().toISOString() }];
      return { ...prev, [activeUser]: { username: activeUser, messages } };
    });
    setInput('');
  }, [input, activeUser, currentUsername]);

  const startConversation = () => {
    if (!recipient.trim()) return;
    setActiveUser(recipient.trim());
    setRecipient('');
  };

  return (
    <div className="flex h-[calc(100vh-56px)] flex-col">
      {/* Header */}
      <div className="border-twitter-border sticky top-0 flex items-center gap-3 border-b bg-white/80 px-4 py-3 backdrop-blur">
        <h1 className="text-xl font-bold">Messages</h1>
        <div
          className={`h-2 w-2 rounded-full ${connected ? 'bg-green-500' : 'bg-red-500'}`}
          title={connected ? 'Connected' : 'Disconnected'}
        />
      </div>

      <div className="flex flex-1">
        {/* Conversation list */}
        <div className="border-twitter-border w-64 border-r p-2">
          {/* New conversation */}
          <div className="mb-3 flex gap-2">
            <input
              type="text"
              placeholder="Username..."
              value={recipient}
              onChange={(e) => setRecipient(e.target.value)}
              className="border-twitter-border focus:border-twitter-blue flex-1 rounded-md border px-2 py-1 text-sm outline-none"
            />
            <button
              onClick={startConversation}
              className="bg-twitter-blue rounded-full px-3 py-1 text-sm font-bold text-white"
            >
              Go
            </button>
          </div>

          {/* Conversations */}
          {Object.keys(conversations).length === 0 ? (
            <p className="text-twitter-gray p-4 text-center text-sm">No conversations yet</p>
          ) : (
            Object.keys(conversations).map((username) => (
              <button
                key={username}
                onClick={() => setActiveUser(username)}
                className={`hover:bg-twitter-light-gray w-full rounded-lg px-3 py-2 text-left text-sm transition-colors ${
                  activeUser === username ? 'bg-twitter-light-gray font-bold' : ''
                }`}
              >
                @{username}
              </button>
            ))
          )}
        </div>

        {/* Chat area */}
        <div className="flex flex-1 flex-col">
          {!activeUser ? (
            <div className="text-twitter-gray flex flex-1 items-center justify-center">
              <p>Select a conversation</p>
            </div>
          ) : (
            <>
              {/* Chat header */}
              <div className="border-twitter-border border-b px-4 py-3">
                <span className="font-bold">@{activeUser}</span>
              </div>

              {/* Messages */}
              <div className="flex-1 overflow-y-auto p-4">
                {displayMessages.length === 0 ? (
                  <p className="text-twitter-gray text-center">No messages yet</p>
                ) : (
                  <div className="flex flex-col gap-3">
                    {displayMessages.map((msg, i) => {
                      const isMe = msg.from === currentUsername;
                      return (
                        <div
                          key={i}
                          className={`max-w-[70%] rounded-2xl px-4 py-2 text-sm ${
                            isMe
                              ? 'bg-twitter-blue self-end text-white'
                              : 'bg-twitter-light-gray self-start'
                          }`}
                        >
                          <p>{msg.text}</p>
                        </div>
                      );
                    })}
                    <div ref={messagesEndRef} />
                  </div>
                )}
              </div>

              {/* Input */}
              <div className="border-twitter-border border-t p-4">
                <div className="flex gap-3">
                  <input
                    type="text"
                    placeholder="Send a message"
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && sendMessage()}
                    className="border-twitter-border focus:border-twitter-blue flex-1 rounded-full border px-4 py-2 text-sm outline-none"
                  />
                  <button
                    onClick={sendMessage}
                    disabled={!input.trim()}
                    className="bg-twitter-blue rounded-full px-4 py-2 font-bold text-white disabled:opacity-50"
                  >
                    Send
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
