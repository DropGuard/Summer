/** Maximum tweet content length */
export const TWEET_MAX_LENGTH = 280;

/** WebSocket reconnect: max attempts */
export const WS_MAX_RECONNECT = 5;

/** WebSocket reconnect: base delay in ms */
export const WS_RECONNECT_DELAY = 3000;

/** Default timeline page size */
export const TIMELINE_PAGE_SIZE = 20;

/** Default replies page size */
export const REPLIES_PAGE_SIZE = 20;

/** Max profile timeline fetch for local filtering */
export const PROFILE_TWEET_LIMIT = 50;

/** Build the correct WebSocket URL for a path */
export function wsUrl(path: string): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}${path}`;
}
