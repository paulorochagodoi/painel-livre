# Histórico de versões

Os APKs de release são assinados com a chave local do projeto (`CN=Painel Livre, O=Uso pessoal, C=BR`,
certificado SHA-256 `7290204a…13d9`) e instalam um por cima do outro. Eles não ficam no repositório.
A v0.4.1 foi a primeira instalada e testada no carro (23/10/2026); as demais validações foram no emulador (`docs/AMBIENTE.md`).

## Não lançado — 0.5.5 (versionCode 11), issues #20 #21 #22 (29/10/2026)

Branch `fix/ac-audio-tela-cheia`: Claude Code (#20 clima + #22 tela cheia) + Codex (#21 toast de áudio),
com ai-memory nas duas sessões. Pré-release de teste; **não mesclar na main sem ok do Paulo**.

Correções pedidas pelo Paulo depois da foto no carro da 0.5.4; versão ainda em 0.5.4
(`versionCode` 10) — o bump para 0.5.5/11 fica para depois do merge com a correção do Codex
(issue #21, áudio/rádio, branch separada).

- **Issue #20 — climatização sem diálogo:** a causa raiz não era o flag de confirmação (já
  removido em 24/10, ver 0.5.4 abaixo) e sim `chooseCommand`, a função chamada por **todos** os
  controles de clima: ela sempre abre um `AlertDialog.setItems` com a lista de valores possíveis,
  mesmo quando a lista tem só dois itens (Ligar/Desligar). AUTO, A/C, recirculação, desembaçador e
  a energia geral (`ac_on`) agora chamam `toggleClimate`: um toque lê o estado atual
  (`CarReadouts.climate`) e manda o oposto, sem diálogo. A ventilação (`ac_fan`) ganhou botões
  +/- (`fanDown`/`fanUp` em `pop_climate.xml`) que chamam `stepFan`, subindo/descendo um nível
  (0–7) sem a lista modal. A temperatura (16–30°, 15 valores) continua pela lista: não é um
  liga/desliga nem cabe num único passo, e não estava nos critérios de aceite da issue.
  Lógica pura nova e testável na JVM: `CarCommands.toggleValue`/`CarCommands.fanStep`.
- **Issue #22 — tela cheia:** `Theme.PainelLivre.Aura` não tinha `android:windowFullscreen`,
  então o Android reservava a barra de status para o sistema e a Home não ocupava a tela toda da
  central. Acrescentado `windowFullscreen=true` (mesma técnica já usada no painel,
  `Theme.PainelLivre.Cluster`): a barra nativa inferior do carro (fora da nossa janela) continua
  aparecendo, só a faixa de cima (que era do sistema) passa a ser nossa. Afeta também
  `DialerActivity`/`WeatherActivity`, que usam o mesmo tema.
- Testes novos: `CarCommandsTest.toggleAndFanStepArePureAndClamped`,
  `CarCommandsTest.modosDeArEVentilacaoViramToqueUnicoSemDialogoDeEscolha`,
  `AuraTest.homeThemeReclaimsTheStatusBarToFillTheScreen` (261 testes no total, de 258 na 0.5.4).
- Nenhum acesso ao carro nesta etapa; validação só local (`./gradlew testDebugUnitTest lintDebug
  assembleDebug`, zero falhas, lint zero erros). Teste físico de ambas as correções continua
  pendente — ver `docs/PLANO.md`.

- Aura, mídia/rádio: removidos os Toasts de releitura de sessão/volume e de ausência de
  confirmação de sessão após tocar/pausar, próxima, anterior e volume. Ícones e volume continuam
  atualizados. Rejeições e falhas reais continuam visíveis e registradas no Log do app.
- Comandos de mídia não alteram mais o texto de estado da janela de climatização.
- Regressão: `CommandFeedbackTest` cobre silêncio dos retornos rotineiros, preservação de erros,
  comportamento de clima e integração da política na `LauncherActivity`.

Regressão confirmada contra a Activity original (1 falha esperada em 4 testes direcionados).
Validação: 262 testes JVM debug (4 novos), zero falhas; `lintRelease` sem erros (80 avisos);
`assembleDebug` concluído. Emulador isolado: volume 5 → 6 → 5 e play sem sessão, sem Toast
rotineiro; próxima/anterior ficam desabilitados sem sessão. Captura em
`docs/telas/aura-054-audio-sem-toast.png`. Rádio BYD e sessão real pendentes no carro.

## [0.5.4] — 2026-10-24 (teste)

- Painel: cartões compactos na coluna x 170–760/y 185–520 (referência 1920×720),
  escalados pela resolução para evitar o topo, a base e as bordas curvas da BYD.
  Foco do mapa em y 380. Área segura continua opcional, desligada e com cálculo anterior.
- Seletor do mapa: a causa encontrada era o bloqueio de leitura por `panel_menu_follow=false`
  no código e no XML, herdado da implementação experimental (#9). Ausência da chave agora
  segue o painel; migração única também liga o false antigo, indistinguível do padrão XML.
  Desligamentos posteriores são respeitados. Ajustes oferece Seguir/Cheio/Pequeno/Simples/OFF,
  com prioridade debug > manual > leitura estável. Moldura recalculada após medir a tela,
  Pequeno entre os mostradores; leitura bem-sucedida e desenhos no AppLog sem exigir debug,
  falhas uma vez por mudança. SDK negado continua usando somente o ADB aprovado no Android 10.
- Comandos: toque no valor envia sem confirmação extra. Migração única liga chave geral e
  funções com evidência, inclusive em atualização; desligamentos posteriores são respeitados.
  Hipóteses seguem bloqueadas. Lista fechada, limites, 1 envio/s, P para partes móveis,
  log e releitura preservados. Nenhum acesso ao carro nesta etapa.

Validação local: `./gradlew test lintDebug assembleDebug`, 258 testes por variante (debug/release),
zero falhas, lint zero erros e 81 avisos. Capturas `aura-054-*` em `docs/telas/`, emulador 5580;
modos Cheio/Pequeno/Simples/OFF e climatização. Conferência física ainda pendente.

## [0.5.3] — 2026-10-23 (teste) — interface Aura na central e no painel

- Rodada 4: “Área segura no painel” agora é opcional e desligada por padrão, inclusive com
  margens antigas salvas. Desligada usa todo o conteúdo do menu; ligada preserva o cálculo anterior.
  Aplicação imediata, sem reiniciar. Aviso de telemetria acompanha o canto superior direito nos dois
  layouts; rota, debug e modos do menu preservados. Regressões JVM para preferência e posições.

Branch `feat/interface-aura` (base 0.5.2 teste). Versão não mudou. Detalhes, mapeamento e inventário
em `docs/INTERFACE-AURA.md`.

- Tela inicial refeita no padrão visual **Aura** da designer (`docs/design/aura-launcher/`), em Views
  nativas: barra de status (Wi-Fi/Bluetooth/GPS, estado do painel, clima e bateria lidos, botão
  Claro/Escuro), herói com relógio, saudação, data e tempo, modelo/trava/resumo, **carro 3D atual** no
  centro com chips de estado (Portas, Vidros, Capô, Porta-malas, Carregamento, Pneus; palavra + ícone,
  `warn` quando aberto), quatro atalhos (Navegação, Bateria e autonomia, Climatização, Mídia) com
  janelas ancoradas (uma por vez, 180 ms, sem animação se o Android estiver sem animações) e dock.
- Painel de instrumentos: o botão Painel do dock abre uma janela com estado, motivo da falha e
  Mostrar no painel / Voltar ao painel original.
- Estado inicial da Aura: controles só mostravam dados; a rodada 3 abaixo acrescenta comandos com liberação explícita. Sem dado não vira fechado.
- Nada inventado: mídia real ("Nada tocando" sem sessão), Casa/Trabalho com distância real ou
  "Definir", categorias da busca fazem busca de verdade no Photon.
- Busca completa (NavSearch) com a escolha Painel Livre/Maps/Waze, rota ativa, lugares, mapa com a rota
  nativa e Centralizar. Apps e tela em pé (atalhos 2 × 2) mantidos.
- Fontes Sora e Albert Sans empacotadas (offline, OFL), ícones de linha Aura em vetor, tokens dos temas
  claro e escuro; discador e previsão do tempo com as mesmas cores. Densidade própria para o desenho
  base 1280 × 720 (manifesto sem mudança).
- Testes: `AuraTest` (14 novos) — 223 testes JVM, 0 falhas; `lintDebug` 0 erros (76 avisos, antes 174
  nesta branch); `assembleDebug` concluído. Emulador `emulator-5580`: claro, escuro, em pé, cada janela,
  simulação de telemetria do debug e animações desligadas. **Não testado no carro.**

### Rodada 2

- Painel Aura com tokens claro/escuro, Sora/Albert Sans e ícones de linha; menu, área segura,
  aviso de telemetria, rota nativa e debug preservados.
- Ruas mais largas por zoom nos dois mapas, seta larga em accent com contorno branco/sombra e
  rota accent com contorno. Duração da chegada sem reticências; idade do GPS simulado corrigida.
- Reproduzir sem sessão retoma mídia dentro da interface; nenhuma abertura externa nova.
  Exceções antigas documentadas em `docs/INTERFACE-AURA.md` para decisão do dono.
- 238 testes JVM passando, lintDebug sem erros (76 avisos), assembleDebug concluído.
  Emulador disponível; capturas `/tmp/pl-aura-prints/r2-*`. Sem teste no carro, versão inalterada.

## 0.5.2 (teste) — 2026-10-23 — área segura, menu do painel, telemetria e carro 3D

versionCode 8. Não lançada; consolida a 0.5.1, telemetria só leitura (#16) e carro 3D.
Validação conjunta local: 209 testes JVM, sem falhas; `lintDebug` com 0 erros e 116 avisos;
`assembleDebug` concluído. Validação visual conjunta no emulador e teste no carro pendentes.

- Aviso de telemetria limitado ao canto superior direito da área segura, inclusive no modo Pequeno
  e com ajuste fino. Cartões, carro, crédito OSM e contorno debug preservados.

- Revisão local dos commits `bac5aa0` e `05c289c`: rejeita Parcel truncado, palavras inválidas e
  valor fora do Parcel; confere o código de saída do shell e pausa também após falhas do serviço/parser.
  Valores fora de 1..4 continuam sem escolher modo; nenhum setter ou novo comando do carro.
- Área segura: cálculo sem overflow com margens extremas, ao menos um pixel livre em displays mínimos
  e redução dos cartões também pela largura para não invadirem o carro com ajustes laterais grandes.
  Preferências antigas continuam como ajuste somado ao padrão (decisão da v0.5.1), cobertas por regressão.
- Validação da revisão: 183 testes JVM, 0 falhas; `lintDebug` com 0 erros e 114 avisos;
  `assembleDebug` concluído. Sem validação visual nova no emulador ou teste no carro.

- **Correção (issue #9)**: no carro (DiLink 3.0, Android 10) o SDK BYD recusava a leitura do menu no
  processo do app ("permissão BYD recusada … ajudante uid 2000 pendente"), então "Seguir o menu" nunca
  mudava nada. Agora, quando o SDK recusa por permissão, o app lê `INSTRUMENT_NAVI_TYPE` pelo ADB local
  (uid 2000, o mesmo canal do `sendInfo`) com um único comando fixo,
  `service call autoservice 5 i32 1007 i32 1086337074`, conferido no carro. Só leitura: nada de
  transação 6 nem `INSTRUMENT_NAVI_TYPE_SET`; o ADB só é usado no Android 10.
- Leitura a cada 1 s, só com a tela do painel aberta e a opção ligada. OFF esconde mapa e cartões
  (tela preta), Simples mostra só os cartões, Pequeno vai para o meio, Cheio é como sempre.
- Mapeamento **confirmado no carro** pelo dono: 1 = OFF, 2 = Simples, 3 = Pequeno, 4 = Cheio;
  0xffffd8e5 = indisponível (fica como sempre).
- Modo de debug: cada mudança lida vai ao registro com valor bruto, hex, modo e origem
  (`[menu] ADB: bruto=2 (0x00000002) → Simples`). A tela do debug e a fotografia usam a mesma leitura.
- Testes: `PanelMenuReaderTest` (parser do `Parcel`, comando, SDK → ADB, pausa após falha) —
  167 no total, 0 falhas; `lintRelease` com 0 erros.
- **Área segura do mapa no painel** (foto do painel do Song Plus): os cartões, o carro, o foco do mapa e
  o crédito do OpenStreetMap ficam dentro do retângulo que os desenhos da BYD não cobrem (faixas de
  cima e de baixo, cantos curvos, avisos dos lados, assistente de faixa). Padrão Song Plus: esquerda
  230, topo 90, direita 230, base 230 (pixels de 1920×720, escalam com o painel). Manobra colada no
  topo da área, chegada logo abaixo, carro em x 960, y 360, crédito no canto de baixo à direita da área.
- Ajustes → **Área segura do mapa no painel**: padrão (Song Plus ou tela inteira) e ajuste fino por
  lado (as antigas "Margens", agora somadas ao padrão e aceitando negativo, ±400). Com o modo de debug
  ligado, o painel mostra o contorno da área segura e as margens finais.
- Testes: `SafeAreaTest` (11) e `PrefsTest` atualizado — 179 no total, 0 falhas; `lintRelease` com 0 erros.
  Conferido no emulador (pré-visualização 1920×720 com rota simulada, `docs/telas/1` e `8`); falta o carro.

### Carro 3D na tela inicial

- Renders locais do Song Plus em 3/4 e vista superior com transição suave e overlays
  de portas, capô, porta-malas, carga, vidros/teto/cortina e pressão dos pneus.
- Cadeado discreto, cache dos bitmaps e simulação debug por cenário.
- Layout da central, cartões e barra inferior preservados; assinatura sem alteração.
- Testes de regressão da decisão de vista e mapeamento dos estados. Sem acesso ao carro.
- Validação: 180 testes JVM, lintDebug sem erros (114 avisos), assembleDebug; capturas
  de padrão/porta/carga/pneu no emulator-5580 em docs/telas/carro-3d-*.png.

### Telemetria só leitura (issue #16)

- Revisados os commits b622959 e 99d7782: telemetria restrita às leituras autoservice 5/7;
  teste adicional recusa código 6 para todos os endereços/protocolos e lotes.
- Leituras parciais não afirmam que todas as portas/vidros estão fechados.
- Concluída a simulação debug; desligar apaga os dados fictícios imediatamente.
- Parser Parcel ignora ASCII por linha e preserva payload multilinha; regressão da sentinela
  0xffffd8e5 em todos os sinais. Documentação e roteiro em docs/TELEMETRIA.md.
- Validação: 174 testes JVM passando; lintDebug com 0 erros e 112 avisos.
- Validação visual conjunta e confirmação dos IDs no carro pendentes; nenhuma escrita no carro.

## 0.5.0 — 2026-10-23 — rota nativa, menu do painel, modo de debug e discador (pré-release de teste)

APK `PainelLivre-v0.5.0-release.apk` (versionCode 6), SHA-256 `1fb15b4ddfe371b4443f1f072a5701996884e0d84140ae4a6853a9e62f2fca06`.
Junta as issues #14, #9 e #15: 155 testes, `lintRelease` com 0 erros. Falta validar no carro.

### Issue #14 — rota no mapa nativo

- Opção **Painel Livre** ao lado de Maps/Waze; destinos por busca, guardados, recentes,
  compartilhamento e intents geo/google.navigation, com confirmação da fonte na entrada externa.
- Rota independente por OSRM HTTPS, linha no mapa da central/painel, próxima manobra, distância,
  restante/chegada e recálculo por desvio. Serviço acompanha GPS sem depender das notificações.
- Prioridade para o integrado ao navegar nativamente, sessão encerrável na aba Navegação e proteção
  contra ativação duplicada. Notificações externas não sobrescrevem a orientação nativa.
- Novo serviço externo documentado no README: OSRM recebe origem/destino precisos. Requer rede;
  sem voz/trânsito/offline; links curtos não são expandidos. Sessão não persiste ao matar o processo.
- Validação: 129 testes JVM (12 novos), zero falhas; `lintRelease` com 0 erros/109 avisos;
  `assembleDebug` concluído. Roteiro do carro em `docs/PLANO.md`. Emulador indisponível
  por falta de `/dev/kvm`; capturas e validação real pendentes. Sem mudança de versão/publicação.

### Menu de navegação do painel e modo de debug (issue #9)

- **Modo de debug** (Ajustes → Testes e ajuda, **desligado por padrão**): com ele ligado, o app
  registra com horário e valores o que muda em `settings` (system/global/secure), `getprop`, histórico de
  broadcasts (`dumpsys activity broadcasts history`), logcat filtrado, getters candidatos do SDK do
  painel e broadcasts conhecidos com os extras. Tudo só leitura; a coleta pelo ADB para sozinha em
  30 min. A tela "Abrir o registro do debug" marca a opção escolhida no painel (Simples/Pequeno/Cheio/OFF),
  tira uma fotografia completa (pacotes, serviços, receptores, propriedades, telecom, telas por display),
  escuta ações digitadas, testa o desenho de cada opção e copia, compartilha ou salva o registro.
- **Seguir o menu do painel** (Ajustes → Painel, **experimental, desligado**): lê `INSTRUMENT_NAVI_TYPE`
  pelo SDK BYD (getter genérico, por nome; device 1007, feature 0x40C03032 do DiPlay) e ajusta a nossa
  tela: Pequeno = mapa e cartões no centro, Simples = só os cartões, OFF = tela preta, Cheio = como
  sempre. Sem leitura ou valor estranho, fica como sempre; um modo novo só vale depois de 2 leituras
  iguais. Nenhum comando novo ao AutoContainer (17 e 39 continuam fora), nada de `service call` cru.
- Testes: `PanelMenuTest`, `DebugProbeTest` e `PrefsTest` — 138 no total.
- Validação: `testDebugUnitTest` 138/138, `lintRelease` com 0 erros, `assembleDebug` concluído;
  emulador: coleta, fotografia, salvar e os quatro desenhos de teste (`docs/telas/7-modo-debug.png`).
  Sem carro; o mapeamento dos valores e a leitura na DiLink 3 são hipóteses (`docs/PLANO.md`, issue #9).

### Discador liga direto pelo celular (issue #15)

- **Problema (no carro, v0.4.1)**: a ligação do discador ia para o app de telefone da BYD.
- **Mudança**: ordem nova do `CallPlan` — `placeCall` com a conta Bluetooth/HFP do Telecom →
  `ACTION_CALL` só se quem atende for o Telecom do Android → `BluetoothHeadsetClient.dial` (reflexão)
  → `placeCall` padrão → `ACTION_CALL` atendido por app → `ACTION_DIAL` → app do carro (comportamento
  antigo como reserva). Confere a chamada em 3 s (`isInCall`) e só avisa; nunca liga duas vezes.
- `READ_PHONE_STATE` nova (pedida junto com `CALL_PHONE`; as duas no Conceder pelo ADB).
- Modo de debug registra o caminho usado e a resposta do sistema, sem o número.
- Testes: `DialerTest` — 143 no total. Validação: `testDebugUnitTest` 143/143, `lintRelease` 0 erros;
  emulador: `CALL_INTENT` → chamada confirmada. HFP só no carro (`docs/PLANO.md`, issue #15).

## 0.4.1 — 2026-10-23 — APK volta a instalar no carro (pré-release de teste)

APK `PainelLivre-v0.4.1-release.apk`, SHA-256 `95e10a175c7352918353127c242691fd0594948d6e05674a7ffe7f324b40005d`.

- **Problema (no carro)**: a v0.4.0 não instalava na central (Song Plus DM-i 2025, DiLink 3): "Fail in
  installation of desktop apps". A v0.3.0 instalava.
- **Causa (hipótese até confirmar no carro)**: a v0.4.0 (issue #7) passou a trazer o alias `HomeAlias`
  (`MAIN` + `HOME` + `DEFAULT`) **ligado** no manifesto; o instalador da BYD parece recusar APK que chega
  declarando um launcher ativo ("desktop app").
- **Correção**: o alias volta a vir `android:enabled="false"`. "Usar como tela inicial" agora o liga em
  tempo de execução (`setComponentEnabledSetting`, `DONT_KILL_APP`) antes do `set-home-activity` ou do
  seletor, e lê o launcher original antes de ligá-lo (a BYD sai como original, não o seletor). Nada
  liga o alias sozinho, nem na primeira abertura. "Voltar ao launcher da BYD" segue igual.
- Testes: `HomeChoiceTest` com estado do alias, quando ligá-lo, "nosso" velho com alias desligado,
  alias desligado no manifesto e nenhum outro componente com `HOME` — 117 no total.
- Validação: `testDebugUnitTest` 117/117, `lintRelease` com 0 erros (108 avisos), `assembleDebug`
  concluído. Sem emulador nem carro; conferir no carro em `docs/PLANO.md` (Fase 1, item 8).

## 0.4.0 — 2026-10-23 — painel, telemetria e central (issues #1 a #7)

APK `PainelLivre-v0.4.0-release.apk`, SHA-256 `21aad317dd33e6e8b49641208c566d910b66bcbd3547b6b6265359eb9fcb401f`.

Reúne as três etapas abaixo (PRs #8, #10 e #11). Validação: `testDebugUnitTest` 113/113, `lintRelease` com 0 erros
(108 avisos), `assembleRelease` assinado com `CN=Painel Livre`. Sem teste em emulador nem no carro nesta versão.

### 2026-10-23 — central: tela inicial, discador e previsão (issues #7, #3, #4)

- **#7 Tela inicial**: causa — o filtro `HOME`/`DEFAULT` ficava no alias `HomeAlias` desligado no
  manifesto, então o Android e a BYD não listavam o Painel Livre como launcher; além disso "Usar como
  tela inicial" podia guardar o seletor do Android (`ResolverActivity`) como "launcher original" e não
  conferia se o `set-home-activity` deu certo. Agora o alias vem ligado; o botão confere antes e depois
  (`HomeChoice`), sem ADB abre a tela "Tela inicial" do Android ou o seletor com "Sempre"; "Voltar ao
  launcher da BYD" desliga o alias se não souber o original. O botão Início volta para a aba Início.
  Diagnóstico mostra o launcher atual, os candidatos e os apps de telefone.
- **#3 Telefone**: discador próprio (`DialerActivity`): teclado, formato brasileiro, Rediscar, apagar
  (toque longo limpa), "+" no toque longo do 0. Liga por `ACTION_CALL` (pede `CALL_PHONE` uma vez),
  depois `ACTION_DIAL`, `TelecomManager.placeCall` e por último abre o app de telefone/Bluetooth do
  carro com o número copiado. Sem nenhum caminho, avisa em vez de "nenhum app". O número não vai para o log.
- **#4 Tempo**: toque no tempo da tela inicial abre a previsão (`WeatherActivity`): agora (sensação,
  umidade, vento, máx/mín, nascer/pôr do sol), 24 horas e 7 dias. Mesmo pedido ao Open-Meteo, mesma
  posição arredondada a 0,1°. Corrigido: sem internet o app pedia o tempo a cada posição do GPS; agora
  no máximo 1 vez por minuto (15 min com dados).
- Testes: `HomeChoiceTest` (7), `DialerTest` (10), `ForecastTest` (9) — 99 no total.
- Validação: `testDebugUnitTest` 99/99, `assembleDebug` e `lintDebug` com 0 erros. Sem emulador nem
  carro nesta etapa; o que depende do carro está em `docs/PLANO.md` (Fase 1, item 8).
- Sem novo APK de release nem mudança da versão 0.3.0.

### 2026-10-23 — painel e telemetria (issues #2, #5 e #6)

- #2: pedido de destino agora pede ativação do painel nos modos navegação/sempre, sem depender
  da chegada da notificação. Modo manual preservado; Maps/Waze continua recebendo o destino.
- #5: escolha entre mapa integrado (padrão, manobras sem linha da rota) e Maps/Waze no painel
  (experimental). Abre pelo ADB no display conhecido e só confirma sucesso se a Activity retomada
  estiver nesse display; falha aparece no estado/notificação e no log, com indicação do mapa integrado.
  Vigias e eventos de cobertura do mapa próprio não disputam o painel com o navegador.
- #6: chamadas BYD deixam de descartar falhas; dados parciais são preservados e cada método com
  erro é identificado no diagnóstico. Log registra mudanças, sem repetição a cada leitura.
  Info da central inclui dados, indisponibilidade de portas/travas e permissão estatística real do app.
- Parcial: SDK ainda consultado pelo uid comum; ajudante uid 2000 e leitura de portas/travas pendentes.
  Nada escreve no carro. Projeção externa, restauração e dados reais exigem validação no carro.
- 14 testes JVM novos (87 no total, todos passando): conteúdo/modos do painel, confirmação por display, preferência, valores BYD,
  exceções de reflexão/permissão e apresentação de leituras parciais. `assembleDebug` concluído;
  `lintDebug`: 0 erros, 105 avisos. Sem emulador/carro e sem novo release. Build usou cache e chave
  debug temporários em `/tmp` pelas restrições de escrita; assinatura release não alterada.

### 2026-10-22 — regressão JVM (issue #1)

- 32 testes novos (73 no total): notificações Maps/Waze, manobras, distância, rua, resumo e chegada;
  modos sempre/navegação/manual, atraso e cancelamento da restauração; padrões e limites das preferências.
- Decisão automática do painel extraída para `PanelAutomation`, sem dependência de Android; acesso
  às preferências permite armazenamento falso nos testes. Sem mudança das regras de ativação.
- Bug reproduzido por teste e corrigido: a restauração automática cancelava o próprio temporizador
  ao entrar em `restore()`, podendo parar antes do comando 0 (depois do 18). O prazo vencido agora
  limpa sua referência antes da restauração. A lista de comandos seguros permanece igual.
- CI com etapa explícita de testes JVM; continua desativado por cobrança da conta.
- Validação: 73 testes passando em `testDebugUnitTest`, `lintRelease` com 0 erros (105 avisos)
  e `assembleDebug` concluído; sem teste de emulador ou carro nesta etapa.
  Exemplos de notificação reutilizados do simulador/testes, sem alegar capturas reais do carro.
- Sem novo APK de release ou alteração da versão 0.3.0.

## 0.3.0 — 2026-10-22 — interface no estilo do SealNav

APK `PainelLivre-v0.3.0-release.apk`, SHA-256 `2d6221c0ef8715bcbf90ee140c9fb2ea1cdd37beae5a1b499cbfd6a80c71b236`.

O dono pediu que as telas sejam as do SealNav, com a única diferença de a rota vir do Google Maps/Waze.

- **Central (cockpit)**: barra de cima (saudação, hora/data, Wi-Fi, Bluetooth, GPS, temperatura); fundo
  e ilustração de SUV desenhados em código (ou foto do carro escolhida pelo motorista); autonomia e
  tempo (Open-Meteo); cartões Mídia (sessão de mídia ativa, anterior/tocar/próxima), Bateria, Painel
  (mostrar/voltar) e Destino (Casa/Trabalho, ou a manobra atual); barra de baixo com Início, Navegação,
  Mídia, Telefone, Apps, Ajustes e atalhos de Painel e do app de navegação; aviso "Falta permitir…"
  com botão Configurar.
- **Aba Navegação**: busca de endereços (Photon/OpenStreetMap), Google Maps ou Waze, Casa/Trabalho
  (definidos pela busca), recentes, mapa com a posição do carro, cartão da rota atual.
- **Painel**: layout do SealNav — cartão da manobra (seta branca em quadrado azul, distância, instrução,
  rua) em cima à esquerda, cartão Chegada/Tempo/Distância embaixo, carro no centro a ~59% da altura,
  nada nas faixas onde a BYD desenha os instrumentos, sem relógio.
- Temas dia/noite automáticos (6h–18h30) para central e mapa; tela em pé (`layout-port`).
- Ajustes reorganizados: Aparência (tema, foto do carro), Painel, Testes e ajuda (simular, permissões,
  diagnóstico), Sistema.
- Removidos: endereços de casa/trabalho em texto (agora pela busca) e a opção "seta branca".
- Testes: `CockpitTest` (Photon, tema, tempo). Total 41.

## 0.2.0 — 2026-10-22 — robustez do SealNav (Fase 3, itens 1–6)

APK `PainelLivre-v0.2.0-release.apk`, SHA-256 `24961524ccc413ea39a6dbea9d6ead0ab8f4b2aafcee62f3dafc98450d2ae2ed`.

- ADB local com uma conexão só, reconexão e até 15 tentativas no boot; estado observável na tela.
- Ativação como o SealNav: limpeza de estado velho (18 → 0), código de tamanho do painel só quando
  seguro (`PanelSize`), pausas de 1 s, 16 → 35.
- Tela do painel aberta pelo shell em tela cheia, com alternativas (janela livre ajustada, direto).
- Carro e câmera animados a cada quadro (`PoseSmoother`), limite de fps 60/30 andando e 5 parado.
- Vigia de travamento (`FreezeWatch`): reabre, redesenha, recria a superfície (com limites).
- Convivência com outros apps no painel (`YieldPolicy`).
- Boot registrado no Diagnóstico, `MY_PACKAGE_REPLACED`, permissões de segundo plano pelo ADB.
- Correções: "Conceder tudo" marcava sucesso como falha; pré-visualização não fechava com toque.
- Testes: `PanelSizeTest`, `PoseSmootherTest`, `FreezeWatchTest`. Total 38.

## 0.1.0 — 2026-10-22 — primeira versão

APK `PainelLivre-v0.1.0-release.apk`, SHA-256 `15eaf960dc714fa850ebb3fcc927cef951a4fbf8a43636944151f850622e7b22`.

- Mapa MapLibre/OpenFreeMap no display do painel, seguindo o GPS da central.
- Leitura da notificação do Google Maps/Waze (`NavParser`, pt-BR e inglês) com seta, distância, rua,
  chegada.
- Ativação do painel pelo `AutoContainer` via ADB local (protocolo do DashCast).
- Launcher simples na central, Configurações, Diagnóstico (dual/single-OS, displays, AutoContainer),
  simulação de rota.
- Testes: `NavParserTest` (13).

### Rodada 3 — comandos internos (não lançado)

- `CarCommands` centraliza a transação 6, com catálogo fechado, valores limitados, chave geral e
  individuais desligados, bloqueio de movimento sem P real recente e registro no Diagnóstico.
- Clima/temperaturas do dock e mídia Android passam pela camada. Confirmação humana no clima,
  releitura real, feedback imediato, limite de um envio/s e ADB sem repetição automática.
- Temperaturas inteiras 16–30, fan 0–7, energia/A-C/AUTO/recirculação/desembaçador comprovados na
  fonte estática, ainda sem teste no carro. Meio grau, bancos, porta-malas/vidros/teto, bateria e
  rádio BYD seguem bloqueados por evidência incompleta. Tabela e fontes em `docs/COMANDOS.md`.
- Sem alteração de versão, manifesto, assinatura; sem acesso ao carro, push ou merge.

Validação da rodada 3: 248 testes JVM, zero falhas; `testDebugUnitTest lintDebug assembleDebug`
com JDK 17. Lint sem erros (79 avisos). No emulator-5580 foram conferidos a janela de clima,
seletor, confirmação e rejeição com a chave desligada, registrada no log; lista de liberações
individuais e recusa de hipótese. Capturas `docs/telas/aura-r3-*.png`. Nenhum teste no carro.
