import { useCallback, useState } from 'react';

/** Recent searches, stored in this browser only (same rules as SearchHistoryPolicy in the apps). */
const STORAGE_KEY = 'mwplay.web.searchHistory';
export const MAX_SEARCH_HISTORY = 20;
const MIN_LENGTH = 2;

export function normalizeSearch(query: string): string {
  return query.trim().replace(/\s+/g, ' ');
}

/** Most recent first; repeating a search (any letter case) moves it to the top. */
export function addSearch(history: string[], query: string): string[] {
  const entry = normalizeSearch(query);
  if (entry.length < MIN_LENGTH) return history;
  return [entry, ...history.filter((item) => item.toLowerCase() !== entry.toLowerCase())].slice(0, MAX_SEARCH_HISTORY);
}

export function removeSearch(history: string[], query: string): string[] {
  return history.filter((item) => item !== query);
}

export function readSearchHistory(): string[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string').map(normalizeSearch).filter((item) => item.length >= MIN_LENGTH).slice(0, MAX_SEARCH_HISTORY) : [];
  } catch {
    return [];
  }
}

function writeSearchHistory(history: string[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(history));
  } catch {
    // Private mode: the history still works for this visit.
  }
}

export function useSearchHistory() {
  const [history, setHistory] = useState(readSearchHistory);
  const update = useCallback((next: (current: string[]) => string[]) => {
    setHistory((current) => {
      const value = next(current);
      writeSearchHistory(value);
      return value;
    });
  }, []);
  return {
    history,
    remember: useCallback((query: string) => update((current) => addSearch(current, query)), [update]),
    remove: useCallback((query: string) => update((current) => removeSearch(current, query)), [update]),
  };
}
