export function TweetSkeleton() {
  return (
    <div className="border-twitter-border border-b px-4 py-3">
      <div className="flex gap-3">
        <div className="h-10 w-10 shrink-0 animate-pulse rounded-full bg-gray-200" />
        <div className="flex-1 space-y-3">
          <div className="flex gap-2">
            <div className="h-4 w-24 animate-pulse rounded bg-gray-200" />
            <div className="h-4 w-16 animate-pulse rounded bg-gray-200" />
          </div>
          <div className="space-y-2">
            <div className="h-4 w-full animate-pulse rounded bg-gray-200" />
            <div className="h-4 w-3/4 animate-pulse rounded bg-gray-200" />
          </div>
          <div className="flex gap-12">
            <div className="h-4 w-8 animate-pulse rounded bg-gray-200" />
            <div className="h-4 w-8 animate-pulse rounded bg-gray-200" />
          </div>
        </div>
      </div>
    </div>
  );
}

export function TimelineSkeleton() {
  return (
    <>
      <TweetSkeleton />
      <TweetSkeleton />
      <TweetSkeleton />
      <TweetSkeleton />
    </>
  );
}
