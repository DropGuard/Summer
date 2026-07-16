export interface Tweet {
  id: number;
  authorId: number;
  content: string;
  type: string;
  parentId: number | null;
  likeCount: number;
  replyCount: number;
  retweetCount: number;
  createdAt: string;
  // Client-enriched fields (populated by useTimeline / useTweet hooks)
  authorUsername?: string;
  authorDisplayName?: string;
}

export interface User {
  id: number;
  username: string;
  displayName: string;
  bio?: string;
  followerCount: number;
  followingCount: number;
  createdAt: string;
}

export interface TokenResponse {
  token: string;
}
