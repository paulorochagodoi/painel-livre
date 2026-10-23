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
| | `PanelMenu.kt` | **Kotlin puro.** Menu de navegação do painel (issue #9): valor → modo (1 OFF, 2 Simples, 3 Pequeno, 4 Cheio), desenho de cada modo, `Follower` (2 leituras iguais, volta ao normal após falhas), só getters no debug |
| | `PanelMenuWatcher.kt` | Lê o modo enquanto a tela do painel está aberta (se "Seguir o menu" estiver ligado) e `PanelMenuFrame`, a moldura que reposiciona/cobre o conteúdo da `ClusterActivity` |
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
| `panel_menu_follow` | `false` | seguir o menu de navegação do painel (issue #9, experimental) |
| `debug_mode` | `false` | modo de debug (issue #9) |
| `debug_actions` | — | ações de broadcast extras que o debug escuta |
| internas | — | `projection_powered`, `panel_geometry`, `last_boot_at`, `autocontainer_service_name`, `original_home_component`, `place_home`, `place_work`, `place_recents` |

## Testes

`app/src/test` tem **143 testes JVM** (117 até a v0.4.1 + 21 da issue #9 + 5 da issue #15), sem carro, emulador,
rede ou espera de tempo real. Execute com JDK 17:

```bash
./gradlew testDebugUnitTest
./gradlew lintRelease
```

| Suíte | Cobertura |
|---|---|
| `NavParserTest` e `NavRegressionTest` | Maps/Waze: manobra (seta de reserva), distância, rua, instrução, resumo, chegada, texto expandido/repetido, prioridade do título, espaços Unicode, inglês/unidades imperiais e ruído |
| `PanelAutomationTest` | modos `nav`/`always`/`manual`, painel já ativo, prazo exato, atraso zero/600 s, nova rota cancelando a devolução, novo prazo e cancelamento explícito |
| `PrefsTest` | padrões, modos, Maps/Waze, valores ausentes/inválidos e limites de atraso, inclinação, fps e quatro margens; opções de boot e recuperação |
| `PanelSizeTest` | escolha segura do código de tamanho e geometria |
| `PoseSmootherTest` | posição, rumo, limites de previsão e ruído de GPS |
| `FreezeWatchTest` | vigia, orçamento de recuperação e convivência (`YieldPolicy`) |
| `CockpitTest` | Photon, tema e códigos do tempo |
| `HomeChoiceTest` | saída do `resolve-activity` (nosso, BYD, seletor, erro), launcher original, resultado do `set-home-activity`, próximo passo, restauração, estado do alias e quando ligá-lo, alias HOME **desligado** no manifesto e nenhum outro componente com `HOME` |
| `DialerTest` | teclado, limite, limpeza de número colado, mínimo de 3 dígitos, `tel:` com `#`, formato brasileiro, ordem dos caminhos de chamada (inclusive "nenhum app"), conta HFP primeiro e `ACTION_CALL` desviado só de reserva, caminhos diretos, conferência sem nova tentativa, escolha da conta Bluetooth, quem atende o `ACTION_CALL`, pedido de permissão e escolha do app do carro |
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

## Menu do painel e modo de debug — issue #9 (não lançado, 2026-10-23)

**Seguir o menu** (`panel_menu_follow`, desligado). Enquanto a `ClusterActivity` está visível,
`PanelMenuWatcher` lê a cada 1,5 s `InstrumentSdk.naviType`: `BYDAutoInstrumentDevice.getInstance`,
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
`SAFE_COMMANDS`. Não usamos `service call autoservice 5 …` porque o número da transação depende da
firmware.

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
