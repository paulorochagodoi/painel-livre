# Arquitetura do Painel Livre

Atualizado: 2026-10-23 (v0.3.0 + issues #7, #3, #4 não lançadas). Pacote `br.painellivre`, um único módulo `:app`, Kotlin, Views
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
| | `SystemSetup.kt` | "Conceder tudo": `pm grant` de localização e permissões BYD de leitura, `cmd notification allow_listener`, `deviceidle whitelist`, `appops RUN_ANY_IN_BACKGROUND`, `set-standby-bucket active`; virar/deixar de ser a tela inicial (`cmd package set-home-activity`, conferido antes e depois) |
| | `HomeChoice.kt` | **Kotlin puro.** Lê a resposta do `resolve-activity` (nosso / outro / seletor / desconhecido), o que guardar como launcher original, se o `set-home-activity` deu certo e o próximo passo (ADB → tela "Tela inicial" → seletor) |
| `cluster` | `ClusterController.kt` | Acha o display do painel (app e `dumpsys display`), mede e guarda a geometria, decide o código de tamanho, manda `sendInfo` (lista segura), sequência de ativação/restauração, abre a tela pelo shell (`--windowingMode 1`, depois 5 + `am task resize`, depois `startActivity` direto) |
| | `PanelAutomation.kt` | **Kotlin puro.** Decisão dos modos automático/navegação/manual e temporizador de restauração, testados com tempo virtual |
| | `PanelSize.kt` | **Kotlin puro.** Decide se manda 29/30/31 (ver "Regras de segurança" no `AGENTS.md`) |
| | `ClusterService.kt` | Serviço em primeiro plano: GPS, liga o painel quando a rota começa (modo `nav`), devolve o original depois de `restore_delay` s, vigia de 15 s que reabre a tela, convivência com outros apps, ações `SHOW/RESTORE/LAUNCH_ONLY/RELAUNCH/SIMULATE`, `status: StateFlow<ClusterStatus>` |
| | `ClusterActivity.kt` | Tela do painel: MapLibre (OpenFreeMap), carro como View fixa no ponto focal, câmera por quadro (Choreographer + `PoseSmoother`), limite de fps (`setMaximumFps`), vigia (`FreezeWatch`), cartões da manobra e da chegada, tema dia/noite, sai se cair no display 0, avisa o serviço quando é coberta |
| | `ClusterPreviewActivity.kt` | Mesma tela na central (toque fecha), para testar sem o painel |
| | `PanelMenu.kt` | **Kotlin puro.** Menu de navegação do painel (issue #9): valor → modo (1 OFF, 2 Simples, 3 Pequeno, 4 Cheio, confirmado no carro), parser do `Parcel`, desenho de cada modo, `Follower` (2 leituras iguais, volta ao normal após falhas), só getters no debug |
| | `PanelMenuReader.kt` | **Kotlin puro.** Lê `INSTRUMENT_NAVI_TYPE`: SDK primeiro; se recusar por permissão, ADB local com o comando fixo de leitura |
| | `PanelMenuWatcher.kt` | Lê o modo enquanto a tela do painel está aberta (se "Seguir o menu" estiver ligado) e `PanelMenuFrame`, a moldura que reposiciona/cobre o conteúdo da `ClusterActivity` |
| | `PoseSmoother.kt` | **Kotlin puro.** Posição/rumo do carro entre fixes do GPS (segue em frente ≤1,5 s, corrige em 0,6 s, salto >150 m, parado ignora <8 m) |
| | `SafeArea.kt` | **Kotlin puro.** Área segura do mapa no painel (padrão Song Plus + ajuste fino), posição dos cartões, do carro e foco da câmera |
| | `FreezeWatch.kt` | **Kotlin puro.** Escada do vigia (`FreezeWatch`), limite por janela (`Budget`) e `YieldPolicy` |
| | `ManeuverView.kt` | Desenha a seta (bitmap do Maps/Waze recolorido, ou vetor por `Maneuver`) |
| | `CarArrowDrawable.kt` | Seta azul do carro (painel e mapa da central) |
| `nav` | `NavNotificationListener.kt` | `NotificationListenerService`: filtra pacotes de navegação, extrai textos e ícone, ignora repetidos |
| | `NavParser.kt` | **Kotlin puro.** Textos pt-BR/EN → `ParsedNav` (manobra, saída da rotatória, distância, rua, chegada, restante) |
| | `Maneuver.kt` | Tipos de manobra com ângulo e código de ícone do CAN da BYD (Fase 4) |
| | `NavRepository.kt` | `state: StateFlow<NavState>` (rota ativa, fonte, ícone, simulação) |
| | `NavSimulator.kt` | Rota de teste pelo mesmo parser; em debug injeta GPS falso |
| | `Places.kt` | Busca Photon, Casa/Trabalho/recentes (SharedPreferences JSON), abre a rota no Maps/Waze por intent |
| `info` | `WeatherRepository.kt` | Open-Meteo (posição arredondada a 0,1°, no máximo a cada 15 min; sem dados, 1 vez por minuto): `weather` (cartão) e `forecast` (tela de previsão) do mesmo pedido |
| | `Forecast.kt` | **Kotlin puro.** `ForecastParser` (URL e leitura de agora, 24 h e 7 dias, rótulos "Agora"/"15h"/"Hoje"/"Amanhã"/"Sáb 25") e `ForecastText` |
| | `MediaRepository.kt` | Sessão de mídia ativa (`MediaSessionManager`, usa o acesso às notificações) e controles |
| `phone` | `DialNumber.kt` | **Kotlin puro.** `DialNumber` (teclas aceitas, limpeza, formato brasileiro, `tel:` com `#` escapado), `CallPlan` (ordem de tentativas para ligar, caminhos diretos, conferência depois de ligar, quando pedir `CALL_PHONE`), `CallAccounts` (conta HFP, quem atende o `ACTION_CALL`), `PhoneApps` (acha o app de telefone/Bluetooth do carro) |
| | `CallPaths.kt` | Contas de chamada do Telecom, proxy do cliente HFP (`HEADSET_CLIENT`) e `dial` por reflexão, `isInCall` |
| `car` | `VehicleData.kt` | Leitura por reflexão de `BYDAutoStatisticDevice` (bateria, autonomias, hodômetro). Só leitura |
| | `InstrumentSdk.kt` | Leitura por reflexão de `BYDAutoInstrumentDevice`: `INSTRUMENT_NAVI_TYPE` pelo `get(device, feature)` e getters candidatos do debug. Só leitura |
| `debug` | `DebugLog.kt` | **Kotlin puro.** `DebugBuffer`: registro do modo de debug (3000 linhas, horário, origem), só grava ligado |
| | `Probe.kt` | **Kotlin puro.** `ProbeParse` (seções do comando de coleta, `settings list`, `getprop`, histórico de broadcasts, candidatos ★, esconder números/MAC) e `ProbeTracker` (linha de base e só as novidades) |
| | `DebugProbe.kt` | Coleta a cada 3 s (SDK + ADB só leitura + receptores), fotografia completa, liga/desliga pela preferência |
| `location` | `LocationRepository.kt` | GPS da central (`LocationManager`), mantém o rumo quando parado, injeção só em debug |
| `ui` | `LauncherActivity.kt` | Cockpit: barra de cima, carro, cartões, abas (Início, Navegação, Apps; Mídia/Telefone/Ajustes abrem outra tela), lista de lugares, mapa da aba Navegação, aviso de configuração; toque no tempo abre a previsão; `onNewIntent` com `HOME` volta à aba Início |
| | `DialerActivity.kt` | Discador interno (aba Telefone): teclado, Rediscar, App do carro; liga pela ordem de `CallPlan` e passa ao próximo caminho se a central recusar |
| | `WeatherActivity.kt` | Previsão do tempo: agora, 24 horas (rolagem lateral) e 7 dias com faixa mín–máx |
| | `cockpit/Palette.kt` | Temas dia/noite e pintura da árvore de Views por `android:tag` (ver abaixo) |
| | `cockpit/CockpitArt.kt` | Fundo "estúdio" e ilustração do SUV, desenhados em código |
| | `cockpit/WeatherIconDrawable.kt` | Ícone do tempo desenhado em código |
| | `SettingsActivity.kt` | `PreferenceFragmentCompat` (`res/xml/preferences.xml`) + ações (foto do carro, simular, conceder, diagnóstico, tela inicial) |
| | `DebugActivity.kt` | Tela do modo de debug: marcar a opção do painel, testar desenho, fotografia, escutar ação, copiar/compartilhar/salvar |
| | `DiagnosticsActivity.kt` | Info da central (getprop, displays, AutoContainer), tamanho do painel, fps, log, copiar |
| | `AppsAdapter.kt` | Grade de apps |
| `util` | `Prefs.kt` | Preferências tipadas (chaves abaixo) |
| | `AppLog.kt` | Log em memória (400 linhas) + logcat `PainelLivre` |
| raiz | `App.kt`, `BootReceiver.kt` | Inicializa MapLibre e canal de notificação; boot/`MY_PACKAGE_REPLACED` → serviço |

## Tela inicial (launcher)

O alias `.ui.HomeAlias` (filtro `MAIN` + `HOME` + `DEFAULT`, alvo `LauncherActivity`, `singleTask`)
vem **desligado** no manifesto. A v0.4.0 o trazia ligado (issue #7) e a central da BYD recusou
instalar o APK ("Fail in installation of desktop apps"; hipótese: o instalador recusa APK com launcher
ativo — a confirmar no carro). Desde a v0.4.1 o app liga o alias em tempo de execução
(`PackageManager.setComponentEnabledSetting`, `DONT_KILL_APP`, permitido para os próprios
componentes), e só a pedido do motorista. Fluxo de "Usar como tela inicial" (`SystemSetup.becomeHome`
+ `HomeChoice`):

1. Lê o estado do alias (`getComponentEnabledSetting` → `HomeChoice.aliasOn`; "padrão" = desligado).
2. Com ADB: lê o launcher atual (`resolve-activity --brief`) **antes** de ligar o alias (assim a BYD é
   a única e não sai o seletor) e guarda-o se for outro launcher de verdade (nunca o `ResolverActivity`).
3. Liga o alias se o próximo passo pedir (`HomeChoice.mustEnableAlias`): o `set-home-activity` recusa
   componente desligado e a tela "Tela inicial"/o seletor só listam o app com o filtro ativo.
4. Com ADB: manda `cmd package set-home-activity br.painellivre/.ui.HomeAlias` e lê de novo para dizer
   se funcionou. Sem ADB ou recusado: abre `Settings.ACTION_HOME_SETTINGS` ou, se a BYD tirou essa
   tela, o próprio Início, para o Android mostrar o seletor com "Sempre".

"Voltar ao launcher da BYD" define o original guardado; se não houver original (ou a central recusar),
desliga o alias para a BYD voltar a ser a única tela inicial. Com o alias ligado e nenhum preferido, o
Android pode mostrar o seletor uma vez ao tocar em Início — é o comportamento normal de launcher.

## Telefone

A central não tem chip; a ligação é do celular pelo Bluetooth (HFP). No AOSP de carro, o
`HfpClientConnectionService` (`com.android.bluetooth`) registra no Telecom uma conta de chamada por
celular conectado; é por ela que a ligação sai pelo celular. Issue #15: no carro a chamada ia para o
app de telefone da BYD; por isso os caminhos **diretos** vêm primeiro. `CallPlan.routes`:

1. `PLACE_CALL_HFP` — `TelecomManager.placeCall` com `EXTRA_PHONE_ACCOUNT_HANDLE` da conta Bluetooth
   (`CallAccounts.pickHfp`: classe com "hfp", pacote `com.android.bluetooth` ou "bluetooth" no nome).
2. `CALL_INTENT` — `ACTION_CALL`, só se quem atende for o Telecom do Android
   (`CallAccounts.SYSTEM_CALL_HANDLERS`).
3. `HFP_DIAL` — `BluetoothHeadsetClient.dial(device, número)` por reflexão (perfil 16, celular conectado).
4. `PLACE_CALL` — `placeCall` com a conta padrão.
5. `CALL_INTENT_APP` — `ACTION_CALL` atendido por um app (o telefone da BYD): pode só abrir o app.
6. `DIAL_INTENT` — `ACTION_DIAL` com o número. 7. `CAR_PHONE_APP` — app do carro com o número copiado.

Os caminhos 1–4 precisam de `CALL_PHONE` (menos o 3); `READ_PHONE_STATE` é pedida junto (sem ela o
Telecom não lista as contas) e as duas entram no "Conceder permissões pelo ADB". Cada falha
(`ActivityNotFound`, `SecurityException`, `dial` nulo) passa para a próxima. Depois de um caminho
direto o app confere `TelecomManager.isInCall` em 3 s (`CallPlan.afterCheck`) e, sem confirmação,
**só avisa** ("toque em App do carro"): nunca tenta outro caminho sozinho, para não ligar duas vezes.
A tela de chamada em andamento é a do discador padrão da central (InCallService): o Android a mostra
com qualquer caminho. Com o modo de debug ligado, o discador registra quem atende cada intent, as
contas do Telecom, o cliente HFP, a ordem, o caminho usado, as falhas e a resposta do sistema —
sem o número, com números e MACs escondidos (`ProbeParse.redactPersonal`). O número nunca vai para o
`AppLog`. `uses-feature android.hardware.telephony` é opcional.

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

Tudo fica dentro da **área segura** (`cluster/SafeArea.kt`, Kotlin puro, `SafeAreaTest`): o retângulo
que os desenhos da BYD não cobrem. Medidas na foto do painel do Song Plus (1920×720):

| Desenho da BYD por cima | Onde |
|---|---|
| Faixa de cima (hora, HEV, P, NORMAL, ASE, temperatura, luzes) | y 0–75 no centro |
| Moldura curva | recorta os cantos de cima; o cartão perdia 40–60 px à esquerda |
| Avisos à esquerda / à direita | x < 200, y 380–460 / x > 1720, y 60–300 |
| Assistente de faixa | x 1290–1450, y 420–520 |
| Faixa de baixo (kW, bateria %, autonomia, OK, velocidade, odômetro) | y > 500, toda a largura |

- Padrão Song Plus: margens **esquerda 230, topo 90, direita 230, base 230** → área x 230–1690,
  y 90–490. As medidas são do painel de referência 1920×720 e escalam com o display real.
  `safe_area=none` usa a tela inteira.
- Ajuste fino (`inset_*`, ±400 px por lado) somado ao padrão; a área nunca fica menor que 30% × 30%
  do display.
- Com o menu do painel em "Pequeno" (#9) o conteúdo encolhe: a área segura é do display e é recortada
  pelo conteúdo (`SafeArea.plan`).
- Cartão da manobra colado no canto de cima à esquerda da área (x 230, y 90), largura `0,30w` sem
  passar do carro; cartão de chegada logo abaixo. Tamanhos em função de `unit` = altura do display,
  menor só se a área for baixa demais para os cartões (`CARDS_HEIGHT_UNITS`).
- Carro no meio da largura da área, a **67,5% da altura** dela (x 960, y 360 no Song Plus). A câmera
  usa o padding mínimo que põe o ponto focal no carro (`SafeArea.focusPadding`; no Song Plus é zero,
  porque y 360 é o meio do display).
- Crédito "© OpenStreetMap" no canto de baixo à direita da área (longe dos cartões e acima do
  assistente de faixa); aviso "SIMULAÇÃO" no topo, no meio da área.
- Modo de debug ligado: contorno rosa da área segura com as margens finais embaixo à esquerda (para
  ajustar olhando o painel; captura em `docs/telas/8-painel-area-segura.png`).
- Nada de relógio: a faixa de cima é da BYD.

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
| `safe_area` | `songplus` | área segura do mapa no painel: `songplus` (230/90/230/230) ou `none` |
| `inset_left/top/right/bottom` | `0` | ajuste fino da área segura (px, −400 a 400, somado ao padrão) |
| `start_on_boot` | `true` | sobe o serviço no boot |
| `nav_app` | `maps` | `maps` ou `waze` (busca, Casa/Trabalho) |
| `car_photo` | — | URI da foto do carro (no lugar da ilustração) |
| `panel_menu_follow` | `false` | seguir o menu de navegação do painel (issue #9, experimental) |
| `debug_mode` | `false` | modo de debug (issue #9) |
| `debug_actions` | — | ações de broadcast extras que o debug escuta |
| internas | — | `projection_powered`, `panel_geometry`, `last_boot_at`, `autocontainer_service_name`, `original_home_component`, `place_home`, `place_work`, `place_recents` |

## Testes

`app/src/test` tem **179 testes JVM** (117 até a v0.4.1 + 21 da issue #9 + 5 da issue #15 + os da rota nativa, da leitura do menu e da área segura), sem carro, emulador,
rede ou espera de tempo real. Execute com JDK 17:

```bash
./gradlew testDebugUnitTest
./gradlew lintRelease
```

| Suíte | Cobertura |
|---|---|
| `NavParserTest` e `NavRegressionTest` | Maps/Waze: manobra (seta de reserva), distância, rua, instrução, resumo, chegada, texto expandido/repetido, prioridade do título, espaços Unicode, inglês/unidades imperiais e ruído |
| `PanelAutomationTest` | modos `nav`/`always`/`manual`, painel já ativo, prazo exato, atraso zero/600 s, nova rota cancelando a devolução, novo prazo e cancelamento explícito |
| `PrefsTest` | padrões, modos, Maps/Waze, valores ausentes/inválidos e limites de atraso, inclinação, fps, padrão da área segura e ajuste fino (negativo, ±400); opções de boot e recuperação |
| `PanelSizeTest` | escolha segura do código de tamanho e geometria |
| `SafeAreaTest` | área segura do Song Plus (230/90/230/230 → x 230–1690, y 90–490), carro em 960×360, cartões e carro fora de todos os desenhos da BYD medidos, foco da câmera no carro, ajuste fino (negativo, limite), margens exageradas, outra resolução, `none`, conteúdo encolhido pelo menu "Pequeno" |
| `PoseSmootherTest` | posição, rumo, limites de previsão e ruído de GPS |
| `FreezeWatchTest` | vigia, orçamento de recuperação e convivência (`YieldPolicy`) |
| `CockpitTest` | Photon, tema e códigos do tempo |
| `HomeChoiceTest` | saída do `resolve-activity` (nosso, BYD, seletor, erro), launcher original, resultado do `set-home-activity`, próximo passo, restauração, estado do alias e quando ligá-lo, alias HOME **desligado** no manifesto e nenhum outro componente com `HOME` |
| `DialerTest` | teclado, limite, limpeza de número colado, mínimo de 3 dígitos, `tel:` com `#`, formato brasileiro, ordem dos caminhos de chamada (inclusive "nenhum app"), conta HFP primeiro e `ACTION_CALL` desviado só de reserva, caminhos diretos, conferência sem nova tentativa, escolha da conta Bluetooth, quem atende o `ACTION_CALL`, pedido de permissão e escolha do app do carro |
| `PanelMenuReaderTest` | `Parcel` do carro (1–4, várias linhas, prévia ASCII, 0xffffd8e5, exceção, lixo), comando fixo só de leitura, SDK primeiro → ADB só com SecurityException e no Android 10, pausa do ADB após falha, `Follower` com leituras do ADB |
| `PanelMenuTest` | valores do menu, constante do SDK x molde, desenho de cada modo, opcional/teste do debug, `Follower` (sem piscar, volta ao normal), só getters, nenhum código novo em `SAFE_COMMANDS` |
| `DebugProbeTest` | registro desligado por padrão e limite, comando de coleta só leitura, seções, `settings`/`getprop`, diff, histórico de broadcasts, candidatos, linha de base e novidades, seções ausentes, esconder número/MAC |
| `ForecastTest` | agora/horas/dias do Open-Meteo, buracos `null`, respostas ruins, rótulos em português, textos, URL com posição arredondada e limite de pedidos |

`PanelAutomation` contém a decisão usada pelo `ClusterService`; as chamadas Android/ADB continuam
no serviço. `kotlinx-coroutines-test` controla o tempo dos testes. Um teste reproduz a restauração
que cancelava o próprio Job antes da pausa entre 18 e 0: ao vencer o prazo, a referência do Job agora
é limpa antes de chamar a restauração, evitando interromper a sequência.

`Prefs` aceita internamente um `SharedPreferences` fornecido pelo teste. Um substituto em memória
via proxy testa os getters reais, padrões e conversões sem inicializar o Android. A persistência do
Android e os setters não são validados por esses testes.

Os exemplos de notificação vêm de `NavSimulator` e `NavParserTest`: **imitam formatos reais**, mas
não são capturas confirmadas no carro. Os testes validam o parse dos quatro campos textuais; não
validam entrega/remoção de notificações pelo sistema nem extração/recoloração do bitmap da seta.
A ativação real via AutoContainer, integração com ADB, ciclo de vida Android e renderização ainda
exigem os roteiros de emulador e o primeiro teste no carro. Esta etapa foi validada somente na JVM.

O CI (`.github/workflows/android.yml`) tem etapa explícita `./gradlew --no-daemon testDebugUnitTest`,
seguida de lint e build debug; os relatórios são publicados mesmo em falha. A execução do workflow
continua desativada por cobrança da conta, conforme `AGENTS.md`; não foi reativada nesta tarefa.
Relatórios locais: `app/build/reports/tests/testDebugUnitTest/` e
`app/build/reports/lint-results-release.html`.

Regra: lógica nova que dá para separar do Android vai para Kotlin puro com teste.
`org.json` real entra só nos testes (`testImplementation("org.json:json")`); no app é o do Android.

## Painel e telemetria — issues #2/#5/#6 (não lançado, 2026-10-23)

`Places.navigate` e `navigateText`, depois de abrir com sucesso o destino no navegador, enviam
`ClusterService.ACTION_ROUTE_REQUEST`. O serviço respeita `cluster_mode` (`manual` não ativa),
limpa a restauração pendente e ativa o painel. Assim a abertura não depende da notificação chegar;
o listener continua sendo necessário para manobras e encerramento automático da navegação.
Sem acesso às notificações, o motorista precisa devolver o painel pelo botão Painel original.
`openApp` continua apenas abrindo o navegador, sem iniciar uma rota ou projeção.

Nova preferência `panel_content`: `integrated` por padrão, `navigation` opcional. No integrado,
`ClusterActivity` mostra mapa/GPS e cartões das notificações; **não há geometria da rota** nessas
notificações. Na opção externa, o controlador resolve a Activity de entrada de Maps/Waze instalado
(lista fechada de pacotes) e usa `am start --display N --windowingMode 1`, preservando a tarefa/rota
já iniciada. Nunca escolhe display 0 como destino de projeção. `PanelContent`, Kotlin puro,
confirma no `dumpsys activity activities` a Activity retomada no display esperado; retorno OK do
shell sozinho não prova projeção. Dump desconhecido resulta em falha visível, sem afirmar sucesso.
Essa opção é experimental: a firmware ou o app pode impedir a movimentação da tarefa.

A escolha é aplicada na ativação; configure antes de ativar. Durante projeção externa, o vigia e os
avisos de cobertura de `ClusterActivity` são ignorados para não abrir o mapa próprio sobre o navegador.
Não há vigia/controle de ciclo de vida do app externo. Restaurar mantém a sequência segura 18 → 0;
não força encerramento nem apaga a rota de outro app. Retorno da tarefa externa à central e conservação
da rota são pontos de teste no carro, sem garantia nesta etapa. Sem novos comandos AutoContainer.

`VehicleReadings` concentra validação numérica e classificação de falhas de reflexão, sem Android.
`VehicleData` consulta os mesmos cinco getters estatísticos documentados no SDK, mantém apenas
valores finitos nos intervalos aceitos e preenche `Snapshot.error` com falhas por método. Valores zero
são válidos; indisponibilidade não vira zero. O dispositivo é reutilizado, mas cada leitura tenta
novamente os getters, permitindo recuperação após concessão de permissões. Falha de inicialização é
reavaliada na próxima leitura. O log só registra mudanças de erro, sem repetir a cada atualização.

`Snapshot.diagnosticText` mostra cada campo ausente como indisponível. Diagnóstico → Dados do carro
usa esse texto; Info da central também o inclui e informa a permissão `BYDAUTO_STATISTIC_GET` real.
A tela inicial existente já avisa Dados do carro indisponíveis; não foi alterada nesta tarefa.
Portas/travas não eram consultadas e continuam explicitamente indisponíveis. `getDoorState(int)`
exige constantes confirmadas; trava não é sinônimo de porta fechada. Não foram adicionados nomes
alternativos de getters nem propriedades `getprop` sem evidência. AutoContainer controla projeção,
não fornece estes dados. Mesmo com `pm grant`, o SDK/Binder BYD pode recusar o uid comum;
o ajudante uid 2000 somente leitura/autenticado permanece como etapa separada.

Regressões novas em `PanelContentTest`, `VehicleReadingsTest` e `PrefsTest`. Validam política,
parse de confirmação, ausência/invalidade, falhas parciais e diagnóstico; não validam Binder, tarefas
Android, GPU ou dados reais do carro. Sem emulador nesta etapa; captura das preferências pendente.

Validação desta alteração: **87 testes JVM, 0 falhas** (14 novos), `assembleDebug` concluído,
`lintDebug` com **0 erros e 105 avisos**. Relatórios em `app/build/reports/tests/testDebugUnitTest/`
e `app/build/reports/lint-results-debug.html`. Ambiente JDK/SDK indicado pelo dono; para contornar
cache/chave debug fora da área gravável, `GRADLE_USER_HOME=/tmp/painel-gradle` e
`ANDROID_USER_HOME=/tmp/painel-cluster-android`, com `--no-daemon` no build/lint. APK debug usa chave
temporária; nenhuma alteração em keystore/local.properties/configuração de assinatura release.

## Rota nativa opcional — issue #14 (não lançado)

`nav_app=native` acrescenta **Painel Livre** aos botões da aba Navegação e aos Ajustes;
`maps` continua sendo o padrão e `waze` continua disponível. `Places.navigate/navigateText`
encaminham o destino ao serviço por `ACTION_NATIVE_ROUTE`, sem abrir app externo.
`nav/native/NativeNavigation` acompanha o GPS no escopo do `ClusterService`; não depende de
Activity visível nem de acesso às notificações. A geometria fica em um `StateFlow` próprio;
a orientação usa `NavRepository` com fonte `br.painellivre`. Notificações externas não substituem
uma orientação nativa ativa. Abrir uma rota externa com sucesso encerra a sessão nativa.

O cálculo usa a API HTTPS do OSRM público (`router.project-osrm.org`), perfil driving, `steps=true`,
`geometries=geojson`, `overview=full`: envia **coordenadas precisas de origem e destino**, sem
identificador pessoal/chave. Busca textual continua no Photon; se houver ambiguidade, é necessário
escolher o resultado na busca. Serviço público, sem garantia de disponibilidade, trânsito ou rotas
atualizadas. Para uso diário avaliar servidor próprio/provedor; não há modo offline nem voz.
Referência do protocolo: https://project-osrm.org/docs/v5.24.0/api/ (Route/RouteStep/StepManeuver).
Não usa Ferrostar nesta primeira implementação: o acompanhamento simples está em Kotlin puro.

`NativeRoute.kt` contém parser, coordenadas validadas, manobras em português e `RouteTracker`.
Projeta o GPS nos segmentos e calcula distância até a próxima manobra e restante. O avanço é
monótono, limitado a uma janela de 1500 m à frente; fixes a mais de 60 m não avançam a orientação.
Recálculo exige desvio contínuo por 8 s e pelo menos 30 s desde o último pedido; o recálculo não é disparado enquanto outro cálculo está ativo.
Trocar destino cancela o Job e impede publicar resposta antiga; a conexão HTTP cancelada pode
levar até o timeout para terminar. Fixes com mais de 60 s ou precisão pior que 50 m são ignorados. GPS preciso/fresco
é exigido também para iniciar. Chegada: menos de 30 m ao longo da linha e 40 m do destino.
São heurísticas, **não map matching completo**: cruzamentos, túneis, pistas paralelas e retornos
precisam de validação. Tempo estimado vem das durações OSRM, proporcional ao trecho restante;
a chegada usa o relógio local, sem trânsito em tempo real.

`NativeRouteLayer` desenha a mesma linha azul no mapa da central e do painel, reaplicada após
carregamento/troca do estilo. Os cartões existentes mostram a seta, distância, rua e chegada.
A rota nativa ativa força conteúdo integrado mesmo com a opção experimental externa selecionada;
`PanelContent` cobre essa prioridade. A abertura segue `ACTION_ROUTE_REQUEST` e respeita o modo
manual; o serviço elimina ativação duplicada quando o estado e o pedido explícito chegam juntos.
Nenhum comando AutoContainer novo. Rota nativa termina na chegada ou por toque no cartão de rota
ativa na aba Navegação → Encerrar. A devolução mantém o prazo configurado. A sessão é só em memória:
morte do processo/serviço exige escolher o destino novamente; recriar apenas a Activity preserva a rota.

`RouteIntentActivity` recebe `ACTION_SEND text/plain` e `ACTION_VIEW geo:/google.navigation:`.
Mostra destino e escolha nativo/Maps/Waze antes de abrir. Texto para o nativo abre a busca visível;
coordenadas seguem diretamente após a escolha. Reconhece URLs longas de Maps/Waze com query de
destino; **não expande links curtos** (Maps/Waze podem compartilhar só um link desses), não faz
requisições a hosts arbitrários. Nesses casos orienta buscar/compartilhar coordenadas.

Regressões em `NativeRouteTest`: entradas, URLs, coordenadas inválidas, OSRM, rotatória, próxima
manobra, avanço/distância/chegada, desvio e prioridade nativo/externo no painel. O cálculo e as
regras de projeção não envolvem SDK/CAN. #9 e #15 continuam no branch separado de comandos/debug.

Validação final da #14: `testDebugUnitTest` **129 testes, 0 falhas** (12 novos), `lintRelease`
**0 erros, 109 avisos**, `assembleDebug` concluído, JDK 17/SDK indicados pelo dono. Consulta pública
OSRM de exemplo (Berlim, sem coordenadas do dono) retornou `Ok`. Sem emulador/carro; não comprova
lifecycle, renderização, entrega de intents nem compatibilidade da firmware. Capturas pendentes.

## Menu do painel e modo de debug — issue #9 (não lançado, 2026-10-23)

**Seguir o menu** (`panel_menu_follow`, desligado). Enquanto a `ClusterActivity` está visível,
`PanelMenuWatcher` lê a cada 1 s (`PanelMenuReader`, ver abaixo) primeiro `InstrumentSdk.naviType`: `BYDAutoInstrumentDevice.getInstance`,
`getDevicetype()` (padrão 1007), a constante `BYDAutoFeatureIds$Instrument.INSTRUMENT_NAVI_TYPE` do
próprio carro (se não for 0; senão 0x40C03032, medido pelo DiPlay na DiLink 5.0) e o getter genérico
`get(int, int)`. O `Follower` só troca de modo depois de 2 leituras iguais e volta ao normal depois
de 3 falhas seguidas. `PanelMenuFrame` envolve o conteúdo da tela (o `FrameLayout` com mapa e cartões):

| Modo | Desenho |
|---|---|
| Cheio, desconhecido, erro, opção desligada | como sempre |
| Pequeno | conteúdo com margens de 28% dos lados e 12% em cima/embaixo (hipótese: o painel mostra o meio) |
| Simples | preto no lugar do mapa, cartões da manobra e da chegada, sem o carro |
| OFF | tudo preto |

O conteúdo muda de tamanho, então a própria tela recalcula cartões e câmera como numa mudança de
display. O mapa continua desenhando por baixo do preto (o vigia não confunde com travamento). Nenhum
comando vai ao AutoContainer: os códigos 17 (meia tela) e 39 (navegação simples) seguem fora de
`SAFE_COMMANDS`.

**Leitura pelo ADB local (v0.5.1).** No carro (DiLink 3.0, Android 10) o SDK recusa no processo do app
(SecurityException: as `BYDAUTO_*_GET` não saem por `pm grant`). Então o `PanelMenuReader` (Kotlin puro)
tenta o SDK uma vez e, só se ele recusar **por permissão** e o Android for o 10 (`PanelMenu.ADB_SDK_INT`),
passa a ler pelo mesmo `SelfAdb` do `sendInfo` com um único comando fixo,
`PanelMenu.ADB_READ_COMMAND` = `service call autoservice 5 i32 1007 i32 1086337074` (transação 5 =
`getInt`, conferida no carro). `PanelMenu.parseParcel` lê `Result: Parcel(00000000 0000000N …)` (também
no formato de várias linhas com endereço), ignora a prévia entre aspas, recusa exceção do Binder
(primeira palavra ≠ 0) e trata 0xffffd8e5 como indisponível. Valores confirmados no carro: 1 OFF,
2 Simples, 3 Pequeno, 4 Cheio. Se o ADB falhar, nova tentativa em 5 s. É a exceção documentada à regra
"nunca `service call autoservice <n>`": só leitura, comando constante, nada de transação 6
(`set`)/`INSTRUMENT_NAVI_TYPE_SET`. O modo de debug usa a mesma leitura (`PanelMenuWatcher.read`) e
cada mudança vai ao `AppLog` e ao registro do debug como `ADB: bruto=2 (0x00000002) → Simples`.

**Modo de debug** (`debug_mode`, desligado). `App.onCreate` e a chave em Ajustes chamam
`DebugProbe.sync`. Ligado, a cada 3 s: getters candidatos do SDK do painel (`PanelMenu.DEBUG_GETTERS`,
só `get…` sem parâmetro) e `INSTRUMENT_NAVI_TYPE`; pelo ADB local um comando só de leitura
(`ProbeParse.COLLECT_COMMAND`: `settings list` ×3, `getprop`, resumo de `dumpsys activity broadcasts
history`, `logcat -d` filtrado) cujo resultado passa pelo `ProbeTracker` (a primeira coleta vira linha
de base; depois só o que mudou, com ★ nos candidatos). Receptores para as ações já documentadas
(`AUTONAVI_STANDARD_BROADCAST_*`, `byd.intent.action.*`, `com.telenav.action.nav.status`) e as
digitadas pelo motorista mostram os extras. Sem ADB, só o SDK continua e o ADB é tentado a cada 30 s;
a coleta pelo ADB para em 30 min (Retomar na tela). Registro separado do `AppLog` (`DebugLog`), também
usado pelo telefone (issue #15). A fotografia completa esconde números de telefone e MACs.

### Revisão local da v0.5.1 (2026-10-23)

`PanelMenu.parseParcel` exige fechamento do Parcel, aceita apenas palavras hexadecimais na parte de
valores e ignora a prévia ASCII. Nunca procura o valor depois do fechamento. `PanelMenuReader` só usa
saída de shell com exitCode 0 e aplica a pausa de 5 s a erros de conexão, shell, Binder e parser.
Leituras fora de 1..4 produzem UNKNOWN; o Follower volta ao desenho normal após as falhas previstas.
O único comando autoservice executável continua a constante de leitura da transação 5, após recusa
por permissão do SDK e somente na API 29. Não há transação 6 nem escrita CAN/SDK.

`SafeArea.resolve` usa intermediários Long/Double para margens extremas e reserva ao menos um pixel
em displays mínimos. `SafeArea.plan` limita a unidade dos cartões também pela largura segura, para
que o mínimo do cartão não alcance o carro em áreas estreitas. O padrão 1920×720 não muda.
As chaves antigas inset_* são intencionalmente reinterpretadas como ajuste aditivo, limitado a ±400;
não há conversão de margens absolutas nem gravação automática das preferências antigas.
