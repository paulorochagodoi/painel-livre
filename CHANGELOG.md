# Histórico de versões

Os APKs de release são assinados com a chave local do projeto (`CN=Painel Livre, O=Uso pessoal, C=BR`,
certificado SHA-256 `7290204a…13d9`) e instalam um por cima do outro. Eles não ficam no repositório.
A v0.4.1 foi a primeira instalada e testada no carro (23/10/2026); as demais validações foram no emulador (`docs/AMBIENTE.md`).

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
