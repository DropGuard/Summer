import { Component } from 'react';

interface Props {
  children: React.ReactNode;
}

interface State {
  error: Error | null;
}

export default class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  render() {
    if (this.state.error) {
      return (
        <div className="flex min-h-screen flex-col items-center justify-center px-4 text-center">
          <h1 className="text-4xl font-bold text-twitter-gray">Something went wrong</h1>
          <p className="mt-4 max-w-md text-twitter-gray">
            {this.state.error.message}
          </p>
          <button
            onClick={() => window.location.reload()}
            className="mt-8 rounded-full bg-twitter-blue px-6 py-3 font-bold text-white transition-colors hover:bg-twitter-blue-hover"
          >
            Reload page
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
