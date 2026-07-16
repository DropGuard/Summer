import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="flex flex-col items-center justify-center px-4 py-16 text-center">
      <h1 className="text-twitter-gray text-6xl font-bold">404</h1>
      <p className="text-twitter-dark mt-4 text-xl">This page doesn't exist</p>
      <p className="text-twitter-gray mt-2">Try searching for something else or go home.</p>
      <div className="mt-8 flex gap-4">
        <Link
          to="/"
          className="bg-twitter-blue hover:bg-twitter-blue-hover rounded-full px-6 py-3 font-bold text-white transition-colors"
        >
          Go home
        </Link>
        <Link
          to="/explore"
          className="border-twitter-border rounded-full border px-6 py-3 font-bold transition-colors hover:bg-gray-50"
        >
          Explore
        </Link>
      </div>
    </div>
  );
}
