import { useToastStore } from '@/stores/toastStore';

export default function ToastContainer() {
  const { toasts, dismiss } = useToastStore();
  if (toasts.length === 0) return null;

  return (
    <div className="fixed bottom-24 left-1/2 z-50 flex -translate-x-1/2 flex-col gap-2 md:bottom-6">
      {toasts.map((t) => (
        <div
          key={t.id}
          onClick={() => dismiss(t.id)}
          className={`cursor-pointer rounded-lg px-4 py-3 text-sm font-medium text-white shadow-lg transition-all ${t.type === 'error' ? 'bg-red-500' : ''} ${t.type === 'success' ? 'bg-twitter-blue' : ''} ${t.type === 'info' ? 'bg-twitter-dark' : ''} `}
        >
          {t.message}
        </div>
      ))}
    </div>
  );
}
