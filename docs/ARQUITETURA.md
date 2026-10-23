# Arquitetura do Painel Livre

Atualizado: 2026-10-22 (v0.3.0). Pacote `br.painellivre`, um único módulo `:app`, Kotlin, Views
clássicas (sem Compose), coroutines + `StateFlow`. minSdk 28, targetSdk 29 (o comportamento do
Android 10 é o que a DiLink 3 espera), compileSdk 34.

## Visão geral

```
 Google Maps / Waze ──notificação──▶ NavNotificationListener ──▶ NavParser ──▶ NavRepository (StateFlow)
                                                                                │
 GPS da central ──▶ LocationRepository (StateFlow) ─────────────────────────────┤
                                                                                ▼
                       ClusterService (foreground) ──▶ ClusterController ──ADB local──▶ AutoContainer (16/35/18/0)
                             │   liga/desliga, vigia,          │                            │
                             │   convivência (YieldPolicy)     └─ am start --display N ─────┤
                             ▼                                                              ▼
                       LauncherActivity (central)                          ClusterActivity (display do painel)
                       cockpit + abas                                      mapa MapLibre + cartões
```

- **Fonte da rota**: o app não calcula rota. Ele lê a notificação contínua do Google Maps/Waze
  (título, texto, subtexto e o ícone grande da seta) e transforma em `ParsedNav`.
- **Privilégio**: tudo que exige uid 2000 (falar com o `AutoContainer`, abrir Activity em display de
  outro dono, `pm grant`, `appops`) passa pelo ADB da própria central (`SelfAdb`, biblioteca dadb).
- **Duas telas**: a central (`LauncherActivity`, pode virar a tela inicial) e o painel
  (`ClusterActivity`, aberta no display virtual `fission…xdja…` criado pelo Qt da BYD).

## Pacotes e arquivos

| Pacote | Arquivo | Responsabilidade |
|---|---|---|
| `adb` | `SelfAdb.kt` | Conexão única dadb com `127.0.0.1:5555`, reconecta 1× se cair, `ensureReady` (15×2 s no boot), `ready: StateFlow<Boolean?>`, mensagens de erro em pt-BR por tipo (porta fechada, chave recusada, tempo esgotado) |
| | `SystemSetup.kt` | "Conceder tudo": `pm grant` de localização e permissões BYD de leitura, `cmd notification allow_listener`, `deviceidle whitelist`, `appops RUN_ANY_IN_BACKGROUND`, `set-standby-bucket active`; virar/deixar de ser a tela inicial (`cmd package set-home-activity`) |
| `cluster` | `ClusterController.kt` | Acha o display do painel (app e `dumpsys display`), mede e guarda a geometria, decide o código de tamanho, manda `sendInfo` (lista segura), sequência de ativação/restauração, abre a tela pelo shell (`--windowingMode 1`, depois 5 + `am task resize`, depois `startActivity` direto) |
| | `PanelSize.kt` | **Kotlin puro.** Decide se manda 29/30/31 (ver "Regras de segurança" no `AGENTS.md`) |
| | `ClusterService.kt` | Serviço em primeiro plano: GPS, liga o painel quando a rota começa (modo `nav`), devolve o original depois de `restore_delay` s, vigia de 15 s que reabre a tela, convivência com outros apps, ações `SHOW/RESTORE/LAUNCH_ONLY/RELAUNCH/SIMULATE`, `status: StateFlow<ClusterStatus>` |
| | `ClusterActivity.kt` | Tela do painel: MapLibre (OpenFreeMap), carro como View fixa no ponto focal, câmera por quadro (Choreographer + `PoseSmoother`), limite de fps (`setMaximumFps`), vigia (`FreezeWatch`), cartões da manobra e da chegada, tema dia/noite, sai se cair no display 0, avisa o serviço quando é coberta |
| | `ClusterPreviewActivity.kt` | Mesma tela na central (toque fecha), para testar sem o painel |
| | `PoseSmoother.kt` | **Kotlin puro.** Posição/rumo do carro entre fixes do GPS (segue em frente ≤1,5 s, corrige em 0,6 s, salto >150 m, parado ignora <8 m) |
| | `FreezeWatch.kt` | **Kotlin puro.** Escada do vigia (`FreezeWatch`), limite por janela (`Budget`) e `YieldPolicy` |
| | `ManeuverView.kt` | Desenha a seta (bitmap do Maps/Waze recolorido, ou vetor por `Maneuver`) |
| | `CarArrowDrawable.kt` | Seta azul do carro (painel e mapa da central) |
| `nav` | `NavNotificationListener.kt` | `NotificationListenerService`: filtra pacotes de navegação, extrai textos e ícone, ignora repetidos |
| | `NavParser.kt` | **Kotlin puro.** Textos pt-BR/EN → `ParsedNav` (manobra, saída da rotatória, distância, rua, chegada, restante) |
| | `Maneuver.kt` | Tipos de manobra com ângulo e código de ícone do CAN da BYD (Fase 4) |
| | `NavRepository.kt` | `state: StateFlow<NavState>` (rota ativa, fonte, ícone, simulação) |
| | `NavSimulator.kt` | Rota de teste pelo mesmo parser; em debug injeta GPS falso |
| | `Places.kt` | Busca Photon, Casa/Trabalho/recentes (SharedPreferences JSON), abre a rota no Maps/Waze por intent |
| `info` | `WeatherRepository.kt` | Open-Meteo (posição arredondada a 0,1°, no máximo a cada 15 min) |
| | `MediaRepository.kt` | Sessão de mídia ativa (`MediaSessionManager`, usa o acesso às notificações) e controles |
| `car` | `VehicleData.kt` | Leitura por reflexão de `BYDAutoStatisticDevice` (bateria, autonomias, hodômetro). Só leitura |
| `location` | `LocationRepository.kt` | GPS da central (`LocationManager`), mantém o rumo quando parado, injeção só em debug |
| `ui` | `LauncherActivity.kt` | Cockpit: barra de cima, carro, cartões, abas (Início, Navegação, Apps; Mídia/Telefone/Ajustes abrem outra tela), lista de lugares, mapa da aba Navegação, aviso de configuração |
| | `cockpit/Palette.kt` | Temas dia/noite e pintura da árvore de Views por `android:tag` (ver abaixo) |
| | `cockpit/CockpitArt.kt` | Fundo "estúdio" e ilustração do SUV, desenhados em código |
| | `cockpit/WeatherIconDrawable.kt` | Ícone do tempo desenhado em código |
| | `SettingsActivity.kt` | `PreferenceFragmentCompat` (`res/xml/preferences.xml`) + ações (foto do carro, simular, conceder, diagnóstico, tela inicial) |
| | `DiagnosticsActivity.kt` | Info da central (getprop, displays, AutoContainer), tamanho do painel, fps, log, copiar |
| | `AppsAdapter.kt` | Grade de apps |
| `util` | `Prefs.kt` | Preferências tipadas (chaves abaixo) |
| | `AppLog.kt` | Log em memória (400 linhas) + logcat `PainelLivre` |
| raiz | `App.kt`, `BootReceiver.kt` | Inicializa MapLibre e canal de notificação; boot/`MY_PACKAGE_REPLACED` → serviço |

## Ciclo do painel

1. Rota começa (`NavRepository.state.active` vira `true`) → `ClusterService.autoShow()` (no máximo 1
   tentativa automática por minuto).
2. `ClusterController.activate()`: `ensureReady` (3 tentativas) → limpeza de estado velho (uma vez por
   processo) → código de tamanho se `PanelSize` aprovar → 1 s → `16` → 1 s → `35` → espera o display
   (até 20 s, app a cada 0,5 s e shell a cada 3 s).
3. `launchClusterUi()`: shell `am start --display N --windowingMode 1 -n br.painellivre/.cluster.ClusterActivity -f 0x10020000`.
4. `ClusterActivity` desenha; o vigia do serviço (15 s) reabre se ela morrer; o vigia da tela
   (1,5 s) cuida de congelamento; `onStop` com o display ligado = "coberta por outro app".
5. Rota termina → depois de `restore_delay` s → `restore()`: fecha a tela, `18` → 1 s → `0`.

## Tela central: temas por tag

As cores não ficam no XML. Cada View marca o papel em `android:tag` (direto ou via estilo `Cc*` em
`res/values/themes.xml`) e `Palette.apply(root, density)` pinta a árvore:

| Tag | Efeito |
|---|---|
| `card`, `card_click` | cartão arredondado (com ripple no segundo) |
| `chip`, `chip_on` | botão neutro / azul |
| `capsule`, `dockbar` | campo de busca, barra de baixo |
| `t1`, `t2`, `t3`, `accent`, `on_accent` | cores de texto |
| `icon`, `icon2`, `icon_accent`, `icon_on`, `icon_ok` | tint de ícone |
| `round`, `round_on` | botão redondo neutro / azul |
| `progress` | barra de progresso |

Para mudar o estado de um botão em tempo de execução: trocar a tag e chamar `palette.apply(view, density)`.
Layouts: `activity_launcher.xml` inclui `include_home`, `include_nav`, `include_dock` (variantes em
`layout-port/` para a tela em pé; a Activity é recriada ao girar) e os cartões `card_*.xml`.

## Painel: posições

Frações da altura `h` e largura `w` do display (1920×720 no Song Plus), dentro das margens do usuário:

- coluna de cartões em `x = 0,06w`, `y = 0,16h`; cartão da manobra com `0,30w` de largura e ícone de `0,19h`;
- cartão de chegada logo abaixo (Chegada / Tempo / Distância);
- câmera com padding de cima `0,30h` e de baixo `0,12h` → carro a `~0,59h`, centralizado;
- crédito "© OpenStreetMap" em `y = 0,66h`; aviso "SIMULAÇÃO" em cima no centro;
- nada de relógio: a faixa de cima é da BYD (hora, P, ECO, temperatura) e a de baixo também (kW,
  bateria, autonomia, velocidade).

## Preferências (`Prefs`)

| Chave | Padrão | Uso |
|---|---|---|
| `cluster_mode` | `nav` | `nav` (só com rota), `always`, `manual` |
| `restore_delay` | `20` | segundos até devolver o painel original |
| `panel_size` | `auto` | `auto`, `off`, `29`, `30`, `31` |
| `theme` | `auto` | `auto` (6h–18h30 claro), `day`, `night` |
| `map_style` | `auto` | `auto` (positron/dark pelo tema) ou estilo OpenFreeMap fixo |
| `map_tilt` | `45` | inclinação do mapa do painel |
| `max_fps` | `60` | limite andando (parado fica em 5) |
| `auto_heal` | `true` | recriar a superfície do mapa se travar |
| `inset_left/top/right/bottom` | `0` | margens extras no painel (px) |
| `start_on_boot` | `true` | sobe o serviço no boot |
| `nav_app` | `maps` | `maps` ou `waze` (busca, Casa/Trabalho) |
| `car_photo` | — | URI da foto do carro (no lugar da ilustração) |
| internas | — | `projection_powered`, `panel_geometry`, `last_boot_at`, `autocontainer_service_name`, `original_home_component`, `place_home`, `place_work`, `place_recents` |

## Testes

`app/src/test` (JVM, sem Android): `NavParserTest`, `PanelSizeTest`, `PoseSmootherTest`,
`FreezeWatchTest` (inclui `YieldPolicy`), `CockpitTest` (Photon, tema, códigos do tempo). Regra: lógica
nova que dá para separar do Android vai para uma classe Kotlin pura com teste. `org.json` real entra
só nos testes (`testImplementation("org.json:json")`); no app é o do Android.
