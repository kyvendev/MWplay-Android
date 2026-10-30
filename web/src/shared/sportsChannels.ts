/**
 * Sports channels for the "Esportes" section of the TV tab, same rules as the apps
 * (data/model/SportsChannels.kt). A channel is sports only through metadata, never because its
 * name contains a sports word:
 * 1. the explicit lists below (fixes for channels a source classifies wrongly);
 * 2. the category it was listed under: a sports catalog (BestCine's "Canais | Esportes") or the
 *    addon's genre filter (e.g. "Esportes");
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

/** TV catalogs that list only sports channels, by name. */
export const SPORTS_CATALOG_NAMES = new Set(['Canais | Esportes'].map(normalizeCategory));

/** Channel ids always treated as sports, whatever the source says. */
export const FORCED_SPORTS_IDS = new Set<string>();

/** Channel ids never treated as sports (a source listed them under sports by mistake). */
export const NOT_SPORTS_IDS = new Set<string>();

export function isSportsCategory(name: string | null | undefined): boolean {
  return typeof name === 'string' && SPORTS_CATEGORIES.has(normalizeCategory(name));
}

/** A sports catalog: listed by name, or "<group> | <sports category>" like "Canais | Esportes". */
export function isSportsCatalog(name: string | null | undefined): boolean {
  if (typeof name !== 'string') return false;
  if (SPORTS_CATALOG_NAMES.has(normalizeCategory(name))) return true;
  const parts = name.split('|');
  return parts.length > 1 && isSportsCategory(parts[parts.length - 1]);
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
 * Esportes opens the source's sports catalog ("Canais | Esportes"), otherwise its sports genre,
 * otherwise every channel filtered by metadata (`sportsByMetadata` = that view is open).
 * `allRequest` loads every channel when nothing better exists; `catalogs` are the TV catalogs.
 */
export function buildCategoryRow<R>(
  genres: CategoryOption<R>[],
  sportsByMetadata: boolean,
  label: (value: string) => string,
  allRequest?: R,
  catalogs: CategoryOption<R>[] = [],
): CategoryRow<R> {
  const sportsCatalogs = catalogs.filter((option) => isSportsCatalog(option.value));
  const inSportsCatalog = sportsCatalogs.some((option) => option.selected);
  const sportsGenres = genres.filter((option) => isSportsCategory(option.value));
  const selectedSportsGenre = sportsGenres.find((option) => option.selected);
  const sportsFromMetadata = sportsCatalogs.length === 0 && sportsGenres.length === 0;
  const sportsOpen = inSportsCatalog || selectedSportsGenre !== undefined || (sportsByMetadata && sportsFromMetadata);
  const allGenre = genres.find((option) => option.value === null);
  // Inside the sports catalog, "Todos os canais" goes back to the first other TV catalog.
  const all = inSportsCatalog ? catalogs.find((option) => !sportsCatalogs.includes(option))?.request : allGenre?.request ?? allRequest;
  const allSelected = !sportsOpen && (allGenre ? allGenre.selected : !genres.some((option) => option.selected));
  const chips: CategoryChip<R>[] = [];
  if (all !== undefined) chips.push({ label: 'Todos os canais', kind: 'all', request: all, selected: allSelected });
  const sportsRequest = sportsCatalogs[0]?.request ?? sportsGenres[0]?.request ?? all;
  if (sportsRequest !== undefined) chips.push({ label: 'Esportes', kind: 'sports', request: sportsRequest, selected: sportsOpen });
  // The other categories belong to the catalog on screen; the sports catalog has none.
  if (!inSportsCatalog) {
    for (const option of genres) {
      if (option.value !== null && !sportsGenres.includes(option)) chips.push({ label: label(option.value), kind: 'other', request: option.request, selected: option.selected && !sportsOpen });
    }
  }
  const sportsSubcategories =
    sportsCatalogs.length > 1
      ? sportsCatalogs.map((option) => ({ label: option.value ?? '', kind: 'sports' as const, request: option.request, selected: option.selected }))
      : sportsCatalogs.length === 0 && sportsGenres.length > 1
        ? sportsGenres.map((option) => ({ label: label(option.value!), kind: 'sports' as const, request: option.request, selected: option.selected }))
        : [];
  return { chips, sportsSubcategories, sportsOpen, listedUnderSports: inSportsCatalog || selectedSportsGenre !== undefined, sportsFromMetadata };
}
