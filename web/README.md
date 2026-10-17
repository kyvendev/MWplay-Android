# MW Play Web

MW Play para navegadores: celular, tablet, computador e navegadores de TV. Não é um produto
separado. É a mesma plataforma dos apps Android e Android TV, rodando o **mesmo `stremio-core`**,
agora compilado para WebAssembly.

## Como o Stremio é integrado

| | Android / Android TV | Web |
|---|---|---|
| Core | `stremio-core` (Rust) via JNI, submódulo `streamio-core-kotlin` | `stremio-core` (Rust) via WebAssembly: pacote oficial `@stremio/stremio-core-web@0.64.0` |
| Ponte | `Core.dispatch(action, field)` / `Core.getState(field)` | `dispatch(action, field)` / `get_state(field)` dentro de um Web Worker |
| Wrapper | `core/StremioCore.kt` | `src/services/stremio/core.ts` (mesmos métodos) |
| Persistência do core | `AndroidStorage` (SharedPreferences) | `localStorage` com prefixo `mwplay.core.` |
| Eventos | `RuntimeEvent.NewState` → flows | `NewState` → hook `useCoreState(field)` |
| Login | `Ctx/Authenticate (Login)` na API de contas do Stremio | igual |
| Catálogos, busca, detalhes, biblioteca | modelos `board`, `discover`, `search`, `meta_details`, `library`, `continue_watching_preview` | os mesmos modelos |
| Fontes de reprodução | `meta_details.streams` → `Player` → `player.stream` (URL convertida) | igual |
| Torrents | servidor local `libstream_server.so` em `127.0.0.1:11470` | só se houver servidor de streaming acessível (veja limitações) |

Nada foi reimplementado: catálogos, metadados, addons, biblioteca, progresso, identificação de
filmes, séries e episódios e a conversão de streams continuam sendo feitos pelo core. O código web
é a camada de interface mais os adaptadores do navegador:

- `services/stremio/coreWorker.ts`: o equivalente ao JNI. É o worker oficial reescrito como ES module
  para o bundler resolver o `.wasm`; fornece storage e hash da URL ao core.
- `services/stremio/bridge.ts`: o mesmo protocolo de mensagens do `bridge.js` oficial.
- `services/stremio/core.ts`: as ações do core com os formatos de `src/runtime/msg/action.rs`.
- `features/player/videoEngine.ts`: troca ExoPlayer/VLC/MPV por `<video>` + hls.js.

## Estrutura

```
web/
  index.html, vite.config.ts      shell, build e geração do service worker
  public/                          manifest.webmanifest, ícones (gerados do vetor oficial)
  scripts/generate-icons.mjs       rasteriza res/drawable/mw_play_mark.xml em PNG (Node puro)
  src/
    app/                           rotas (#/...), modo de interface, tecla voltar
    components/                    logo, pôsteres, fileiras, modal, estados, navegação
    features/
      auth/        login (Ctx Authenticate)
      home/        Início: board + continuar assistindo + destaque
      catalog/     Descobrir (filtros do core) e Detalhes (temporadas, episódios, fontes)
      search/      busca com debounce
      library/     biblioteca da conta
      player/      player web, progresso para o core, legendas, faixas de áudio, Media Session
      settings/    conta, modo de interface, legendas (perfil do core)
      tv/          navegação espacial por setas
    pwa/                           registro do SW, aviso de atualização, dica de instalação
    services/stremio/              integração com o core
  tests/                           Vitest (unidade e interface)
```

## Executar

```bash
cd web
npm install --ignore-scripts
npm run dev        # http://localhost:5173
npm test           # Vitest
npm run build      # tsc + build de produção em dist/
npm run preview    # serve dist/ em http://localhost:4173 (com service worker)
npm run icons      # regenera os ícones a partir do vetor oficial
```

Para publicar, sirva `web/dist/` em **HTTPS** (exigido para service worker, instalação e Media
Session), com fallback de qualquer rota para `index.html` (as rotas usam `#`, então basta servir
`/`). Nenhum backend é necessário: o core fala direto com a API do Stremio e com os addons, que
aceitam CORS.

## Instalar no iPhone (PWA)

1. Abra o endereço do MW Play no **Safari** (no iOS, a instalação só funciona pelo Safari).
2. Toque em **Compartilhar** (quadrado com seta).
3. Escolha **Adicionar à Tela de Início** e confirme.

O app abre em tela cheia, sem barra do navegador, com o ícone oficial. O iOS não mostra um
diálogo de instalação automático; o MW Play exibe essa orientação uma vez, de forma discreta.

## Acessar pela TV

- Abra o navegador da TV (Samsung Tizen, LG webOS, Android TV/Google TV com Chrome ou TV Bro,
  Fire TV com Silk) e digite o endereço do MW Play.
- TVs conhecidas são detectadas pelo user agent e abrem no **modo TV** (layout de tela grande,
  rail lateral, foco grande e navegação por setas). Para forçar: `endereço/?ui=tv`, ou em
  Configurações → Modo da interface → TV.
- Setas movem o foco, **OK/Enter** ativa, e **Voltar** (Escape, Backspace, tecla voltar do
  controle webOS 461 / Tizen 10009) fecha modais ou volta à tela anterior.
- No player, ←/→ na linha do tempo avançam ou recuam 10 s, e ↑/↓ só navegam (nunca fazem seek,
  a mesma regra do app de TV). A primeira tecla com os controles ocultos só os exibe.
- O layout é desenhado numa grade lógica de 960×540 e escala com a largura da tela, mantendo a
  mesma composição em 720p, 1080p e 4K.

### Diferenças entre navegadores de TV

| Navegador | Situação esperada |
|---|---|
| Chrome / Chromium recentes (Android TV, Google TV) | Suporte completo (WebAssembly, module workers, HLS via hls.js). |
| Samsung Tizen 2021+ / LG webOS 6+ | Devem funcionar (Chromium 79–94+). webOS 6 usa Chromium 79, anterior a *module workers* (Chrome 80): o core pode não iniciar e o app mostra a mensagem de navegador não suportado. |
| Tizen/webOS mais antigos | Sem garantia: sem module workers ou com WebAssembly limitado. |
| Fire TV (Silk) | Chromium recente; deve funcionar. |
| Teclas de mídia | Play/Pause/FF/RW funcionam quando o navegador repassa as teclas para a página; alguns modelos as interceptam. |

Nenhum desses aparelhos foi testado fisicamente; veja "Próximas etapas".

## Limitações conhecidas

- **Torrents:** a versão web não tem servidor de streaming (não há tela para ele nas
  Configurações); fontes torrent aparecem como "Disponível apenas no app MW Play". Tecnicamente
  elas só tocariam com um servidor de streaming do Stremio acessível pelo navegador
  (`http://127.0.0.1:11470/` por padrão, por exemplo o Stremio Service no computador). Em celular
  e TV pelo navegador isso normalmente não existe. As fontes torrent aparecem desativadas, com
  explicação, e as fontes HTTP vêm primeiro. O servidor embutido no APK (`libstream_server.so`) é
  nativo e não roda no navegador.
- **Formatos e codecs:** o navegador decide o que toca. MP4/H.264/AAC e HLS funcionam bem; MKV,
  HEVC/H.265, AC3/E-AC3/DTS e legendas embutidas em MKV costumam falhar fora do Safari e de alguns
  Chromium. **DASH** só toca se o navegador suportar nativamente (hls.js cobre só HLS).
- **CORS:** HLS via hls.js e legendas de addons exigem que o servidor da fonte permita acesso pelo
  navegador. MP4 direto não exige. Fontes que dependem de cabeçalhos HTTP próprios
  (`behaviorHints.proxyHeaders`) só funcionam via servidor de streaming.
- **Safari / iOS:** volume não é controlável por JavaScript (o controle fica oculto e vale o volume
  do aparelho); tela cheia usa o player nativo do iOS; HLS é nativo; não há aviso automático de
  instalação; o armazenamento de PWAs pode ser limpo pelo iOS após semanas sem uso (a sessão do
  core é refeita ao entrar de novo).
- **Rede local:** com o app servido em HTTPS por um domínio público, o acesso a
  `http://127.0.0.1:11470` pode exigir a permissão de acesso à rede local do Chrome ou ser
  bloqueado em outros navegadores (mixed content / Private Network Access).
- **Sessão:** como no Android, o core guarda a sessão da conta no armazenamento local do
  navegador (`localStorage`). A senha nunca é armazenada. O service worker nunca armazena
  respostas de conta, catálogo, addons, imagens ou streams; só o shell do app.
- **Analytics:** o core envia os mesmos eventos de analytics do Stremio que já envia no Android
  (o crate Kotlin é compilado com a feature `analytics`).
- **Não portado:** Chromecast, Picture-in-Picture e o controle de MediaSession do Android
  (no navegador há a Media Session API, já integrada), gerenciamento do servidor nativo e
  atualizações por APK.

## Testes

`npm test` roda 38 testes (Vitest + jsdom):

- **Lógica (sem mocks):** classificação de fontes, detecção HLS/DASH, deep links do Descobrir,
  SRT→WebVTT, gêneros/elenco/IMDb, rotas, regras de setas (sliders não mudam valor com ↑/↓),
  teclas voltar de TV, detecção de TV e persistência do modo de interface, storage do core,
  ponte do worker.
- **Ações do core (dublê de transporte):** formatos exatos enviados ao core para login, board,
  busca, biblioteca, detalhes (filme e episódio), player, progresso (`device: "web"`) e unload.
- **Interface (core falso + fixtures):** erro de inicialização, login obrigatório e erro de senha,
  estados do board (carregando, erro, pronto) sem carregamento duplicado, abertura de detalhes,
  fontes bloqueadas sem servidor e ordem das fontes, início da reprodução pelo modelo Player,
  busca com debounce, configurações salvas no perfil do core, modo de interface persistido.
- **Player (motor de vídeo como dublê):** um único motor por tela, retomada na posição salva,
  nenhuma recarga em atualizações de progresso, modelo do conteúdo anterior ignorado, liberação
  do motor e `Unload` do core ao sair, mensagens de erro.
- **PWA (arquivos reais):** manifesto, ícones existentes, metadados do Safari, regras do
  service worker.

As fixtures (`tests/fixtures/coreStates.ts`) são dados públicos do Cinemeta capturados do core
real; as fontes nelas são sintéticas. Nenhum teste automatizado acessa a rede.

**Validação manual com serviços reais** (navegador embutido, Chromium, em 2026-10-17):

- inicialização do core WebAssembly;
- catálogos reais do board (Cinemeta, YouTube, Public Domain Movies);
- busca;
- detalhes de filme e de série com episódios;
- fontes dos addons de uma conta;
- conversão de stream pelo modelo `Player` (HTTP e torrent);
- reprodução do stream de teste público Big Buck Bunny (Mux, CC-BY), com HLS nativo e com
  hls.js, retomada de posição, troca de fonte sem pipeline duplicado e liberação de recursos;
- navegação por setas no layout de TV, regras da timeline e voltar;
- build de produção com service worker ativo e cache só do shell.

Ainda não testado: iPhone e iPad (Safari), TVs físicas e a instalação real da PWA.

## Próximas etapas

1. Publicar `web/dist` em HTTPS (domínio próprio) e testar em iPhone (Safari e PWA instalada),
   Android (Chrome), Samsung Tizen, LG webOS e Fire TV.
2. Medir quais fontes HTTP dos addons usados pelo MW Play tocam em cada navegador (codecs e CORS).
3. Decidir a estratégia para torrents na web (servidor de streaming remoto autorizado ou apenas
   fontes HTTP).
4. Se necessário, avaliar dash.js para fontes DASH.

## Privacidade da interface

A interface web não lista, não identifica e não permite remover os addons da conta: as fontes e
os catálogos aparecem sem o nome de origem. Os addons continuam sendo usados pelo core para
catálogos, busca e fontes, e são gerenciados apenas pela conta, fora do MW Play Web.

## Sessão encerrada remotamente

Igual aos APKs (`MainActivity.isSessionRevoked`): o MW Play Web valida a sessão em
`POST https://api.strem.io/api/getUser`. Ele considera a sessão revogada só com HTTP 401/403 ou
HTTP 200 com `error.code == 1`, e então desloga pelo core (`Ctx/Logout`), voltando à tela de
login. Falhas de rede, timeout (5 s) e outros erros nunca deslogam. A checagem roda:

- ao abrir o app;
- quando a aba volta a ficar visível;
- quando a conexão volta;
- a cada 5 minutos com a aba visível (abas de navegador e de TV ficam abertas por horas).

Assim, encerrar a sessão pelo site do Stremio (por exemplo, "sair de todos os dispositivos")
desconecta o MW Play Web.
