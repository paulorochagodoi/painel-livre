# Interface Aura — tela central

Atualizado: 2026-10-23. Branch `feat/interface-aura` (não lançada). Desenho da designer em
`docs/design/aura-launcher/` (HTML/CSS/JS de referência; **não** é usado pelo app). O app implementa o
mesmo padrão em Android nativo (Views + Kotlin), sem WebView, e só na **tela central** (tela inicial,
busca, apps, discador e previsão). O painel de instrumentos (`ClusterActivity`) não mudou.

## Regras que esta interface segue

- **Só leitura do carro.** Clima, bancos, vidros, porta-malas, trava e bateria aparecem como o carro
  informa (telemetria #16 e SDK BYD). Os controles do Aura que mandariam comando ao carro
  (temperatura −/+, liga/desliga do ar, ventilação, modos, bancos, "Fechar" vidros/porta-malas,
  trancar, EV/HEV, manter carga, limite, agendamento, V2L) **não existem** aqui: viram indicador ou
  saem. Nenhum código novo fala com `autoservice`; o `TelemetryTest` que recusa a transação 6 segue
  passando.
- **Nada inventado.** Sem endereços, rádios ou músicas de exemplo. Sem dado, o chip some ou diz
  "Sem dado"; nunca "Fechado"/"Travado" chutado. Sem mídia: "Nada tocando". Casa/Trabalho sem
  endereço: "Definir". Categorias da busca fazem uma busca de verdade (Photon) pelo termo.
- **Estado nunca só pela cor**: chip = ícone + peça + estado em palavras; aberto em `warn`.
- **Uma janela por vez**, ancorada no atalho, fecha no X, tocando fora, no mesmo atalho, em Início
  ou no voltar. Entrada de 180 ms (fade + 10 dp); com "remover animações" do Android, aparece direto.

## Escala e medidas

O Aura é desenhado em 1280 × 720. Em vez de `scale()` (que estragaria o mapa e o teclado), a
`LauncherActivity` usa densidade própria (`AuraScale.densityDpi`): 1 dp do layout = 1 px do desenho.
Ex.: 1920 × 1080 → 240 dpi; com a barra do sistema (1920 × 1024) a altura manda (228 dpi) e sobra
largura, que os atalhos e o dock absorvem. Em pé a referência é 720 × 1280 (`layout-port/`). O
manifesto não mudou (o tema Aura entra por `setTheme` no código).

## Mapeamento Aura → Android

| Aura (docs/design) | Android | Observação |
|---|---|---|
| `tokens.json` (cores claro/escuro) | `ui/aura/AuraTokens.kt` (`AuraColors.LIGHT/DARK`) | Preferência `theme` de sempre: auto/day/night. `AuraTest` confere todas as cores com o `tokens.json` |
| espaços, raios, toque, alturas | `AuraDims` + dp nos layouts `aura_*`, `tile_*`, `pop_*`, `dock_*` | 56 dp mínimo; 80 dp no play e no "Mostrar no painel" |
| Sora / Albert Sans | `res/font/*.ttf` (OFL, `assets/licencas/`) e estilos `Au*` em `res/values/aura.xml` | Offline, sem Google Fonts |
| Icons (linha, 24, traço 1,8) | `res/drawable/au_*.xml` (vetores gerados dos caminhos do `aura.js`) | Play/pausa/anterior/próxima preenchidos |
| cores por classe CSS | `AuraPainter` por `android:tag="a_…"` (tabela abaixo) | Troca de tema sem recriar a tela |
| Backdrop (horizonte + halo) | `AuraBackdropDrawable` | Também no discador e na previsão |
| StatusBar | `aura_status.xml` | Wi-Fi, Bluetooth, GPS (apagados quando desligados) + estado do painel · clima e bateria lidos · botão Claro/Escuro. Sem "4G": não há dado do celular |
| Herói: relógio/data/tempo | `aura_clock.xml` | Saudação + data; tempo do Open-Meteo e temperatura interna lida; toque abre a previsão |
| Herói: modelo/trava/resumo | `aura_summary.xml` | Modelo pela identificação da central (`PanelSize.matchModel`); trava e resumo só com leitura; toque abre o Diagnóstico |
| VehicleStatus (carro de cima 2D) | **carro 3D atual** `SongCarView` + `CarOverlay` em `aura_car.xml`, chips em `AnchoredLayout` | 3/4 padrão; vista de cima quando há destaque; bocal na traseira direita (.84, .15). Chips: Portas, Vidros, Capô, Porta-malas, Carregamento e Pneus |
| ShortcutTiles | `aura_tiles.xml` + `tile_nav/battery/climate/media.xml` | Ordem fixa; tile aberto com borda `accent` |
| NavPanel | `pop_nav.xml` | Rota ativa (Encerrar na nativa, Abrir no Maps/Waze), campo, Casa/Trabalho (km em linha reta), categorias |
| NavSearch | `aura_nav.xml` (+ `aura_nav_side/map`) | Busca Photon, Painel Livre/Maps/Waze, rota atual, lugares, mapa MapLibre com a rota nativa e Centralizar |
| BatteryPanel | `pop_battery.xml` | Anel %, autonomia total, elétrica/combustível, carga, cabo, tempo, combustível no tanque, potência, 12 V; "Ver todos os dados" |
| ClimatePanel | `pop_climate.xml` | Temperatura de cada lado, ventilação, interna/externa, modos (só com leitura 0/1), bancos aquecer/ventilar com nível |
| MediaPanel | `pop_media.xml` | Sessão de mídia real; anterior/tocar/próxima; volume da mídia do Android; Abrir app |
| (novo) janela do painel | `pop_panel.xml`, aberta pelo botão Painel do dock | Estado da projeção, motivo da falha, Mostrar/Voltar ao original, Ajustes do painel |
| Dock | `aura_dock.xml` + `dock_buttons.xml` + `dock_temp_*.xml` | Início · motorista (lida) · Painel, app de navegação, Clima, Telefone, Apps, Ajustes, Mídia · passageiro (lida). Em pé, sem as temperaturas |
| Popover (`.au-pop` + seta) | `PopoverFrame` (desenho, sombra, seta, altura máx. 420) | Posição pela regra pura `PopoverAnchor` |
| `state.ui.open` | `PopoverState` | Uma janela por vez |
| `Aura.fmt.dec` / textos | `AuraFormat` | `22,0°`, `6,4 km`, `1h40`, `62%` |

### Tags do `AuraPainter`

| Tag | Efeito |
|---|---|
| `a_tile`, `a_tile_on` | atalho (`surface`, borda `line`; aberto com borda `accent`); sombra só no claro |
| `a_chip`, `a_chip_click`, `a_chip_warn`, `a_chip_warn_click` | chip de estado neutro / aberto |
| `a_ic`, `a_ic_warn`, `a_ic_acc` | círculo do ícone do chip (neutro / aberto / carregando) |
| `a_sunken`, `a_sunken_click`, `a_sunken_sm`, `a_sunken_pill`, `a_sunken_round` | controles internos (`surface-sunken`) |
| `a_raised_sm`, `a_raised_md`, `a_raised_round` | sobre janela/mapa (`surface-raised`) |
| `a_primary_pill`, `a_primary_round` | ação principal (`accent` + `on-accent`) |
| `a_badge`, `a_badge_on`, `a_soft`, `a_soft_pill` | selos `accent-soft` / `accent` |
| `a_seg_on`, `a_seg_off` | segmentado |
| `a_mode_on`, `a_mode_off`, `a_heat_on`, `a_cool_on`, `a_seat_off` | indicadores do clima e dos bancos |
| `a_field`, `a_side`, `a_dock`, `a_db`, `a_db_on`, `a_plain_click`, `a_divider`, `a_banner`, `a_bar` | campo, lateral da busca, dock e botões, divisória, aviso, barra |
| `a_ink`, `a_muted`, `a_accent`, `a_warn`, `a_ok`, `a_on_accent` | texto |
| `a_icon`, `a_icon_muted`, `a_icon_accent`, `a_icon_ok`, `a_icon_warn`, `a_icon_heat`, `a_icon_cool`, `a_icon_on_accent` | tint de ícone |

Views desenhadas à mão (`PopoverFrame`, `RingView`, `LevelBars`, `EqualizerView`) recebem as cores por
`AuraThemed`. As telas antigas que usam `Palette` (discador, previsão, itens) ganham as cores Aura por
`AuraColors.toPalette()`; o painel continua com `Palette.forSetting`.

## Inventário de funções (antes → agora) — conferido em 2026-10-23

Todas as funções da tela central do cockpit anterior continuam; nada foi removido. "Emulador" = visto
no `emulator-5580` (prints em `/tmp/pl-aura-prints/`, cópias em `docs/telas/aura-*.png`).

| # | Função (cockpit anterior) | Onde ficou | Conferido |
|---|---|---|---|
| 1 | Saudação (Bom dia/Boa tarde/Boa noite) | herói, antes da data | emulador |
| 2 | Estado do painel na barra de cima | barra de status, "Painel: …" | emulador |
| 3 | Relógio e data | herói (Sora 72) | emulador |
| 4 | Wi-Fi / Bluetooth / GPS apagados quando desligados | barra de status (e o leitor de tela diz "desligado") | emulador |
| 5 | Temperatura do tempo e cartão do tempo; toque abre a previsão (#4) | linha do tempo no herói | código (emulador sem GPS → linha some, como antes) |
| 6 | Carro 3D (3/4, vista de cima com destaques, portas, vidros, teto, capô, porta-malas, bocal .84/.15, pressões, cadeado, pulso de carga) | centro do herói, mesmo `SongCarView`/`CarOverlay` | emulador (simulação "Porta e carga") |
| 7 | Foto do carro (Ajustes) no lugar da ilustração | igual | código |
| 8 | Autonomia total, elétrica/combustão e barra | atalho Bateria + janela | emulador |
| 9 | Cartão "Carro" (2D, título, clima/bancos/pneus/vidros); toque → Diagnóstico | chips + resumo/trava + janela Climatização; toque no carro ou na trava → Diagnóstico | emulador |
| 10 | Aviso "Falta permitir…" + Configurar | cartão `warn` no herói | emulador |
| 11 | Mídia: título/artista/app, capa, anterior/tocar/próxima, abrir app | atalho (tocar, próxima) + janela (capa, anterior/tocar/próxima, Abrir app) | emulador (sem sessão) |
| 12 | Bateria: %, barra, linha da carga; toque → Diagnóstico | atalho + janela; "Ver todos os dados" → Diagnóstico | emulador |
| 13 | Painel: estado, motivo da falha, Mostrar no painel / Painel original | janela do botão Painel do dock; botão aceso com o painel ativo | emulador |
| 14 | Destino: Casa/Trabalho (ou Definir), manobra ativa, "Abrir Maps/Waze" | atalho Navegação + janela | emulador (sem rota) |
| 15 | Chip "Painel" no dock (liga/volta) | botão Painel → janela com o mesmo botão | emulador |
| 16 | Chip do app de navegação (abre Maps/Waze; nativo abre a busca) | botão de rota do dock | código |
| 17 | Abas Início, Navegação, Mídia, Telefone (discador #3/#15), Apps, Ajustes | dock: Início, Mídia, Telefone, Apps, Ajustes; Navegação pelo atalho/janela/busca | emulador |
| 18 | Busca (450 ms, tecla Buscar, limpar) | NavSearch | emulador |
| 19 | Painel Livre / Google Maps / Waze | NavSearch (e "Rota pelo …" na janela) | emulador |
| 20 | Rota ativa: encerrar a nativa (pergunta) ou abrir o app | NavSearch e janela Navegação | código |
| 21 | Casa/Trabalho/recentes, resultados, "Buscar no …", segurar para redefinir, faixa "Escolha o endereço…" | NavSearch (e segurar Casa/Trabalho na janela) | emulador |
| 22 | Mapa da busca com o carro e a linha da rota nativa (#14) | NavSearch + botão Centralizar | emulador |
| 23 | `route_search` (pedido de rota de outro app) | abre a busca com o texto | código |
| 24 | Grade de apps | página Apps (dock) | emulador |
| 25 | Início pelo botão HOME volta ao início; voltar não fecha o launcher | igual (e fecha a janela aberta) | código |
| 26 | Pedido de localização, `ClusterService`, `MediaRepository`, teste do ADB local | igual | emulador |
| 27 | Tema automático/claro/escuro (Ajustes) | igual + botão Claro/Escuro na barra | emulador |
| 28 | Tela em pé | `layout-port/` (atalhos 2 × 2) | emulador |

Fora da tela central, sem mudança: Ajustes (todas as opções, inclusive área segura e ajuste fino,
menu do painel Simples/Pequeno/Cheio/OFF, telemetria, modo de debug, simular rota, tela inicial),
registro do debug, Diagnóstico, painel de instrumentos (rota nativa, aviso de telemetria, área
segura), discador (só as cores) e previsão (só as cores).

**Novos** (todos com dado real): botão Claro/Escuro; categorias Recarga/Combustível/Estacionamento
(busca Photon); volume da mídia (o mesmo das teclas de volume do Android); Centralizar no mapa;
chip Pneus.

**Só visual que saiu da central**: o carro 2D pequeno do cartão "Carro" (`CarStatusView` continua no
painel), o ícone de velocímetro da saudação e o fundo "estúdio" (`BackdropDrawable`, ainda no código).

## Testes

`AuraTest` (JVM): todas as cores dos dois temas contra o `tokens.json`, contraste 4,5:1 do texto em
todas as superfícies, tema e botão de tema, cores Aura nas telas antigas, números BR, chips (sem dado
não vira fechado, fechado neutro, aberto `warn` com palavra, carregando `accent`, pneus), resumo,
trava, clima só com o que foi lido, bateria SDK → telemetria, linha da carga, uma janela por vez,
âncora/seta da janela e escala.

## Decisões que precisam do Paulo

1. **Painel em dois toques.** Antes o cartão/chip "Painel" ligava direto; agora o botão do dock abre
   a janela (mostra o motivo da falha) e o botão grande liga/volta. Dá para voltar ao toque único.
2. **Botão Claro/Escuro** sai do automático; o automático volta em Ajustes → Tema.
3. **Volume** na janela Mídia usa o volume de mídia do Android (não é comando ao carro). Se preferir
   sem, é só tirar.
4. **Apps nativos da BYD** (clima, energia): não abri nenhum porque não temos o nome dos pacotes no
   carro e não quis adivinhar. Com o nome (Diagnóstico ou `pm list packages`), as janelas ganham um
   botão "Abrir na BYD".
5. **Casa/Trabalho** mostram distância em linha reta (km), não minutos: sem rota calculada não há
   tempo de verdade.
6. **Modos do ar** (AUTO, A/C, Recircular, Desembaçador) só aparecem se o carro responder 0/1
   (significado ainda é hipótese, `docs/TELEMETRIA.md`).
7. **Densidade própria**: conferir no carro se textos e toques ficam do tamanho certo na DiLink 3.
8. Tela do painel de instrumentos não foi redesenhada (fora do escopo).
