# AGENTS.md — guia para agentes que continuam o Painel Livre

Leia este arquivo inteiro antes de mexer no projeto. Ele resume o contexto, as decisões e as regras que
não se quebram; os detalhes estão nos documentos de `docs/`. Escreva para o usuário em **português do
Brasil**, frases simples, sem jargão desnecessário.

## 1. O que é e aonde queremos chegar

- **Dono e carro**: BYD Song Plus DM-i 2025, central DiLink 3 (Android 10), multimídia 13.1.33, vendido
  **sem mapa de fábrica** (os modelos 2024 e 2026 vieram com). A central é **dual-OS**: DashCast e
  SealNav já projetaram no painel desse carro (confirmado pelo dono).
- **Meta de produto (decisão firme do dono)**: o **"SealNav aberto"** — mesmas telas e o mesmo jeito de
  usar do SealNav (app pago), fácil de usar e de entender; **a única diferença de propósito é a rota vir
  do Google Maps ou do Waze**. O SealNav calcula a própria rota; o Painel Livre lê a notificação do
  Maps/Waze.
- **Depois**: setas no HUD/painel via CAN (Fase 4), controles de conforto como os do SealNav (Fase 4b),
  navegação própria opcional com linha da rota no painel (Fase 3, item 7), extras (Fase 5).

## 2. Onde o projeto está (2026-10-23)

- **v0.4.0**, lançada em 2026-10-23: painel/telemetria (#2, #5, #6) e central (#7 tela inicial, #3 discador,
  #4 previsão do tempo), com **113 testes JVM passando** e lint sem erros. A v0.3.0 foi testada só no emulador;
  o que entrou na v0.4.0 foi validado só na JVM/build/lint. Conferir no carro: `docs/PLANO.md`, Fase 1, item 8.
  **A v0.4.0 não instalou no carro** ("Fail in installation of desktop apps"); a **v0.4.1** (não lançada)
  volta o alias HOME a desligado no manifesto (ver §9).
  **Nada foi testado no carro ainda** — o próximo passo combinado é o primeiro teste no carro
  (`docs/PLANO.md`, Fase 1). O dono ainda não liberou acesso ADB ao carro para o agente.
- Funciona (emulador): tela central estilo cockpit (dia/noite, deitada/em pé), aba Navegação com busca
  e Casa/Trabalho/recentes abrindo a rota no Maps/Waze, painel com o layout do SealNav, leitura da
  notificação do Maps/Waze, ativação do painel na sequência do SealNav, ADB persistente, vigia de
  travamento, convivência com outros apps, diagnóstico; discador interno e previsão do tempo (JVM).
- Histórico em `CHANGELOG.md`. Telas em `docs/telas/`.

- **Não lançado (branch `feat/comandos-painel-debug`)**: issue #9 — modo de debug (Ajustes, desligado por
  padrão) e "Seguir o menu de navegação do painel" (experimental, desligado); issue #15 — discador liga
  direto pelo celular (conta HFP, `ACTION_CALL` do Telecom, cliente HFP; app do carro de reserva);
  143 testes JVM. Checklists em `docs/PLANO.md`, issues #9 e #15.

**Próximos passos, em ordem**:
1. Fase 1 — primeiro teste no carro (checklist em `docs/PLANO.md`). Depende do dono.
2. Fase 4b, passo 1 — extrair dos moldes do SDK BYD (dentro do APK do BYD Navigator) as constantes de
   clima, bancos, vidros e porta-malas. Não precisa do carro.
3. **Lembrete do dono**: ampliar os testes de regressão (roteiros do emulador automatizados, testes de
   interface, casos vindos do carro) — detalhes no topo da Fase 1 de `docs/PLANO.md`.
4. Ajustes que o teste no carro mostrar (margens, parser das notificações, autoinício).
5. Ajudante uid 2000 próprio (só leitura primeiro) → Fases 4 e 4b.

## 3. Mapa da documentação

| Arquivo | Conteúdo |
|---|---|
| `README.md` | Para quem usa: o que faz, instalar, segurança, créditos |
| `docs/PLANO.md` | Fases, estado de cada uma, checklist do teste no carro, regras da Fase 4b |
| `docs/ARQUITETURA.md` | Pacotes, arquivos, fluxo de dados, ciclo do painel, temas por tag, preferências, testes |
| `docs/AMBIENTE.md` | Ferramentas, compilar, assinar, emulador que imita a DiLink 3, roteiros de teste, scripts `tools/re` |
| `docs/ENGENHARIA_REVERSA.md` | BYD Navigator (Telenav) e DashCast: AutoContainer, displays, SDK BYD (leitura, conforto, HUD) |
| `docs/SEALNAV_RE.md` | SealNav em sala limpa: achados A–K, interface, o que já foi levado ao Painel Livre |
| `CHANGELOG.md` | Versões, o que mudou, SHA-256 dos APKs |

## 4. Comandos

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew assembleDebug assembleRelease lintRelease
```

Ambiente (JDK 17, SDK, `local.properties`, chave de assinatura, emulador com display de painel
simulado) em `docs/AMBIENTE.md`.

Repositório: <https://github.com/paulorochagodoi/painel-livre> (privado, branch `main`). O CI
(`.github/workflows/android.yml`: testes, lint e build debug) está **desativado** desde 2026-10-22
porque o GitHub recusou a execução por cobrança da conta; depois que o dono resolver, reativar com
`gh workflow enable Android`. Até lá, rode os testes localmente antes de cada envio.

## 5. Regras que não se quebram

### Segurança do carro
- **AutoContainer** (`service call AutoContainer 2 i32 1000 i32 <código> s16 ""`): só `16`, `35`, `18`,
  `0` (`ClusterController.SAFE_COMMANDS`) e um código de tamanho aprovado por `PanelSize`. **Nunca `1`**
  (destrói o display). Não amplie essas listas sem evidência do carro e sem falar com o dono.
- **Código de tamanho** (29 = 8,8", 30 = 12,3", 31 = 10,25"): o código errado já deixou painéis de 8,8"
  em "modo simples". Automático só manda 30 para Song Plus/Seal U **com** painel já medido ≥1800 de
  largura e proporção ≥2; escolha manual é bloqueada se contradizer o painel medido. Primeira ativação,
  sem medida, não manda nada (comportamento do DashCast). Coberto por `PanelSizeTest`.
- **Vigias e reaberturas nunca reenviam comandos ao AutoContainer** — só reabrem a tela. Isso evita
  laço com a firmware.
- A tela do painel **nunca pode ficar na central**: ela não recebe toque e cobriria a tela. Se cair no
  display 0 ou o display sumir, ela fecha (`ClusterActivity.leaveIfOnCentralScreen`, `displayListener`).
- **Nada escreve no CAN/SDK BYD hoje.** Escrita só nas Fases 4 e 4b, com as regras de lá: lista fechada
  de métodos dentro do ajudante uid 2000, nada de travas/portas/câmbio/freio/direção/ADAS/CAN cru,
  vidros/teto/porta-malas só com o carro parado, ler o valor de volta, limite de frequência, confirmação
  na primeira vez e interruptor geral, uma função por vez testada no carro parado.
- **Telefone**: o discador só liga quando o motorista toca em Ligar; nunca ligar sozinho nem para
  número que não foi digitado. O número não vai para o log.
- **SDK/`autoservice` da BYD**: ler só por nome de getter (reflexão), nunca por `service call autoservice
  <n>`: os números de transação mudam com a firmware e, na DiLink 3, podem cair num setter. O modo de
  debug só lê (`settings list`, `getprop`, `dumpsys`, `logcat -d`); nada de `settings put`/`setprop`.
- Nunca mexer nas chaves ADB do sistema (o comando `adbkeys` do SealNav autoriza/apaga chaves sem aviso:
  **não adotar**).

### Sala limpa (SealNav e BYD Navigator)
- Estudar **mecanismo e protocolo** (interoperabilidade) e registrar em `docs/`. O código do Painel Livre
  é escrito do zero.
- **Não** copiar código, imagens (carro 3D, cursores, papéis de parede), ícones, textos longos, voz,
  chaves de API ou perfis do SealNav. **Não** usar os servidores dele (`sealnav.com` API,
  `relay.sealnav.com`). **Não** contornar a ativação (`ActivationActivity`) dele nem a ativação por VIN
  do BYD Navigator.
- Olhar as fotos públicas de sealnav.com para seguir o layout é permitido (foi assim que a v0.3.0 foi
  desenhada); o visual é próprio.
- **Nunca versionar** APKs de terceiros (BYD Navigator, DashCast, SealNav) nem saída de descompilação.
  Eles ficam fora do repositório, na pasta de trabalho do dono.

### Privacidade e segredos
- `keystore/` e `local.properties` ficam fora do git. Nunca imprimir senhas da chave.
- Serviços externos usados: OpenFreeMap (mapas), Photon (busca; vai o texto e a posição arredondada a
  0,01°), Open-Meteo (tempo; posição arredondada a 0,1°). Nada de dados pessoais em URL além disso.
  Novo serviço externo = registrar no README e preferir os abertos e sem chave.

### Produto
- Interface nova segue a estrutura do SealNav (ver `docs/SEALNAV_RE.md`, "Interface"). Na dúvida,
  conferir as fotos públicas antes de desenhar.
- Textos da interface em português do Brasil, curtos e claros. O usuário final é motorista, não técnico.
- Mudança grande de escopo ou de jeito de usar: perguntar ao dono antes.

## 6. Convenções de código

- Kotlin, Views clássicas, coroutines/`StateFlow`. Comentários em português explicando **por quê**
  (referenciando o achado em `docs/` quando a ideia vem de engenharia reversa).
- Lógica que dá para separar do Android vai para Kotlin puro com teste na JVM (`PanelSize`,
  `PoseSmoother`, `FreezeWatch`, `NavParser`, `Palette`). Não use `android.graphics.Color` (nem outra
  API Android) na inicialização dessas classes: quebra os testes da JVM.
- Cores da tela central **não** vão no XML: use `android:tag` e `Palette.apply` (tabela em
  `docs/ARQUITETURA.md`). Estilos `Cc*` em `res/values/themes.xml`.
- Log com `AppLog` (aparece em Diagnóstico → Log do app), mensagens em português.
- Versão em `app/build.gradle.kts` (`versionCode` +1, `versionName`); o APK sai como
  `PainelLivre-v<versão>-<tipo>.apk`.

## 7. Como validar uma mudança

1. `./gradlew testDebugUnitTest` — tudo passando; teste novo para lógica nova.
2. `./gradlew lintRelease` — 0 erros.
3. Emulador (`docs/AMBIENTE.md`): instalar o debug, rodar os roteiros que tocam no que mudou
   (ativação, restaurar, simulação, convivência…) e conferir o log `PainelLivre`.
4. Mudou tela: capturar de novo e atualizar `docs/telas/`.
5. Atualizar `CHANGELOG.md`, `docs/PLANO.md` e o documento de `docs/` afetado.
6. Release: subir a versão, `assembleRelease`, conferir a assinatura
   (`apksigner verify --print-certs` → `CN=Painel Livre`), anotar o SHA-256 no `CHANGELOG.md`.

## 8. Conhecimento rápido do carro

- Display do painel: nome com `fission`/`xdja` (`fission_bg_xdjaVirtualSurface`, 1920×720, dono
  `com.xdja.containerservice`, uid 1000); na DiLink 5.1 `shared_…_0`. Criado pelo Qt depois do `35`.
- `ro.build.system.fission_single_os=1` → central single-OS: não há painel projetável.
- Uid 2000 vem do ADB local (`127.0.0.1:5555`); a Activity do painel precisa ser `exported` e
  `allowEmbedded`; abrir com `am start --display N --windowingMode 1`.
- No painel, os instrumentos da BYD são desenhados **por cima** da projeção: faixa de cima (hora, P,
  ECO, temperatura), faixa de baixo (kW, bateria, autonomia, velocidade) e alertas à direita. Os cartões
  ficam entre ~16% e ~62% da altura, à esquerda; o carro no centro a ~59%.
- SDK BYD (`android.hardware.bydauto.*`): leitura de bateria/autonomia por `BYDAutoStatisticDevice`;
  assinaturas de conforto e registradores do HUD em `docs/ENGENHARIA_REVERSA.md`.
- O SealNav identifica o modelo por `ro.product.name` + `Build.MODEL/PRODUCT/DEVICE/FINGERPRINT`.

## 9. Armadilhas já encontradas

- Comentário XML não pode ter `--` (quebrou o manifesto com "`--display`").
- Aspas de polegada em recursos: escrever `12,3\"`; aspas soltas somem na compilação.
- `MapLibreMap.triggerRepaint()` (não existe em `MapView`); `MapView.setMaximumFps` só depois do mapa pronto.
- Margem em porcentagem não existe em `FrameLayout`; calcular no código.
- `layout-port/` só vale se a Activity for recriada ao girar (por isso a `LauncherActivity` não declara
  `orientation|screenSize` em `configChanges`).
- Dentro de `apply { }`, nome de variável local pode esconder a propriedade (`this.maneuver = …`).
- adb como root é recusado no `am start --display`; o carro usa uid 2000.
- O display simulado do emulador aparece como janela e engole toques; `uiautomator` falha com o mapa
  animando; SwiftShader desenha traços no mapa que não existem no carro.
- **`HomeAlias` ligado no manifesto = o APK não instala no carro** (v0.4.0: "Fail in installation of
  desktop apps"; a v0.3.0, com o alias desligado, instalava). Hipótese até confirmar no carro: o
  instalador da BYD recusa APK que chega como launcher ativo. Desde a v0.4.1 o alias vem
  `enabled="false"` e só "Usar como tela inicial" o liga em tempo de execução
  (`setComponentEnabledSetting`, `DONT_KILL_APP`); nada liga sozinho (nem na primeira abertura).
  Nenhum componente ligado pode declarar `HOME` (`HomeChoiceTest`). Com o alias desligado o app não
  aparece como launcher — por isso o botão o liga antes do `set-home-activity`/seletor (issue #7).
  `cmd package resolve-activity` pode responder com o seletor (`ResolverActivity`), que não é um
  launcher para guardar/restaurar; o launcher original é lido antes de ligar o alias.
- `ACTION_DIAL` pode não ter app na DiLink (issue #3): use o `DialerActivity`, nunca `ACTION_DIAL` cru.
- `ACTION_CALL` pode ser atendido pelo app de telefone da BYD (só abre o app, issue #15): o `CallPlan`
  prefere a conta HFP do Telecom e só usa o `ACTION_CALL` direto se quem atende for o Telecom do
  Android. A tela de chamada da BYD aparecer é normal (InCallService). Sem confirmação em 3 s, o
  discador só avisa — nunca tenta outro caminho sozinho (ligaria duas vezes).
- Menu de navegação do painel (Simples/Pequeno/Cheio/OFF, issue #9): nenhum app de terceiros o segue;
  o OFF não esconde a projeção 16/35. O Painel Livre só muda o próprio desenho (`PanelMenuFrame`) e só
  com "Seguir o menu" ligado; mapeamento 1–4 vem do DiPlay (DiLink 5.0), não confirmado na DiLink 3.
- O MapLibre só desenha quando algo muda: carro parado sem quadros é normal (o vigia sabe disso).

## 10. Trabalhando com o dono

- Ele testa no carro dele; agentes não têm acesso ao carro. Peça só observação segura (Diagnóstico →
  Copiar tudo, logcat) e explique cada passo.
- Antes de qualquer coisa que mexa no carro, no GitHub (publicar, mudar visibilidade) ou em contas,
  confirme com ele.
- Ao terminar uma etapa: resumo curto do que mudou, como foi testado (emulador ≠ carro) e o que falta.

## Continuação — issues #2, #5 e #6 (2026-10-23, não lançado)

- Abrir destino em `Places.navigate/navigateText` pede também o painel, sem esperar notificação;
  o modo manual continua respeitado. As manobras ainda exigem acesso às notificações.
- Ajustes → O que mostrar no painel: `panel_content=integrated` (padrão, mapa próprio sem linha
  da rota) ou `navigation` (Maps/Waze no display do painel pelo ADB, **experimental, sem teste no carro**).
  Não confundir mover o navegador com extrair sua rota. Escolher antes de ativar. Os vigias próprios
  não disputam a tela com o navegador. A confirmação usa Activity retomada no display correto.
- Telemetria continua no SDK por reflexão sob o uid do app, não no AutoContainer. Falhas parciais
  agora aparecem em Diagnóstico → Dados do carro e Info da central, com log ao mudar o erro.
  Portas/travas ainda não têm leitura implementada; não inventar IDs, propriedades ou estados.
  Permissão concedida por `pm grant` não garante que o serviço BYD aceite o uid do app.
- Não houve acesso ao carro, mudança de comandos seguros, escrita CAN/SDK, manifesto ou launcher.
  Checklist específico e limites em `docs/PLANO.md` (issues #2/#5/#6).

Validação local desta etapa: 87 testes JVM (14 novos), `assembleDebug` concluído,
`lintDebug` com 0 erros e 105 avisos. Sandbox exigiu `GRADLE_USER_HOME=/tmp/painel-gradle`
e `ANDROID_USER_HOME=/tmp/painel-cluster-android` (chave debug temporária, sem alterar a assinatura
release). Captura das preferências e testes Android/carro continuam pendentes.
