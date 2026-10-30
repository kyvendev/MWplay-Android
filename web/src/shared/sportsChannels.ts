/**
 * Sports channels for the "Esportes" section of the TV tab, same rules as the apps
 * (data/model/SportsChannels.kt). A channel is sports only through metadata, never because its
 * name contains a sports word:
 * 1. the explicit lists below (fixes for channels a source classifies wrongly);
 * 2. the category it was listed under (the addon's genre filter, e.g. "Esportes");
 * 3. its own genres from the addon's metadata.
 * Category names are compared whole, accents and case ignored.
 */

export function normalizeCategory(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim()
    .replace(/\s+/g, ' ');
}

/** Category / genre names that mean sport (Portuguese and the English used by addons). */
export const SPORTS_CATEGORIES = new Set(
  [
    'Esportes', 'Esporte', 'Esportes em geral', 'Outros esportes', 'Futebol', 'Automobilismo', 'Basquete', 'Lutas', 'Tênis',
    'Sports', 'Sport', 'Football', 'Soccer', 'Motorsport', 'Basketball', 'Fighting', 'Tennis',
  ].map(normalizeCategory),
);

/** Channel ids always treated as sports, whatever the source says. */
export const FORCED_SPORTS_IDS = new Set<string>();

/** Channel ids never treated as sports (a source listed them under sports by mistake). */
export const NOT_SPORTS_IDS = new Set<string>();

export function isSportsCategory(name: string | null | undefined): boolean {
  return typeof name === 'string' && SPORTS_CATEGORIES.has(normalizeCategory(name));
}

export function isSportsChannel(
  id: string,
  genres: string[],
  listedUnderSports: boolean,
  forced: Set<string> = FORCED_SPORTS_IDS,
  excluded: Set<string> = NOT_SPORTS_IDS,
): boolean {
  if (excluded.has(id)) return false;
  if (forced.has(id)) return true;
  if (listedUnderSports) return true;
  return genres.some(isSportsCategory);
}

/** The sports channels of `items`, each once, in the source's order. */
export function sportsChannels<T>(
  items: T[],
  id: (item: T) => string,
  genres: (item: T) => string[],
  listedUnderSports: boolean,
  forced?: Set<string>,
  excluded?: Set<string>,
): T[] {
  const seen = new Set<string>();
  return items.filter((item) => {
    const key = id(item);
    if (seen.has(key) || !isSportsChannel(key, genres(item), listedUnderSports, forced, excluded)) return false;
    seen.add(key);
    return true;
  });
}

export interface CategoryOption<R> {
  value: string | null;
  selected: boolean;
  request: R;
}

export type CategoryKind = 'all' | 'sports' | 'other';

export interface CategoryChip<R> {
  label: string;
  kind: CategoryKind;
  request: R;
  selected: boolean;
}

export interface CategoryRow<R> {
  chips: CategoryChip<R>[];
  /** Sports categories of the source, shown under "Esportes" when it has more than one. */
  sportsSubcategories: CategoryChip<R>[];
  sportsOpen: boolean;
  /** The channels on screen come from a sports category of the source. */
  listedUnderSports: boolean;
  /** The source has no sports category: Esportes filters every channel by its metadata. */
  sportsFromMetadata: boolean;
}

/**
 * "Todos os canais", "Esportes" and the source's other categories, unchanged and in its order.
 * `sportsByMetadata`: Esportes is open on a source without a sports category. `allRequest`
 * loads every channel when the genre filter has no "all" option (or there is no genre filter).
 */
export function buildCategoryRow<R>(options: CategoryOption<R>[], sportsByMetadata: boolean, label: (value: string) => string, allRequest?: R): CategoryRow<R> {
  const allOption = options.find((option) => option.value === null);
  const all = allOption?.request ?? allRequest;
  const sports = options.filter((option) => isSportsCategory(option.value));
  const selectedSports = sports.find((option) => option.selected);
  const sportsOpen = selectedSports !== undefined || (sportsByMetadata && sports.length === 0);
  const allSelected = allOption ? allOption.selected : !options.some((option) => option.selected);
  const chips: CategoryChip<R>[] = [];
  if (all !== undefined) chips.push({ label: 'Todos os canais', kind: 'all', request: all, selected: allSelected && !sportsOpen });
  const sportsRequest = sports[0]?.request ?? all;
  if (sportsRequest !== undefined) chips.push({ label: 'Esportes', kind: 'sports', request: sportsRequest, selected: sportsOpen });
  for (const option of options) {
    if (option.value !== null && !sports.includes(option)) chips.push({ label: label(option.value), kind: 'other', request: option.request, selected: option.selected && !sportsOpen });
  }
  const sportsSubcategories = sports.length > 1 ? sports.map((option) => ({ label: label(option.value!), kind: 'sports' as const, request: option.request, selected: option.selected })) : [];
  return { chips, sportsSubcategories, sportsOpen, listedUnderSports: selectedSports !== undefined, sportsFromMetadata: sports.length === 0 };
}
