/**
 * Portuguese labels for what addons send in English (content types, catalog names and genres),
 * same table as ContentLabels.kt in the apps. Unknown values are shown as they come.
 */

const TYPES: Record<string, string> = {
  movie: 'Filmes',
  series: 'Séries',
  anime: 'Anime',
  channel: 'Canais',
  tv: 'TV',
  other: 'Outros',
  events: 'Eventos',
  documentary: 'Documentários',
};

export function typeName(type: string | null | undefined): string {
  const value = type?.trim().toLowerCase();
  if (!value) return 'Todos os tipos';
  return TYPES[value] ?? value.charAt(0).toUpperCase() + value.slice(1);
}

const CATALOGS: Record<string, string> = {
  popular: 'Populares',
  featured: 'Em destaque',
  new: 'Lançamentos',
  newest: 'Lançamentos',
  'new releases': 'Lançamentos',
  trending: 'Em alta',
  top: 'Mais vistos',
  'top rated': 'Mais bem avaliados',
  latest: 'Mais recentes',
  'last videos': 'Vídeos recentes',
  'recently added': 'Adicionados recentemente',
  calendar: 'Calendário',
  'calendar-videos': 'Calendário',
  year: 'Por ano',
  'by year': 'Por ano',
  library: 'Biblioteca',
  search: 'Busca',
  discover: 'Descobrir',
  channels: 'Canais',
  live: 'Ao vivo',
  'live tv': 'TV ao vivo',
  movies: 'Filmes',
  series: 'Séries',
  'popular movies': 'Filmes populares',
  'popular series': 'Séries populares',
  'featured movies': 'Filmes em destaque',
  'featured series': 'Séries em destaque',
};

export function catalogName(name: string | null | undefined): string {
  const value = name?.trim() ?? '';
  return CATALOGS[value.toLowerCase()] ?? value;
}

/** Home shelf title: "Popular" + movie becomes "Filmes populares"; others get the type after a dot. */
export function shelfTitle(name: string | null | undefined, type: string | null | undefined): string {
  const raw = name?.trim() ?? '';
  const typeLabel = type === 'movie' ? 'Filmes' : type === 'series' ? 'Séries' : null;
  if (typeLabel && raw.toLowerCase() === 'popular') return `${typeLabel} populares`;
  if (typeLabel && raw.toLowerCase() === 'featured') return `${typeLabel} em destaque`;
  return type ? `${catalogName(raw)} · ${typeName(type)}` : catalogName(raw);
}

const GENRES: Record<string, string> = {
  action: 'Ação',
  adventure: 'Aventura',
  animation: 'Animação',
  anime: 'Anime',
  biography: 'Biografia',
  business: 'Negócios',
  classic: 'Clássicos',
  comedy: 'Comédia',
  cooking: 'Culinária',
  crime: 'Crime',
  documentary: 'Documentário',
  drama: 'Drama',
  educational: 'Educativo',
  entertainment: 'Entretenimento',
  family: 'Família',
  fantasy: 'Fantasia',
  'film-noir': 'Noir',
  'game-show': 'Game show',
  general: 'Geral',
  history: 'História',
  horror: 'Terror',
  isekai: 'Isekai',
  kids: 'Infantil',
  lifestyle: 'Estilo de vida',
  'martial arts': 'Artes marciais',
  mecha: 'Mecha',
  music: 'Música',
  musical: 'Musical',
  mystery: 'Mistério',
  nature: 'Natureza',
  news: 'Notícias',
  psychological: 'Psicológico',
  reality: 'Reality show',
  'reality-tv': 'Reality show',
  religious: 'Religioso',
  romance: 'Romance',
  school: 'Escolar',
  'sci-fi': 'Ficção científica',
  'science fiction': 'Ficção científica',
  short: 'Curta-metragem',
  'slice of life': 'Cotidiano',
  sport: 'Esporte',
  sports: 'Esportes',
  supernatural: 'Sobrenatural',
  'talk-show': 'Talk show',
  thriller: 'Suspense',
  travel: 'Viagem',
  'tv movie': 'Filme para TV',
  war: 'Guerra',
  weather: 'Clima',
  western: 'Faroeste',
};

export function genreName(genre: string | null | undefined): string {
  const value = genre?.trim() ?? '';
  return GENRES[value.toLowerCase()] ?? value;
}

/** Value of a Discover extra filter (genre, year…); null means "all". */
export function extraOptionName(extraName: string, value: string | null): string {
  const isGenre = extraName.toLowerCase() === 'genre';
  if (value === null) return isGenre ? 'Todos os gêneros' : 'Todos';
  return isGenre ? genreName(value) : value;
}

// region TV tab --------------------------------------------------------------------------------

export const TV_TYPE = 'tv';
const PREFERRED_TV_PROVIDERS = [/best\s*-?\s*cine/i];

export function isTvType(type: string | null | undefined): boolean {
  return type?.toLowerCase() === TV_TYPE;
}

/**
 * The TV tab opens on a catalog whose name, addon URL or addon name matches a preferred provider
 * (Best Cine), otherwise the first one. Other addons' TV catalogs stay in the catalog filter.
 */
export function preferredTvCatalog<T>(catalogs: T[], name: (catalog: T) => string, transportUrl: (catalog: T) => string, addonName: (catalog: T) => string | null): T | null {
  const preferred = catalogs.find((catalog) =>
    [name(catalog), transportUrl(catalog), addonName(catalog) ?? ''].some((text) => PREFERRED_TV_PROVIDERS.some((pattern) => pattern.test(text))),
  );
  return preferred ?? catalogs[0] ?? null;
}
