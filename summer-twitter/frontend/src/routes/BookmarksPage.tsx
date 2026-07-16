import { Link } from 'react-router-dom';
import { useBookmarkStore } from '@/stores/bookmarkStore';

export default function BookmarksPage() {
  const { bookmarks, remove } = useBookmarkStore();

  return (
    <div>
      <div className="border-twitter-border sticky top-0 border-b bg-white/80 px-4 py-3 backdrop-blur">
        <h1 className="text-xl font-bold">Bookmarks</h1>
      </div>

      {bookmarks.length === 0 ? (
        <div className="flex flex-col items-center justify-center px-4 py-16 text-center">
          <div className="bg-twitter-light-gray mb-4 flex h-16 w-16 items-center justify-center rounded-full">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth={1.5}
              className="text-twitter-gray h-8 w-8"
            >
              <path d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
            </svg>
          </div>
          <p className="text-twitter-dark text-lg font-bold">No bookmarks yet</p>
          <p className="text-twitter-gray mt-1">
            Save tweets to read later by tapping the bookmark icon.
          </p>
        </div>
      ) : (
        <div>
          {bookmarks.map((b) => (
            <div
              key={b.tweetId}
              className="border-twitter-border flex items-start gap-3 border-b px-4 py-3"
            >
              <Link to={`/tweet/${b.tweetId}`} className="min-w-0 flex-1">
                <div className="flex items-center gap-1 text-sm">
                  <span className="font-bold hover:underline">{b.authorName}</span>
                </div>
                <p className="mt-1 line-clamp-3 text-[15px] leading-5 break-words whitespace-pre-wrap">
                  {b.content}
                </p>
              </Link>
              <button
                onClick={() => remove(b.tweetId)}
                className="text-twitter-gray shrink-0 rounded-full p-2 transition-colors hover:bg-red-50 hover:text-red-500"
                title="Remove bookmark"
              >
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth={2}
                  className="h-4 w-4"
                >
                  <path d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
