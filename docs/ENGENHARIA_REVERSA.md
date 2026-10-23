# Engenharia reversa — mapas no painel do BYD (DiLink 3)

Data: 2026-10-22 (atualizado com a v0.3.0). Alvo do usuário: BYD Song Plus DM-i 2025, DiLink 3, multimídia 13.1.33, sem mapas de
fábrica. Objetivo: entender como a navegação de fábrica e o DashCast chegam ao painel e construir uma
alternativa aberta ao SealNav.

Legenda: **[observado]** = visto no binário/código; **[inferido]** = conclusão a partir do observado;
**[aberto]** = não verificado (precisa do carro).

## 1. Artefatos analisados

| Artefato | Identidade | Assinatura |
|---|---|---|
| `BYD_Navigator_7.2.62.6.45738_Alx.apk` | `com.telenav.app.arp` 7.2.62.6.45738-G_TN-P (vc 710625738), minSdk 23, target 28, SHA-256 `25a810ee…72f3`, 5 DEX, 32 312 classes, libs só arm64 | Certificado BYD `CN=auto_api, emailAddress=auto_api@byd.com`, emissor `BYDAUTO-Co.Ltd-H-DCA` |
| `DashCast-v1.9.55-beta-release.apk` | `com.byd.dashcast` 1.9.55-beta (vc 694), minSdk 28, target 29, SHA-256 `72e1a745…a8f`, 3 966 classes | **Chave "platform" de teste pública do AOSP** (SHA-1 `27:19:6E:38:6B:87:5E:76:AD:F7:00:E7:EA:84:E4:C6:EE:E3:3D:FA`) |
| Código do DashCast | github.com/Kiroha/byd-dashcast, commit `99add71` (2026-09-03), MIT | — |

Ferramentas: REA (`open_binary` + `inspect_artifact`: inventário completo de 11 652 entradas do Navigator,
evidência `ev_84698ba1…` / resumo `ev_29d4a033…`, inventário `ev_cf3e1d8e…`). As ferramentas Android do
REA (JADX/apktool) não estavam disponíveis nesta máquina (sem `REA_JADX_MCP_JAR`, sem apktool), então o
manifesto (AXML) e as tabelas DEX (strings, classes, chamadas e strings por método) foram extraídos com
parsers próprios em Python — hoje em `tools/re/` (uso em `docs/AMBIENTE.md`). Limite: sem descompilação
de corpo de método — fluxos foram inferidos pelas chamadas e constantes de cada método. Depois, o JADX
1.5.6 foi instalado e usado no estudo do SealNav (`docs/SEALNAV_RE.md`).

## 2. BYD Navigator (Telenav)

**Integração com o sistema BYD [observado]:**
- Serviço exportado `com.telenav.arp.speech.SpeechNaviService` implementa `com.byd.naviserviceapi.INaviService`
  — API do **assistente de voz** da BYD (planejar rota, buscar POI, zoom, casa/trabalho, callbacks de estado
  do mapa). Não é o canal do painel.
- Serviço `com.telenav.arp.service.NavigationService` (ação `com.telenav.arp.service.NAVIGATION`) com
  `INavigationService`, que inclui `getClusterTurnImage` / `getClusterMapImage`.
- `IClusterMapServicePrivate` (`createMap(Surface, Bundle{width,height,density}, Messenger)`,
  `updateSurface`, `pauseMap`, `resumeMap`, `destroyMap`): o lado do painel entrega uma `Surface` e o app
  desenha o mapa nela. **Nesta build não há subclasse concreta de `BaseClusterMapServicePrivate` nem serviço
  declarado no manifesto** → o mapa no painel não está ligado neste APK [inferido].
- Broadcasts públicos de turn-by-turn (`ExternalNotificationPerformer`):
  - `com.telenav.action.maneuver.status` — `SUB_ACTION` = `KEY_MANEUVER_START` / `KEY_MANERVER_UPDATE`
    (grafia original) / `KEY_MANEUVER_END`; `ACTION_INFO` = Bundle{`EXT_STRING_STREET_NAME`,
    `EXT_STRING_DISTANCE`, `EXT_BITMAP_ICON`}.
  - `com.telenav.action.nav.status` (`KEY_NAV_START`/`KEY_NAV_STOP`),
    `com.telenav.action.nav.app.status` (`KEY_APP_GO_FOREGROUND`/`BACKGROUND`).
  - `byd.intent.action.NAVIGATION_STATE_CHANGED` e `byd.intent.action.MAP_ACTIVITY_STATE_CHANGED`
    (extras `packageName`, `state`).
- SDK BYD (`android.hardware.bydauto.*`, embutido só como *stubs*): o Navigator **só lê** (unidade de
  velocidade, VIN, modelo, autonomia elétrica, carregamento). Nenhuma chamada a
  `BYDAutoInstrumentDevice.send*` → esta build não escreve setas no CAN [observado].
- Licença: ativação por "virtual VIN" (`android.broadcast.virtual_vin_action` com `supplier`,
  `virtual_code`, `active_state`, `license_timeout`, `active_country`…). O app depende de ativação
  vinculada ao carro e de serviços da Telenav. **Não foi contornado** — por isso o Painel Livre é um app
  próprio e não um Navigator reempacotado.

**API do painel no SDK BYD (stubs) [observado]:** `BYDAutoInstrumentDevice.sendSimpleGuidanceInfo(icon, dist)`,
`sendNextPathName(String)`, `sendRestRouteInfo(h, min, metros)`, `sendAutoNaviStatus(int)`,
`sendCameraGuidanceInfo`, `sendSafeGuidanceInfo`, `sendDestinationSetStatus`, `sendAddressInfo`.
Leitura usada no launcher: `BYDAutoStatisticDevice.getElecPercentageValue()`, `getElecDrivingRangeValue()`,
`getFuelPercentageValue()`, `getFuelDrivingRangeValue()`, `getTotalMileageValue()`.

## 3. DashCast — como chega ao painel

**Projeção (DiLink 3 dual-OS "1 for 2") [observado no código, confirmado pelo autor em carros]:**
- Privilégio: o app conecta no adbd local (`127.0.0.1:5555`, biblioteca `dadb`) e roda comandos como uid 2000.
- Serviço Binder `AutoContainer` (`auto_container` em algumas DiLink 5), transação 2 =
  `sendInfo(type=1000, info, "")`:
  - `16` liga a projeção em tela cheia; `35` faz o Qt criar o VirtualDisplay; `18` encerra; `0` recarrega o
    vídeo nativo (sempre depois do 18);
  - `29/30/31` trocam o formato do painel (8,8"/12,3"/10,25") — o código errado **já deixou painéis de
    8,8" em "modo simples"**; `1` desconecta o Qt e destrói o display. O Painel Livre **nunca** manda o
    `1` e só manda 29/30/31 pela regra do `PanelSize` (ver `AGENTS.md` §5).
  - O DashCast liga com 16 → 3 s → 35 e não manda código de tamanho. O SealNav manda
    [limpeza 18 → 0] → tamanho → 1 s → 16 → 1 s → 35 (detalhes em `docs/SEALNAV_RE.md`, achados B–D);
    o Painel Livre segue o do SealNav desde a v0.2.0.
- Display do painel: `fission_bg_xdjaVirtualSurface` (DiLink 3/4) ou `…XDJAScreenProjection…` (DiLink 5),
  1920×720, dono `com.xdja.containerservice` (uid 1000). Na DiLink 5.1 a navegação oficial da BYD
  (AmapService) põe o mapa no painel abrindo uma Activity com `setLaunchDisplayId` no display
  `shared_fission_bg_XDJAScreenProjection_0`.
- `ro.build.system.fission_single_os=1` → central **single-OS**: não existe display projetável (o SealNav
  relata isso em alguns Song Plus brasileiros com 13.1.32). O seu carro projetou com DashCast e SealNav,
  então é dual-OS [observado pelo usuário].

**Conforto (clima, vidros, bancos, porta-malas) [observado nos moldes do SDK embutidos no BYD Navigator]**
— só assinaturas (nome + tipos), sem o significado dos parâmetros; para a Fase 4b do `PLANO.md`:

| Classe | Leitura | Escrita |
|---|---|---|
| `ac.BYDAutoAcDevice` | `getAcStartState()`, `getTemprature(int)`, `getAcWindLevel()`, `getAcWindMode()`, `getAcCycleMode()`, `getAcDefrostState(int)`, `getAcMaxCoolingState()`, `getAcControlMode()`, `getAcCompressorMode()` | `setAcTemperature(int,int,int,int)`, `setAcWindLevel(int,int)`, `setAcWindMode(int,int)`, `setAcCycleMode(int,int)`, `setAcDefrostState(int,int,int)`, `setAcMaxCoolingState(int)`, `setAcControlMode(int,int)`, `setAcCompressorMode(int,int)`, `setAcVentilationState(int,int)` |
| `bodywork.BYDAutoBodyworkDevice` | `getWindowOpenPercent(int)`, `getWindowState(int)`, `getWindowPermitState()`, `getSunroofPosition()`, `getDoorState(int)` | `setBodyWindowCtrlState(int,int)`, `setAllWindowState(int,int,int,int)`, `setMoonRoofState(int)`, `setSunshadeState(int)` |
| `setting.BYDAutoSettingDevice` | `getSeatHeatingState(int)`, `getSeatVentilatingState(int)`, `getBackDoorElectricMode()`, `getBackDoorOpenedHeight()` | `setSeatHeatingState(int,int)`, `setSeatVentilatingState(int,int)`, `setBackDoorElectricMode(int)`, `setBackDoorOpenedHeight(int)` |

Todos retornam `int` (provável código de resultado). Como no HUD, a escrita exige o processo uid 2000
com contexto que concede as permissões (achado I do `SEALNAV_RE.md`).

**Setas no HUD/painel via CAN [observado no código DashCast]:** `BYDAutoInstrumentDevice.set(int[], BYDAutoEventValue)`
por reflexão dentro de um processo uid 2000 com contexto que concede todas as permissões (o SDK recusa
mesmo app assinado com chave de plataforma). Registradores:

| Registrador | ID | Uso |
|---|---|---|
| `INSTRUMENT_SEND_NAVI_STATUS` | 0x43E0003A | 2 = ativa, 4 = parada |
| `INSTRUMENT_GUIDE_SIMPLE` | 0x43F01010 | ícone da manobra |
| `INSTRUMENT_FRONT_CROSSING_DIST` | 0x43F01018 | distância (m) |
| `INSTRUMENT_NEXT_PATHNAME` | 0x43FA1008 | rua, UTF-16LE sem BOM |
| `INSTRUMENT_NAVI_MILEAGE` / `_HOUR` / `_MINUTE` | 0x43F02028 / 0x43F02010 / 0x43F02018 | restante |
| `INSTRUMENT_EXPECTED_ARRIVE_*` | 0x43F09010…28 | relógio de chegada |
| `SETTING_NAVI_SCREEN_STATUS` (SettingDevice) | 0x4C10E015 | 3 no início da rota |

Ícones: esquerda 1, direita 2, levemente 3/5, acentuada 7/8, retorno 9/10, reto 11, rotatórias 15–44,
destino 48 (tabela em `Maneuver.bydCanIcon`). **Não implementado no Painel Livre ainda** (Fase 4).

**Classes do SDK BYD presentes nos moldes do BYD Navigator [observado]** (`android.hardware.bydauto.<pacote>.BYDAuto<Nome>Device`):
`ac.Ac`, `adas.ADAS`, `audio.Audio`, `auxiliary.Aux`, `bigdata.BigData`, `bodywork.Bodywork`,
`charging.Charging`, `collision.Collision`, `cputemprature.CpuTemprature`, `doorlock.DoorLock`,
`doormirror.RearViewMirror`, `dtc.Dtc`, `energy.Energy`, `engine.Engine`, `funcnotice.FuncNotice`,
`gb.GB`, `gearbox.Gearbox`, `instrument.Instrument`, `light.Light`, `location.Location`, `motor.Motor`,
`mqtt.Mqtt`, `multimedia.Multimedia`, `ota.Ota`, `panorama.Panorama`, `phone.Phone`, `power.Power`,
`qcfs.Qcfs`, `radar.Radar`, `radio.Radio`, `reminder.Reminder`, `rescue.Rescue`, `rse.RSE`,
`safetybelt.SafetyBelt`, `security.Security`, `sensor.Sensor`, `setting.Setting`, `signal.Signal`,
`special.Special`, `speed.Speed`, `statistic.Statistic` (todas com `getInstance(Context)`,
`registerListener`, `hasFeature`, `getFeatureList`). Para listar métodos de uma delas:
`python3 tools/re/dexdump.py BYD_Navigator.apk nav/` e `python3 tools/re/q.py nav class BYDAutoAcDevice`.

**Permissões de leitura** que o Painel Livre declara e concede pelo ADB (as mesmas que o Navigator
declara): `android.permission.BYDAUTO_STATISTIC_GET`, `_BODYWORK_GET`, `_CHARGING_GET`, `_SPEED_GET`,
`_INSTRUMENT_GET`. A escrita não depende de permissão de app: o SDK recusa até app com chave de
plataforma, por isso o caminho é o processo uid 2000 com contexto que concede tudo.

**Identificação do carro** [observado no SealNav]: `ro.product.name` + `Build.MODEL/PRODUCT/DEVICE/FINGERPRINT`
(o SealNav procura "song", "seal u", "atto", "dolphin"… nesse texto). Outras propriedades lidas pelo
Diagnóstico ou pelo SealNav: `ro.build.system.fission_single_os`, `apps.setting.product.inswver` (versão do MCU do
painel), `persist.sys.car.type`, `ro.build.dilink.version`/`ro.dilink.version`, `sys.screen.current.mode`.

O APK 1.9.55 traz ainda `assets/cluster_live/` (Qt `.rcc` com 84 arquivos de idioma modificados do painel) —
fora do escopo; o Painel Livre não altera arquivos do painel.

## 4. Decisões do Painel Livre

| Pergunta | Resposta | Base |
|---|---|---|
| Dá para instalar o Navigator da BYD no carro? | Instalar sim (assinatura BYD intacta), mas ele depende de ativação por VIN/Telenav e não desenha no painel nesta build | §2 [observado/inferido] |
| Como pôr mapa no painel? | ADB local → `AutoContainer` 16/35 → Activity no display `fission…` | §3 [observado] |
| De onde vem a orientação? | Notificação contínua do Google Maps/Waze (seta = largeIcon) | escolha do usuário; padrão DashCast/OpenBYD |
| Mapa do painel | MapLibre + OpenFreeMap (OSM, sem chave), GPS da central | escolha de projeto |
| Código de tamanho do painel | só pela regra do `PanelSize` (automático: 30 para Song Plus/Seal U com painel medido de 12,3") | §3 + `docs/SEALNAV_RE.md` D |
| Interface | igual à do SealNav (cockpit + painel), visual próprio | pedido do dono; `docs/SEALNAV_RE.md` "Interface" |
| HUD/CAN | documentado, não implementado | Fase 4; precisa de ajudante uid 2000 |
| Conforto (clima, bancos, vidros, porta-malas) | assinaturas levantadas, não implementado | Fase 4b, com as regras de segurança do `docs/PLANO.md` |

## 5. Itens em aberto (precisam do carro)

1. Confirmar no Diagnóstico: `fission_single_os`, `inswver`, nome do display, identificação do modelo
   (o automático do código de tamanho depende de reconhecer "song plus") e se a abertura pelo shell em
   tela cheia funciona.
2. Se os cartões do painel ficam livres dos instrumentos da BYD (as fotos públicas do SealNav no Seal U
   indicam faixa de cima e de baixo ocupadas; ajustar margens se preciso).
3. Formato exato das notificações do Google Maps/Waze em pt-BR na versão instalada (o parser cobre os
   formatos conhecidos; o Diagnóstico mostra o que foi lido).
4. Onde a BYD guarda a lista de "autoiniciar" (o SealNav liga por acessibilidade; preferimos um ajuste
   pelo ADB ou instrução ao motorista).
5. Para a Fase 4b: assinaturas e constantes reais em `/system/framework/bydauto*.jar` (só leitura,
   `adb pull`) e o mapeamento de cada botão de conforto.
