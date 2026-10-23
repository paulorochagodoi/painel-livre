# Histórico de versões

Os APKs de release são assinados com a chave local do projeto (`CN=Painel Livre, O=Uso pessoal, C=BR`,
certificado SHA-256 `7290204a…13d9`) e instalam um por cima do outro. Eles não ficam no repositório.
Nenhuma versão foi testada no carro ainda: todos os testes foram no emulador (`docs/AMBIENTE.md`).

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
