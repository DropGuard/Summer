import { useState } from 'react';
import { useCreateTweet } from '@/api/tweets';
import { useAuthStore } from '@/stores/authStore';

export default function ComposeTweet() {
  const [content, setContent] = useState('');
  const createTweet = useCreateTweet();
  const currentUsername = useAuthStore((s) => s.currentUsername);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!content.trim() || createTweet.isPending) return;
    createTweet.mutate(
      { content: content.trim() },
      { onSuccess: () => setContent('') },
    );
  };

  const remaining = 280 - content.length;

  return (
    <div className="border-twitter-border border-b px-4 py-3">
      <div className="flex gap-3">
        {/* Avatar */}
        <div className="bg-twitter-blue flex h-10 w-10 shrink-0 items-center justify-center rounded-full font-bold text-white">
          {(currentUsername?.[0] ?? '?').toUpperCase()}
        </div>

        <form onSubmit={handleSubmit} className="flex-1">
          <textarea
            placeholder="What is happening?!"
            value={content}
            onChange={(e) => setContent(e.target.value)}
            maxLength={280}
            rows={3}
            className="placeholder:text-twitter-gray w-full resize-none text-lg outline-none"
          />

          <div className="border-twitter-border flex items-center justify-between border-t pt-3">
            <div className="text-twitter-gray text-sm">
              {content.length > 0 && (
                <span
                  className={
                    remaining < 0 ? 'text-red-500' : remaining < 20 ? 'text-yellow-500' : ''
                  }
                >
                  {remaining}
                </span>
              )}
            </div>

            <button
              type="submit"
              disabled={!content.trim() || createTweet.isPending}
              className="bg-twitter-blue hover:bg-twitter-blue-hover rounded-full px-5 py-2 font-bold text-white transition-colors disabled:opacity-50"
            >
              {createTweet.isPending ? 'Posting...' : 'Post'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
