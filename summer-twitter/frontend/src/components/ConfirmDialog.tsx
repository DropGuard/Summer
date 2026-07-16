import { useEffect, useRef } from 'react';

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
  destructive?: boolean;
}

export default function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = 'Delete',
  cancelLabel = 'Cancel',
  onConfirm,
  onCancel,
  destructive = true,
}: ConfirmDialogProps) {
  const btnRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (open) btnRef.current?.focus();
  }, [open]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/40" onClick={onCancel} />
      {/* Dialog */}
      <div className="relative w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl">
        <h2 className="text-lg font-bold">{title}</h2>
        <p className="text-twitter-gray mt-2 text-sm">{message}</p>
        <div className="mt-6 flex flex-col gap-2">
          <button
            ref={btnRef}
            onClick={onConfirm}
            className={`w-full rounded-full py-3 font-bold text-white transition-colors ${
              destructive
                ? 'bg-red-500 hover:bg-red-600'
                : 'bg-twitter-blue hover:bg-twitter-blue-hover'
            }`}
          >
            {confirmLabel}
          </button>
          <button
            onClick={onCancel}
            className="border-twitter-border w-full rounded-full border py-3 font-bold transition-colors hover:bg-gray-50"
          >
            {cancelLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
