# Interface Aura — central e painel

Atualizado: 2026-10-23. Branch `feat/interface-aura` (não lançada). Desenho da designer em
`docs/design/aura-launcher/` (HTML/CSS/JS de referência; **não** é usado pelo app). O app implementa o
mesmo padrão em Android nativo (Views + Kotlin), sem WebView, na **tela central** (tela inicial,
busca, apps, discador e previsão) e, desde a rodada 2, no **painel de instrumentos** (`ClusterActivity`).

## Regras que esta interface segue

- **Comandos internos com liberação explícita (rodada 3).** `CarCommands` valida cada ação.
  A chave geral “Permitir comandos ao carro” e cada função no Diagnóstico começam desligadas.
  Com elas desligadas, nada muda no carro. Clima e temperaturas do dock abrem escolhas internas;
  o envio pede confirmação. Bancos/porta-malas ainda não têm contrato seguro e ficam bloqueados.
  Bateria continua leitura: não há comando confirmado. Rádio BYD sem API confirmada; mídia Android
  usa MediaSession/AudioManager dentro da interface, também com as duas liberações.
  Catálogo, fontes, limites e hipóteses em [COMANDOS.md](COMANDOS.md).
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
| `Aura.fmt.dec` / textos | `AuraFormat` → `Units` | `22,0°C`, `6,4 km`, `1h40`, `62%`. A escala (°C/°F, km/mi, psi…) vem de Ajustes → Unidades |

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
`AuraColors.toPalette()`; o painel usa `ClusterLook.of(AuraColors.forSetting(...))`.

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

Comportamento preservado fora da tela central: Ajustes (todas as opções, inclusive área segura e ajuste fino,
menu do painel Simples/Pequeno/Cheio/OFF, telemetria, modo de debug, simular rota, tela inicial),
registro do debug, Diagnóstico, comportamento do painel de instrumentos (rota nativa, aviso de telemetria, área
segura; visual Aura na rodada 2), discador (só as cores) e previsão (só as cores).

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
4. **Resolvido na rodada 2:** clima, energia e estados do carro ficam dentro do Painel Livre.
   Não adicionar botão para abrir apps da BYD ou apps externos nas novas janelas.
5. **Casa/Trabalho** mostram distância em linha reta (km), não minutos: sem rota calculada não há
   tempo de verdade.
6. **Modos do ar** (AUTO, A/C, Recircular, Desembaçador) só aparecem se o carro responder 0/1
   (significado ainda é hipótese, `docs/TELEMETRIA.md`).
7. **Densidade própria**: conferir no carro se textos e toques ficam do tamanho certo na DiLink 3.
8. **Resolvido na rodada 2:** painel redesenhado no padrão Aura; validação no carro pendente.


## Rodada 2 — tudo dentro, painel Aura e mapas

- Nenhum atalho novo abre apps externos. Tocar em reproduzir sem sessão usa o evento de mídia do
  Android (`resumeLast`), sem abrir outro app. Clima/energia continuam internos e somente leitura.
- **Exceções antigas preservadas para o Paulo decidir:** “Abrir app” na mídia; Maps/Waze pelo dock,
  destinos e rota ativa; grade Apps; telas do Android para conceder permissões; opção experimental de
  mover Maps/Waze ao painel. O discador mantém o caminho HFP/Telecom e a reserva do telefone BYD.
  A previsão é uma Activity do próprio Painel Livre. Não foram adicionados pacotes da BYD.
- `ClusterLook` usa os tokens Aura, cartões 95% opacos, borda/raio, Sora nos números e Albert Sans no
  texto. Ícones de linha na chegada e no aviso; manobras preservadas. Menu Simples/Pequeno/Cheio/OFF,
  SafeArea, `telemetryBounds`, aviso de telemetria, rota nativa e contorno debug mantidos.
- `MapLook`/`MapStyler` alargam vias principais em 1,6× e ruas em 1,4×, conservando as paradas de zoom
  do estilo. Trilhas/ferrovias não mudam. Aplicação tanto na central quanto no painel após carregar
  cada estilo. Rota em accent com contorno e largura por zoom; seta cheia e larga, contorno branco e
  sombra leve (60 dp na central, 12% da unidade no painel).
- Correções da revisão: duração da chegada sem reticências; cartão Pequeno ampliado antes da seta para não cortar a distância; GPS da simulação com relógio monotônico
  atualizado para a navegação nativa não rejeitar o fix como antigo.
- Regressão: 238 testes JVM, zero falhas (15 novos: `InsideInterfaceTest` e `MapLookTest`), lintDebug
  zero erros/76 avisos, assembleDebug concluído. Inclui contraste sobre mapas, tamanho dos textos,
  seta e área segura, expressões de zoom e as exceções de abertura externa. O bloqueio de setters da
  telemetria segue passando. Nada testado no carro nesta rodada.
- Capturas novas: `/tmp/pl-aura-prints/r2-*`. Pré-visualização na central do emulador 1920×720;
  a linha rosa é o contorno de debug da área segura. Não representa a sobreposição real da BYD.

Capturas finais: oito modos/temas do painel e duas com rota nativa (central e painel),
com cópias em `docs/telas/aura-r2-*.png`. Preferências de antes da rodada 2 restauradas
no emulador a partir do backup do Claude (`prefs-orig.xml`, debug desligado).

## Rodada 3 — comandos

Decisão explícita do Paulo substitui a regra de somente leitura das rodadas anteriores. As descrições
históricas da rodada 2 permanecem como registro, não como restrição atual.

- Temperaturas (janela e dock) escolhem 16–30 °C inteiros: a fonte não comprova escala de meio grau.
  Ventilação escolhe 0–7; cabeçalho da janela liga/desliga o ar; modos escolhem ligar/desligar.
- Não há mudança otimista dos números ou cores. “Aguarde…” é imediato; Toast e cabeçalho mostram
  rejeição, envio e confirmação/ausência dela. A telemetria relida atualiza os indicadores reais.
- Bancos e porta-malas estão ligados à mesma fronteira, mas o seletor informa bloqueio antes de
  permitir escolher. IDs/valores ambíguos nunca são enviados. Sem dado não vira desligado.
- Modos sem dado aparecem apagados e com descrição “Sem dado”. GET cru desses modos não permite
  declarar confirmação da escrita; AUTO usa 1/2 no fio e isso não redefine o decoder do GET.
- Mídia sem sessão envia play ao Android; próximo/anterior podem não ser atendidos por rádio BYD.
  Volume é relido no AudioManager; callbacks continuam mostrando o estado real da sessão.
- Confirmação humana antes de cada comando de clima. Limite global de um envio por segundo,
  serialização e transporte sem repetição automática; sair durante envio não o cancela no carro.

Validação da rodada 3: 248 testes JVM, zero falhas; `testDebugUnitTest lintDebug assembleDebug`
com JDK 17. Lint sem erros (79 avisos). No emulator-5580 foram conferidos a janela de clima,
seletor, confirmação e rejeição com a chave desligada, registrada no log; lista de liberações
individuais e recusa de hipótese. Capturas `docs/telas/aura-r3-*.png`. Nenhum teste no carro.


## Rodada 4 — área segura opcional

Decisão do Paulo após observar o painel real: **Ajustes → Área segura no painel**
(`safe_area_enabled`) é opt-in, desligada por padrão, inclusive nas instalações com margens
antigas salvas. Desligada, ignora o padrão e os quatro ajustes: mapa, carro, cartões e aviso de
telemetria usam todo o conteúdo disponível do menu, sem recorte da SafeArea. Ligada, conserva
o cálculo anterior (padrão Song Plus e ajustes salvos). Os ajustes só ficam disponíveis quando ligada.
Esta decisão substitui as descrições anteriores que tratavam a área segura como obrigatória.

`Prefs.safeAreaEnabled` persiste em SharedPreferences; `SafeArea.effectiveInsets` retorna zero
quando desligada. `SafeArea.Plan` continua organizando os elementos, agora sobre o conteúdo inteiro.
O listener de `ClusterActivity` recalcula posições e câmera sem reiniciar. Menu
Simples/Pequeno/Cheio/OFF, rota nativa e aviso de telemetria conservam seus fluxos; OFF continua
ocultando o conteúdo. Debug continua disponível, mas o contorno da área segura só aparece com
a opção ligada. O aviso de telemetria fica no canto superior direito do conteúdo nos dois casos.

Regressões JVM: padrão desligado mesmo com margens antigas, opt-in explícito, margens ligadas e
ignoradas, cartões e aviso dentro do conteúdo inteiro/reduzido. No carro, conferir as duas opções,
rota, manobras, telemetria e os quatro modos, com atenção às sobreposições originais da BYD.

Validação da rodada 4: comando solicitado `JAVA_HOME=$HOME/.jdks/jdk-17.0.21+9
ANDROID_HOME=$HOME/Android/Sdk ./gradlew testDebugUnitTest lintDebug assembleDebug` concluído:
250 testes JVM, zero falhas; lintDebug zero erros e 79 avisos; assembleDebug concluído.
Emulador `emulator-5580`: capturas 1920×720, modo Cheio, área segura desligada, manobras
simuladas nos temas claro e escuro, inspecionadas visualmente:
`docs/telas/aura-r4-painel-claro-cheio.png` e `aura-r4-painel-escuro-cheio.png`.
Preferências originais restauradas. Telemetria posicionada nos dois layouts coberta na JVM;
não houve teste no carro nesta rodada.

## Correções 0.5.4 — foto do Song Plus e menu do painel

A foto `foto-painel-0.5.3.jpg` mostrou a manobra sobre o relógio/HEV/D/NORMAL/RDS e a chegada
cortada à esquerda. Geometria estimada em 1920×720: topo nativo até y 170, borda curva x < 120,
ícones esquerdos x 100–180/y 520–580, base nativa desde y 540 e ícones direitos x 1800–1900/y 180–340.
Com Área segura desligada (continua opt-in), `SafeArea.defaultPlan` posiciona a coluna em x 170/y 185,
largura 590, manobra 110, espaço ~9 e chegada ~95. Telemetria abaixo dos cartões até y 493;
aviso logo abaixo, antes de y 520. O foco fica em x 960/y 380, no centro visível.
Tudo escala pelo display real. Área segura ligada preserva o cálculo e ajustes anteriores.
Texto de rua compacto usa uma linha para caber; o mapa Cheio continua ocupando todo o display.

### Causa do seletor e correção

O histórico `4fa8f47` introduziu a leitura opcional em `feat/comandos-painel-debug`;
`bac5aa0` acrescentou SDK → ADB (0.5.1), mas manteve o padrão false. O histórico da branch
`feat/rota-nativa-painel` não continha os arquivos do menu naquela base; a rota nativa não era
um leitor alternativo. No código 0.5.3, `PanelMenuWatcher.layouts` nem chamava o leitor com
`panel_menu_follow=false`; `effective` devolvia UNKNOWN e `layoutFor` mantinha Cheio.
Essa é a causa comprovada no código, sem afirmar qual evento chega hoje no carro.

Agora a chave ausente vale true (XML também). Como o XML antigo gravava false mesmo sem escolha
do usuário, a migração única `panel_menu_migration=1` liga também esse false. Depois da migração,
desligamentos explícitos são respeitados.
O observador roda enquanto ClusterActivity está STARTED, independentemente do debug e da rota.
SDK negado usa o único comando ADB fixo conferido, somente API 29; a estabilização requer duas
leituras iguais. Erros preservam temporariamente o último modo e depois voltam a Cheio.
Cada leitura boa registra bruto, modo e origem no AppLog; falhas são registradas uma vez por
mudança e incluem o motivo SDK/ADB. Toda troca de desenho é registrada, incluindo o primeiro.

Ajustes → Tamanho do mapa no painel salva Seguir o painel (padrão), Cheio, Pequeno, Simples ou OFF.
Prioridade: teste temporário do debug > escolha manual > leitura estável do painel.
OFF cobre mapa e cartões; Simples cobre mapa e esconde carro; Pequeno reduz o conteúdo ao centro
entre os mostradores; Cheio ocupa todo o display com as margens fixas dos cartões.
`PanelMenu.bounds` extrai a geometria pura da moldura. Dimensões zero aguardam o primeiro layout;
o listener já existente reaplica após medir. Mudança de tamanho do conteúdo recalcula cartões,
carro e padding da câmera pelo listener de ClusterActivity. Nenhum código novo de AutoContainer.

No carro, confirmar as quatro opções e a origem/bruto no Log do app, incluindo atualização de
2 leituras (~2 s), ADB indisponível, escolha manual e retorno a Seguir; conferir cortes com nomes
de rua longos, ETA com três campos, avisos/telemetria e Área segura ligada/desligada.
As medidas vêm de foto, não de calibração física; prints do emulador não incluem a sobreposição BYD.

### Validação e capturas da 0.5.4

`JAVA_HOME=$HOME/.jdks/jdk-17.0.21+9 ANDROID_HOME=$HOME/Android/Sdk ./gradlew test lintDebug assembleDebug`:
258 testes por variante (debug e release), zero falhas; lint com zero erros e 81 avisos.
Regressões novas: `Panel054Test` (geometria/moldura/modos/prioridade), `PrefsTest` (migrações,
padrões e desligamentos posteriores), `CarCommandsTest` (toque direto e todas as hipóteses
bloqueadas). Testes existentes de leitor SDK negado → ADB, estabilização, P, limites, frequência,
log e releitura também executados. Nenhum teste no carro.

Capturas em `emulator-5580`, display 0 com `wm size 1920x720`, `ClusterPreviewActivity` e
simulação de navegação; escolha manual persistente exercita o mesmo layout de cada modo:

- `docs/telas/aura-054-painel-cheio.png`
- `docs/telas/aura-054-painel-pequeno.png`
- `docs/telas/aura-054-painel-simples.png`
- `docs/telas/aura-054-painel-off.png`
- `docs/telas/aura-054-comandos.png` (climatização sem telemetria inventada)

Cópias em `/tmp/aura-054-prints/`. O sandbox não permite escrever em
`/home/paulo/Documentos/REA-BYD/prints/aura/`; por isso a cópia nesse destino ficou pendente.
Preferências do emulador usadas para os quatro modos foram restauradas após as capturas.

## Correções 29/10/2026 — issues #20 e #22, fotos no carro

Branch `fix/ac-audio-tela-cheia` (base 0.5.4/`versionCode` 10; versão não muda nesta etapa).

### Issue #20 — diálogo da climatização

A foto `docs/issues/ac-auto-dialogo.jpg` mostrou um `AlertDialog` "AUTO" com `Ligar`/`Desligar`/
`CANCELAR`; `docs/issues/ac-ventilacao-dialogo.jpg` mostrou "Ventilação" com a lista `0`…`7`.
Causa raiz (não era a hipótese do flag de confirmação, já removido em 24/10 — ver `docs/COMANDOS.md`):
**todo** controle de clima na janela Climatização chamava `chooseCommand(key)`
(`LauncherActivity.kt`), e essa função sempre monta `AlertDialog.Builder().setItems(...)` com a
lista de valores do `Spec` — para um `max=1.0` (toggle) a lista vira exatamente `Ligar`/`Desligar`;
para `ac_fan` (`max=7.0`) vira a lista `0`…`7`. Não existia um caminho de toggle nem de passo
direto; só o seletor genérico.

Correção: AUTO (`ac_auto`), A/C (`ac_compressor`), recirculação (`ac_recirc`), desembaçador
(`ac_defrost`) e a energia geral (`ac_on`, o botão "Ligar/desligar" da janela) passam a chamar
`toggleClimate(key)`, que lê o estado atual em `CarReadouts.climate(lastTelemetry)` e manda o
oposto com `CarCommands.toggleValue` — um toque, sem diálogo. A ventilação ganhou dois botões
(`fanDown`/`fanUp`, estilo igual ao +/- de volume de `pop_media.xml`) chamando `stepFan(up)`, que
usa `CarCommands.fanStep` para subir/descer um nível (0–7) sem lista modal. A temperatura
(16…30°, 15 valores) continua por `chooseCommand`: não é liga/desliga nem cabe num passo só, e a
issue não pediu mudar isso.
`toggleValue`/`fanStep` são Kotlin puro em `CarCommands` (sem import Android), testados na JVM.

### Issue #22 — tela não preenche

A foto `docs/issues/tela-principal-nao-preenche.jpg` mostra a Home Aura com a barra de sistema do
Android (ícones do carro: localização/Bluetooth/4G/sino/perfil) ainda visível acima da nossa barra
de status, e a barra nativa do carro (voltar/início/clima rápido/ajustes) abaixo do nosso dock —
ou seja, o Android reservava a faixa de status para o sistema em vez de dar essa altura para a
janela do app, porque `Theme.PainelLivre.Aura` (usado por `LauncherActivity`, `DialerActivity` e
`WeatherActivity`) não declarava `android:windowFullscreen`. `AuraScale.densityDpi` já lida bem
com um `heightPx` menor que o físico (teste `screenScalesToTheBaseDrawing`, caso 1920×1024), mas
isso só reescala o desenho para o espaço recebido — não devolve a faixa do sistema para o app.

Correção: `android:windowFullscreen=true` em `Theme.PainelLivre.Aura` (`values/aura.xml`), a mesma
técnica já usada no painel (`Theme.PainelLivre.Cluster`). Isso esconde a barra de status do
Android e devolve essa altura à janela do app; a barra nativa inferior do carro não faz parte da
nossa janela e continua aparecendo, como o critério de aceite pede. Não muda layout, margens nem
paddings dentro de `activity_launcher.xml` — o problema era só a janela receber menos altura do
que o display físico.

### Validação

`./gradlew testDebugUnitTest lintDebug assembleDebug` (sandbox com `GRADLE_USER_HOME=/tmp/painel-gradle`,
`ANDROID_HOME=/home/paulo/Android/Sdk`): 261 testes JVM (258 da 0.5.4 + 3 novos:
`CarCommandsTest.toggleAndFanStepArePureAndClamped`,
`CarCommandsTest.modosDeArEVentilacaoViramToqueUnicoSemDialogoDeEscolha`,
`AuraTest.homeThemeReclaimsTheStatusBarToFillTheScreen`), zero falhas; lint zero erros;
`assembleDebug` concluído. Nenhum teste no carro nesta etapa (sem acesso); o AVD deste ambiente
não tem `/dev/kvm`, então as capturas visuais das duas correções ficam pendentes para o Paulo
conferir no carro ou num emulador com aceleração.

## Correção #21 — retorno dos comandos de áudio (29/10/2026)

`LauncherActivity.command` mostrava o retorno de `CarCommandRuntime.execute` em um Toast longo
após cada comando, inclusive mídia, e também escrevia esse retorno em `popClimateState`.
`CommandFeedback` agora suprime os retornos rotineiros de mídia (sessão relida, volume relido,
sem confirmação de sessão Android). Tocar/pausar, próxima, anterior e volume continuam pelo
mesmo caminho de execução, com releitura, limite de frequência e log preservados. Os ícones e
o indicador de volume continuam refletindo o estado disponível. Falhas/rejeições mantêm aviso;
comandos de mídia não escrevem no estado do clima. Sem API nova de rádio ou fonte.

Regressão JVM: `CommandFeedbackTest`, incluindo integração com a Activity. Conferência de rádio
BYD no carro ainda depende do dono.

Validação local: 262 testes debug, zero falhas; lintRelease zero erros e 80 avisos; build debug
concluído. Emulador Android 10 isolado (5590): volume 5 → 6 → 5, play sem sessão sem Toast;
próxima/anterior desabilitados sem sessão. Captura: `docs/telas/aura-054-audio-sem-toast.png`.
Uma captura estática não demonstra a ausência temporal de Toast; a política e sua ligação ao
caminho da Activity são cobertas pelos testes JVM.

## Achados extras do teste no carro — 0.5.6 (29/10/2026)

Teste da v0.5.5-teste no carro de Paulo (DiLink 3.0, 15:07–15:10). Diálogos sumiram e o fan +/-
confirmou certo; três problemas novos apareceram e foram corrigidos aqui (detalhe do sinal AUTO e
do coalesce em `docs/COMANDOS.md`, "Achados do teste no carro em 29/10"):

- **Temperatura também era lista.** O texto acima ("a temperatura continua por `chooseCommand`")
  descrevia a 0.5.5, mas Paulo pediu o mesmo tratamento das issue #20 para ela. `pop_temp.xml`
  ganhou os botões `tempDown`/`tempUp` (mesmo estilo de `fanDown`/`fanUp`) ao redor do valor;
  `LauncherActivity.stepTemp` sobe/desce 1° usando `CarCommands.stepValue` (genérico: passo e
  limites vêm do próprio `Spec`). Os atalhos de temperatura no dock (`dockTempDriver`/
  `dockTempPassenger`) não chamam mais `chooseCommand`: abrem a janela Climatização, igual ao
  comentário que já existia em `aura_dock.xml` ("tocar abre a Climatização") — o código é que
  ainda não seguia isso.
- **Bancos e porta-malas ainda mostravam Toast de bloqueio ao tocar** (`chooseCommand` checava
  `spec.confirmed` e mostrava "Comando ainda não confirmado; controle bloqueado" antes de montar a
  lista). Aquecimento/ventilação dos bancos virou `toggleSeat` (liga nível 1 / desliga, sem lista
  de 4 níveis — se o Paulo quiser os 3 níveis quando os Specs forem confirmados, é um pedido novo);
  o chip do porta-malas virou `toggleTrunk`. Os dois continuam bloqueados pelo `CarCommands`
  (Specs hipótese, sem `dev`/`fid` ou sem P confirmado) — só a UI deixou de perguntar antes.
- `chooseCommand` foi removido inteiro; não sobrou `AlertDialog.setItems` para nenhum comando do
  carro (os `AlertDialog` restantes são de navegação/diagnóstico, sem relação com `CarCommands`).
- Toques rápidos: `LauncherActivity.command` não rejeita mais com Toast "Aguarde o comando
  anterior" — toggles ficam numa fila de 1 (`pendingCommand`) e ventilação/temperatura somam no
  alvo com debounce de 350 ms (`stepCommand`), detalhado em `docs/COMANDOS.md`.
- Validação local: 271 testes JVM, zero falhas; `lintDebug` zero erros (80 avisos). Sem novo
  teste no carro desta rodada para bancos/porta-malas (continuam bloqueados por hipótese); o
  comportamento de temperatura/AUTO/recirculação pede confirmação do Paulo na próxima instalação.
