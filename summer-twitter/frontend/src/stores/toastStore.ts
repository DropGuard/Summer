import { create } from 'zustand';

export interface Toast {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
}

interface ToastState {
  toasts: Toast[];
  show: (message: string, type?: Toast['type']) => void;
  dismiss: (id: string) => void;
}

let nextId = 0;

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],
  show: (message, type = 'info') => {
    const id = String(++nextId);
    set({ toasts: [...get().toasts, { id, message, type }] });
    setTimeout(() => {
      set({ toasts: get().toasts.filter((t) => t.id !== id) });
    }, 4000);
  },
  dismiss: (id) => {
    set({ toasts: get().toasts.filter((t) => t.id !== id) });
  },
}));
