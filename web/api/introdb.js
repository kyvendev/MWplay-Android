// Same-origin relay for TheIntroDB reads: api.introdb.app only allows its own site in CORS, so
// the browser cannot call it directly. Only GET /segments with a valid IMDb id, season and
// episode is forwarded; anything else is rejected, so this is not an open proxy.
const IMDB_ID = /^tt\d{5,10}$/;
const NUMBER = /^\d{1,4}$/;

export async function GET(request) {
  const params = new URL(request.url).searchParams;
  const imdbId = params.get('imdb_id') ?? '';
  const season = params.get('season') ?? '';
  const episode = params.get('episode') ?? '';
  if (!IMDB_ID.test(imdbId) || !NUMBER.test(season) || !NUMBER.test(episode)) {
    return Response.json({ error: 'Parâmetros inválidos.' }, { status: 400 });
  }
  const upstream = `https://api.introdb.app/segments?imdb_id=${imdbId}&season=${season}&episode=${episode}`;
  try {
    const response = await fetch(upstream, {
      headers: { Accept: 'application/json', 'User-Agent': 'MW Play (web)' },
      signal: AbortSignal.timeout(6000),
    });
    return new Response(await response.text(), {
      status: response.status,
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        // Answers change rarely; let the CDN keep them for an hour.
        'Cache-Control': response.ok ? 'public, s-maxage=3600, stale-while-revalidate=86400' : 'no-store',
      },
    });
  } catch {
    return Response.json({ error: 'TheIntroDB indisponível.' }, { status: 502 });
  }
}
