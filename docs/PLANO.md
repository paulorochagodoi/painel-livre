# Plano — Painel Livre

Atualizado: 2026-10-23. Estado: **v0.3.0** (+ issues #7, #3, #4 da central, não lançadas) = Fase 3 itens 1–6 (v0.2.0) + **interface redesenhada no
estilo do SealNav** (central tipo cockpit e painel com o layout dele). 73 testes de unidade, testada
no emulador (Android 10, display secundário 1920×720); **ainda não testada no carro**.
APK: `PainelLivre-v0.3.0-release.apk` (fora do repositório, na pasta de trabalho do dono; instala por
cima das anteriores, mesma assinatura). Guia para agentes: `AGENTS.md`; histórico: `CHANGELOG.md`.

Meta de produto (pedido do usuário em 2026-10-22): **uma alternativa aberta ao SealNav, fácil de usar
e de entender, igual a ele; a única diferença é a rota vir do Google Maps ou do Waze.**

| Fase | Objetivo | Depende de | Estado |
|---|---|---|---|
| 1 | Primeiro teste no Song Plus | carro + ADB | a fazer |
| 2 | Engenharia reversa do SealNav (por que funciona melhor que o DashCast) | — (estático) / Fase 1 (dinâmico) | **feita** — ver `SEALNAV_RE.md`; pergunta 3 respondida pelas fotos públicas; logcat com o SealNav no carro é opcional |
| 3 | Levar ao Painel Livre o que a Fase 2 mostrar (robustez, navegação própria) | Fase 2 | **itens 1–6 na v0.2.0** (emulador); 7–9 a fazer |
| 3b | Interface igual à do SealNav (cockpit + painel), rota pelo Maps/Waze | Fase 2 | **v0.3.0** (emulador); clima/bancos/vidros → Fase 4b |
| 4 | Setas no HUD/painel via CAN | Fase 1 | a fazer |
| 4b | Controles de conforto na central (clima, bancos, vidros, porta-malas), como no SealNav | Fase 1 + ajudante uid 2000 (o mesmo da Fase 4) | **planejada** (pedido do usuário, 2026-10-22); estudo estático pode começar antes do carro |
| 5 | Extras: câmeras no painel, voz, launcher 3D | Fases 2–3 | opcional |

---

## Issues #6, #16 e #24 — roteiro curto no carro (não feito pelo agente)

Só leitura. Carro parado em P para a telemetria. Não rodar transação 6, não mandar `settings put`
nem código 1 do AutoContainer. A versão do APK continua 0.5.7.

1. ADB local já usado pelo app. No notebook, se precisar conferir o serviço:
   `adb shell service list | grep autoservice` (tem que citar `android.gui.BYDAutoServer`).
2. Ajustes → Ler dados do carro ligado. Modo de debug → Abrir o registro → **Ler dados do carro**.
   No log, a linha `Telemetria: bateria … · tanque … · elétrico … · combustível … · portas …`.
   `0xffffd8e5` é "indisponível", não zero.
3. Comparar com o painel original: SOC, tanque, autonomia elétrica e a combustível. Abrir e fechar
   uma porta (parado) e ver o carro na central e o aviso no mapa do painel. Clima, bancos, pneus,
   tampa e carga: só olhar; não é para comandar vidro nem teto.
4. Se um número não bater, no debug preencher **Endereços de leitura** (ex.: `soc=1014:0x4A505038:7`)
   e ler de novo. Anotar o que o log mostrou. Leituras de referência, sem escrever:
   `adb shell service call autoservice 5 i32 1011 i32 555745336` (câmbio; 1 = P na issue #16) e
   `adb shell service call autoservice 7 i32 1014 i32 1246777400` (SOC que o SealNav lê).
5. Rota: deixar **desligado** "Mover o app de navegação para o painel". Conceder notificações.
   Iniciar uma rota no Waze ou no Maps **na central**. O painel (mapa integrado) deve mostrar seta,
   distância e a próxima rua. O log diz `Manobra de Waze:` ou `Manobra de Google Maps:`.
   `adb shell dumpsys display | grep fission` confirma o display 1 (1920×720). O Waze não deve ser
   a activity retomada nesse display (`dumpsys activity activities`).

Validação local desta etapa: suíte JVM 274 testes / 0 falhas antes; 291 / 0 falhas depois.
`assembleDebug` gerou `PainelLivre-v0.5.7-debug.apk`. Sem acesso ao carro.

## Central — issues #7, #3, #4 (2026-10-23, não lançado)

- **#7 launcher**: "Usar como tela inicial" conferido e com plano B sem ADB, Início volta à aba
  Início. Detalhes em `ARQUITETURA.md`, "Tela inicial". A v0.4.0 trazia o alias HOME ligado no
  manifesto e **não instalou no carro** ("Fail in installation of desktop apps"); na v0.4.1 o alias
  volta desligado e o botão o liga em tempo de execução (causa ainda é hipótese, ver Fase 1, item 8).
- **#3 telefone**: discador próprio com quatro caminhos para ligar (`ARQUITETURA.md`, "Telefone").
- **#4 tempo**: tela de previsão (agora, 24 h, 7 dias) do mesmo pedido ao Open-Meteo.
- 26 testes JVM novos (99 no total). Validado só na JVM e no build/lint; **falta emulador e carro**
  (Fase 1, item 8).

## Regressão JVM — issue #1

Suíte ampliada de 41 para 73 testes, sem carro ou emulador. Cobertura de notificações Maps/Waze,
modos do painel e atraso de restauração com tempo virtual, preferências e regras puras já existentes.
Corrigido o cancelamento da própria restauração automática antes de completar a sequência 18 → 0.
Detalhes e limites em `ARQUITETURA.md`, seção Testes. O primeiro teste no carro continua pendente.

> **Lembrete (pedido do dono, 2026-10-23): desenvolver os testes de regressão.** A suíte JVM já cobre a
> lógica pura (73 testes). Falta: (1) automatizar no emulador os roteiros de `AMBIENTE.md` (ativação,
> bloqueio 8,8", estado velho, ADB no boot, convivência, restaurar, fim de rota) com um script que
> confira o log `PainelLivre`; (2) testes instrumentados da interface (cockpit, aba Navegação, painel);
> (3) depois do primeiro teste no carro, transformar o que o carro mostrar (formato real das
> notificações, margens, sequência do AutoContainer) em casos fixos; (4) reativar o CI quando a
> cobrança do GitHub for resolvida.

## Issues #2/#5/#6 — implementação e testes no carro (2026-10-23)

Validação local: **87 testes JVM passando (14 novos)**, `assembleDebug` concluído e `lintDebug`
com **0 erros e 105 avisos**. Sem emulador nesta etapa.

Implementado, sem teste no carro: pedido de rota também solicita o painel (exceto manual), escolha
`panel_content` entre mapa integrado e Maps/Waze experimental, falhas por método no SDK BYD e
indisponibilidade explícita no diagnóstico. Causas no código: `Places` só abria intent na central;
projeção automática dependia de notificação; controlador sempre abria `ClusterActivity`; exceções
nos getters BYD eram engolidas e portas/travas nunca eram consultadas. Recusa do uid do app na
DiLink 3 é hipótese fundamentada no achado I de `SEALNAV_RE.md`, ainda não confirmada neste carro.

Checklist específico, com carro parado e ações feitas pelo dono:

1. Em Ajustes, escolher **Mapa integrado**, modo **Só durante a navegação**; abrir destino em Maps
   e Waze pelos resultados, Casa/Trabalho e busca textual. Painel deve abrir sem esperar notificação.
   Sem acesso às notificações, mapa aparece mas manobras/fim de rota não são detectados: restaurar
   manualmente. Com acesso, conferir manobras e restauração após o atraso. Modo manual não deve
   ativar ao abrir destino. Testar também modo Sempre, app ausente e falha ADB.
2. Restaurar e escolher **App de navegação (experimental)** antes de ativar. Iniciar rota e conferir
   se a tarefa com a rota aparece no `fission_bg_xdjaVirtualSurface` 1920×720, preservando destino.
   Comparar Maps/Waze, confirmar margens/instrumentos BYD e legibilidade. O navegador exige toque:
   não interagir com ele dirigindo. O log precisa confirmar pacote e display; se a firmware recusar,
   deve informar falha e permitir voltar à opção integrada, sem insistir com AutoContainer.
3. Com navegador no painel, esperar mais de 30 s: mapa integrado não deve tomá-lo. Testar Painel
   original, fim da rota, perda/recriação do display e novo pedido de rota. Conferir onde a tarefa
   externa fica após restaurar e se volta à central sem perder a rota; isso não foi garantido por JVM.
   Não há vigia do app externo, nem encerramento forçado dele. Se houver incompatibilidade, usar
   o integrado. Não considerar este modo validado apenas porque `am start` devolveu sucesso.
4. Diagnóstico → Info da central e Dados do carro → Copiar tudo antes/depois de Conceder permissões.
   Conferir bateria, combustível, autonomias e hodômetro com a tela BYD. Distinguir SDK ausente,
   método ausente, permissão negada e sentinelas inválidas. A permissão do app pode constar concedida
   e a chamada Binder continuar recusada. Registrar erro, sem tentar escrita nem nomes de propriedades.
5. Portas/travas: confirmar que o diagnóstico diz **indisponível**, sem sugerir estado fechado/travado.
   Para implementar leitura depois: obter SDK real só por leitura, confirmar parâmetros/constantes de
   `getDoorState(int)` e API de trava; testar cada porta e trava separadamente, com carro parado.
   Se SDK exigir uid 2000, implementar ajudante próprio somente leitura/autenticado antes da interface.

Pendências: primeiro teste real, capturas das novas preferências no emulador, lifecycle/projeção de
apps externos, SDK real e ajudante uid 2000. Não houve acesso ao carro, escrita CAN/SDK, mudança de
manifesto, launcher, telefone/clima, assinatura, versão ou publicação nesta etapa.

## Issue #9 — menu de navegação do painel e modo de debug (2026-10-23, não lançado)

Estudo em `ENGENHARIA_REVERSA.md`, §6; implementação em `ARQUITETURA.md`, "Menu do painel e modo de
debug". Feito: **modo de debug** (desligado por padrão) e **seguir o menu** (experimental, desligado),
que lê `INSTRUMENT_NAVI_TYPE` só pelo SDK (getter, por nome) e muda apenas o desenho da nossa tela.
Validação: 138 testes JVM, `lintRelease` 0 erros, `assembleDebug`. Emulador `dilink3` (display
simulado 1920×720): ligar o debug em Ajustes, linha de base, mudanças de settings e broadcasts com
horário, fotografia completa, Salvar arquivo e os quatro "Testar desenho" no painel (Pequeno no
centro, Simples sem mapa, OFF preto, volta ao normal) — captura em `docs/telas/7-modo-debug.png`. No
emulador não há SDK BYD ("SDK BYD não encontrado", como esperado). **Nada testado no carro.**

Hipóteses que o carro decide: (a) a DiLink 3 tem o registrador e o app consegue lê-lo pelo uid
dele; (b) 1 = OFF, 2 = Simples, 3 = Pequeno, 4 = Cheio; (c) no Pequeno o painel mostra o meio da
imagem; (d) existe algum aviso (setting, propriedade, broadcast, log) quando a opção muda.

Checklist no carro (carro parado, dono fazendo; só leitura):

1. Ajustes → Testes e ajuda → ligar **Modo de debug**; Conceder permissões pelo ADB (para o SDK do
   painel e o ADB local). Abrir **Abrir o registro do debug**: deve aparecer "linha de base gravada".
   Se aparecer "sem ADB", aceitar "Permitir depuração USB" e esperar 30 s.
2. Tocar em **Fotografia completa** e conferir se aparecem `com.example.amapservice` ou outro pacote
   Amap/AutoNavi, os serviços `autoservice`/instrument e quem escuta broadcasts de navegação.
3. Sem projeção ativa: no painel (botões do volante), trocar a opção para **Simples**; na central,
   tocar em **Simples** (marca no registro). Esperar ~6 s. Repetir para Pequeno, Cheio e OFF, e
   voltar à opção inicial. Repetir tudo com o mapa do Painel Livre no painel (Simular rota).
4. Ver o que apareceu entre as marcas: linha `[sdk] INSTRUMENT_NAVI_TYPE = …` (e se muda), `[sdk]`
   de outros getters, `★` em settings/propriedades/broadcasts/logcat. Se o SDK der "permissão BYD
   recusada", anotar (vai para o ajudante uid 2000). **Copiar** ou **Salvar arquivo** e mandar.
5. Se um broadcast novo aparecer só com o nome, tocar em **Escutar ação…**, colar o nome e repetir o
   passo 3 para ver os extras.
6. Só se o passo 4 mostrar o `INSTRUMENT_NAVI_TYPE` mudando com 1/2/3/4: ligar **Seguir o menu de
   navegação do painel**, ativar o painel e trocar as opções. Cheio = como sempre; Pequeno = mapa no
   meio (conferir se fica visível ou se o painel corta outro pedaço — anotar qual); Simples = só os
   cartões; OFF = preto. Os botões **Testar desenho** fazem o mesmo sem depender da leitura.
7. Desligar o modo de debug no fim (ele para o ADB sozinho em 30 min, mas o SDK segue lendo).

Não fazer: `service call autoservice …` à mão sem antes confirmar o número da transação na DiLink 3
(pode ser um setter nesta firmware), `setprop`, `settings put`, broadcasts adivinhados, códigos 17/39
no AutoContainer. Se o carro confirmar outro mapeamento, ajustar `PanelMenu.modeOf` e o teste.

**Resultado no carro (2026-10-23, DiLink 3.0, Android 10).** (a) o registrador existe, mas o SDK
recusa no uid do app ("permissão BYD recusada"); pelo ADB local (uid 2000)
`service call autoservice 5 i32 1007 i32 1086337074` responde `Result: Parcel(00000000 0000000N …)`;
(b) confirmado pelo dono trocando o menu: 1 = OFF, 2 = Simples, 3 = Pequeno, 4 = Cheio; erro aparece
como 0xffffd8e5. **v0.5.1 (teste)**: o `PanelMenuReader` lê pelo ADB quando o SDK recusa (só esse
comando, só leitura, só Android 10) a cada 1 s com o painel aberto e "Seguir o menu" ligado.
Conferir no carro: ligar **Seguir o menu**, ativar o painel, trocar Simples/Pequeno/Cheio/OFF e ver
no registro do debug `[menu] ADB: bruto=N … → modo` e o desenho mudar em ~2 s; (c) Pequeno no meio
ainda é hipótese.

## Issue #15 — discador liga direto pelo celular (2026-10-23, não lançado)

No carro (v0.4.1) a ligação ia para o app de telefone da BYD. Agora os caminhos diretos vêm antes
(conta HFP no Telecom → `ACTION_CALL` do Telecom → cliente HFP → `placeCall` padrão) e os que abrem app
ficam de reserva (`ARQUITETURA.md`, "Telefone"). Validação: 143 testes JVM, lint 0 erros; emulador:
primeiro Ligar pede a permissão "Telefone", depois `CALL_INTENT` → tela de chamada → `CALLING` em 3 s,
registro do debug sem o número. O emulador não tem HFP: os caminhos 1 e 3 só o carro confirma.

Hipóteses: (a) a DiLink 3 usa o `HfpClientConnectionService` do AOSP (ou outra conta "bluetooth") e o
Telecom a lista; (b) na v0.4.1 o `ACTION_CALL` era atendido pelo app da BYD, ou faltava `CALL_PHONE` e
caía no `ACTION_DIAL`; (c) mesmo ligando direto, a tela de chamada da BYD aparece (é o InCallService
dela) — isso é esperado; o que muda é não precisar discar de novo.

Checklist no carro (carro parado, celular pareado, número conhecido que não seja de emergência):

1. Ajustes → Conceder permissões pelo ADB (deve mostrar ✓ em `CALL_PHONE` e `READ_PHONE_STATE`) e
   ligar o Modo de debug.
2. Telefone → digitar o número → Ligar. Se pedir permissão, Permitir. Anotar: a chamada começou sem
   discar de novo? Saiu pelo som do carro? Apareceu a tela da BYD?
3. Abrir o registro do debug e copiar as linhas `[telefone]`: quem atende o `ACTION_CALL`, contas do
   Telecom, cliente HFP, ordem, caminho usado e "resposta do sistema 3 s depois".
4. Fotografia completa → seção `# telecom` e `# serviços`: anotar a conta do Bluetooth e serviços de
   telefone da BYD (pacotes com bt/phone). Se a conta não for reconhecida, ajustar `CallAccounts.isBluetooth`.
5. Se a resposta for `NOT_CONFIRMED` sem ligar, tocar em App do carro (reserva) e mandar o registro.

## Interface Aura na tela central (2026-10-23, não lançado)

Pedido do dono: refazer a tela inicial no padrão visual **Aura** da designer
(`docs/design/aura-launcher/`), substituindo o cockpit na central e, por decisão da rodada 2,
redesenhando também o painel de instrumentos no padrão Aura. Mantidas todas as funções, o carro 3D e a telemetria só
leitura. Mapeamento, inventário conferido e decisões pendentes em `docs/INTERFACE-AURA.md`.
Validação: 223 testes JVM (14 novos em `AuraTest`), `lintDebug` 0 erros, `assembleDebug`, emulador
(claro, escuro, em pé, cada janela, sem animações). Prints: `docs/telas/aura-*.png`.

Checklist no carro (parado; só olhar e tocar na central):

1. Instalar por cima e abrir: conferir se textos, atalhos e dock têm o tamanho certo na DiLink 3
   (densidade própria do desenho 1280 × 720) e se a fonte nova aparece com acentos.
2. Tocar em cada atalho (Navegação, Bateria, Climatização, Mídia) e no botão Painel do dock: a janela
   abre perto do dedo, a seta aponta para o atalho, fecha no X, tocando fora, no mesmo atalho e em
   Início. Nunca duas abertas.
3. Com "Ler dados do carro" ligado: abrir uma porta, o porta-malas e um vidro → chips em laranja com a
   palavra certa, carro na vista de cima; fechar tudo → "Tudo fechado"; conferir a trava. Conferir que
   clima e bancos só mostram (nenhum botão muda o carro) e se os modos AUTO/A/C aparecem coerentes.
4. Janela do painel: Mostrar no painel / Voltar ao painel original funcionam como o cartão antigo.
5. Busca: Casa/Trabalho, uma categoria e um endereço abrem a rota no app escolhido; rota nativa
   aparece no mapa da busca. Mídia: tocar/pausar/próxima e volume com o Bluetooth do celular.
6. Botão Claro/Escuro e Ajustes → Tema (automático) voltam a funcionar como antes.

Rodada 2: 238 testes JVM passando, lintDebug sem erros (76 avisos), assembleDebug concluído.
Mapas com vias mais largas, seta larga e rota accent com contorno; painel com fontes e tokens Aura.
Somente leitura; aberturas externas antigas mantidas e listadas em `INTERFACE-AURA.md`.

Checklist adicional (depende do dono; nenhum acesso ao carro nesta rodada):

7. Conferir painel claro/escuro em Simples, Pequeno, Cheio e OFF; números legíveis, cartões e aviso
   de telemetria dentro da área segura, sem cobrir instrumentos BYD. Conferir contorno debug.
8. Conferir rota nativa nos dois mapas, seta e larguras em diferentes zooms, sem perder o contorno.
9. Decidir se as funções antigas que abrem apps externos devem ser substituídas por telas internas.

## Fase 1 — Primeiro teste no carro

1. Instalar `PainelLivre-v0.3.0-release.apk`, aceitar "Permitir depuração USB" e tocar em **Configurar**
   no aviso em cima do carro (ou Ajustes → Conceder permissões pelo ADB).
2. Ajustes → Diagnóstico → **Info da central** → **Copiar tudo** (registra `fission_single_os`, `inswver`, nome do
   display do painel, serviço `AutoContainer`, identificação do modelo e decisão do tamanho do painel).
3. Ajustes → **Simular rota** com o carro parado: o painel liga? A tela abre pelo shell em tela cheia? (Log do app.)
   Na 1ª ativação o automático não manda o código de tamanho (painel ainda não medido) — é o
   comportamento do DashCast, que já funciona no carro. Na 2ª ativação, se o modelo for reconhecido
   como Song Plus e o painel medir 1920×720, manda o 30 antes do 16/35, como o SealNav. Comparar as
   duas. Se o modelo não for reconhecido, escolher 12,3" (código 30) nas Configurações e repetir.
   Se algo parecer errado: **Painel original** e Configurações → Tamanho → "Não enviar".
4. Conferir se os cartões do painel ficam livres dos instrumentos da BYD (faixas de cima e de baixo,
   cantos curvos, avisos dos lados, assistente de faixa). Com o modo de debug ligado aparece o contorno
   rosa da área segura (padrão Song Plus 230/90/230/230); se algo da BYD entrar nele, Ajustes → Área
   segura → ajuste fino do lado (positivo afasta da borda) e mandar foto com os valores. Conferir a tela central deitada e em pé, de dia e de noite.
5. Rota real no Google Maps: conferir seta/distância/rua (o parser foi testado com textos imitados).
6. Conferir o mapa na GPU do carro (no emulador apareceram traços amarelos que parecem artefato do
   SwiftShader). Diagnóstico mostra os quadros/s do mapa: andando deve ficar perto do limite (60 ou 30);
   no emulador ficou em ~15 porque o PC renderiza por software.
7. Desligar e ligar o carro: o Diagnóstico mostra "Último boot recebido"? O serviço sobe sozinho?
   Se não, testar "Usar como tela inicial".
8. **Central (issues #7, #3, #4)** — só o carro confirma:
   - *Instalar (v0.4.1)*: o APK instala pela central sem "Fail in installation of desktop apps"? Se
     instalar, confirma a hipótese (a BYD recusa APK com launcher ativo no manifesto). Se ainda falhar,
     anotar a mensagem exata e o logcat da instalação (`PackageManager`, `PackageInstaller`).
   - *Tela inicial*: no Diagnóstico → Info da central, anotar `tela_inicial=` e a lista `launchers:`
     (antes do botão o Painel Livre **não** deve aparecer; o alias vem desligado). Tocar em "Usar como
     tela inicial" (agora deve aparecer na lista) e depois no Início da BYD (botão
     da tela e do volante, se houver): abre o Painel Livre? Se a BYD abrir o launcher dela mesmo assim,
     é porque o botão Início da ROM chama o pacote direto (sem passar pelo HOME do Android) — anotar o
     pacote do launcher dela e o que aparece no logcat ao tocar (`ActivityTaskManager: START`). Depois
     do boot, a central abre direto no Painel Livre? Conferir se aparece o seletor de launcher sem
     pedir. "Voltar ao launcher da BYD" volta mesmo?
   - *Telefone*: com o celular pareado, discar um número conhecido (não emergência) com o carro
     parado. Anotar qual caminho o Log do app mostra (`Telefone: chamada por …` e `depois de … →`) e
     se a ligação sai pelo som do carro (roteiro completo na seção da issue #15). O Diagnóstico lista `telefone:` (apps que respondem a `ACTION_DIAL`) e pacotes
     com `phone`/`bluetooth`; se a BYD tiver um app próprio que aceite o número por intent, levar o
     pacote para `PhoneApps`/`CallPlan`.
   - *Tempo*: tocar no tempo, conferir horas/dias e a leitura de dia e de noite, deitada e em pé.

Opcional, no mesmo dia: com o **SealNav** projetando, guardar `logcat`, `dumpsys display`,
`dumpsys activity activities` e `ps -A | grep app_process` (só observação, nada é alterado) — confirma
na prática a sequência de ativação e o código de tamanho que ele manda neste carro.

Se der tempo (prepara a Fase 4b, só leitura): `adb pull /system/framework/bydauto.jar` (e
`bydautosdk.jar`/`android.hardware.bydauto.jar`, se existirem) para conferir assinaturas e constantes.

---

## Fase 2 — Engenharia reversa do SealNav

### Regras (sala limpa)

- O objetivo é entender **mecanismos e protocolos** do carro (interoperabilidade). Os achados vão para
  `SEALNAV_RE.md`; o código do Painel Livre continua escrito do zero.
- **Não** copiar código, imagens (carro 3D, cursores, guias), gravações de voz, chaves de API ou perfis.
- **Não** usar os servidores do SealNav (`sealnav.com`, `relay.sealnav.com`) nem contornar a ativação
  (`ActivationActivity`). Os termos de uso dele pedem "não burlar o licenciamento" e não falam de
  engenharia reversa.
- Preferir os componentes abertos que o próprio SealNav usa (MapLibre BSD-2, Ferrostar BSD-3, Filament
  Apache-2), com a licença de cada um.

### Triagem já feita (estática, 2026-10-22)

| Item | Observado |
|---|---|
| Identidade | `com.sealnav` 0.37.512 (vc 1922), minSdk 25 / target 29, SHA-256 `7695e342…85b5`, assinatura **debug comum** (`CN=Android Debug`) |
| Código | pacote interno `com.example.bydcluster.nav`, ofuscado (R8), strings legíveis (`ClusterManager.kt`, `ClusterLauncherImpl.kt`) |
| Mapa e rota | MapLibre (`libmaplibre.so`) + **Ferrostar** (`libferrostar.so`, núcleo Rust via JNA) com Valhalla (`valhalla1.openstreetmap.de`) e OSRM público → ele tem **rota própria**, por isso desenha a linha da rota no painel |
| 3D | Filament + glTF (carro girando no launcher) |
| Privilégio | mesmo caminho do DashCast: ADB local `localhost:5555` + ajudantes em `app_process` como uid 2000 |
| Ajudantes uid 2000 | `assets/carlisten.dex` (`CarListen`: AC, ADAS, portas, luzes, pneus, velocidade, carga, Instrument…) e `assets/camrelay.dex` (`CamProbe`: câmeras via `/system/framework/bmmcamera.jar`, `mirror`, `dashcam`, `stream`, `hls`, `adbkeys`) |
| Robustez do painel | strings `cluster_auto_heal`, `cluster_first_frame_recovery`, `cluster-freeze-watch`, `cluster_phase_lock`, `cluster_pace`, `clusterFps`, `applyFpsReduction`, "came back to the cluster twice in 30000ms", `cluster_profile_v3.json` |
| Renderização | "direct surface: no first frame in 2000 ms, shown over the window's copy" → desenha direto numa Surface com fallback para janela |
| Serviços | `NavCastProjectionService` (foreground location), `GnssHelperService` (processo próprio `:gnsshelper`), `RestarterJobService`, `PollStallReceiver`, `BootReceiver` (directBootAware), `BydSetupAccessibilityService`, leitor de notificações de mídia |

### Perguntas a responder (cada uma com a evidência que precisa)

1. **Por que o painel não cai / se recupera?** Sequência de comandos `sendInfo`, auto-heal, recuperação do
   primeiro quadro, vigia de congelamento, "phase lock", "claim back" quando outro app toma o painel.
   *Evidência:* strings + chamadas por método (estático) e logcat com o SealNav no carro (dinâmico).
2. **Como desenha no painel?** Activity, Presentation ou Surface direta? Limite de fps (`clusterFps`,
   `applyFpsReduction`): provável motivo de rodar mais leve. *Evidência:* chamadas a `Surface`,
   `VirtualDisplay`, `Presentation` e `setMaximumFps` por classe.
3. **Como mantém velocidade/bateria da BYD visíveis?** Geometria, margens ou modo do Qt. *Evidência:* foto
   do painel + `dumpsys display` com o SealNav ativo.
4. **O que é `cluster_profile_v3.json`?** Perfil por carro/firmware, embutido ou baixado. *Evidência:*
   strings e fluxo de rede (sem usar o servidor deles).
5. **GPS em processo separado (`GnssHelperService`):** contorna alguma falha do GNSS da BYD?
6. **Dados do carro (`CarListen`):** quais devices e feature IDs, e como passa pela checagem de permissão
   do SDK rodando como uid 2000. Comparar com o `CanWriteVerbs` do DashCast. Só leitura no Painel Livre.
7. **Configuração automática:** o que o `BydSetupAccessibilityService` e o comando `adbkeys` fazem (risco:
   mexer em chaves ADB do sistema; revisar antes de copiar qualquer ideia).
8. **Câmeras no painel** (`CamProbe` + `bmmcamera.jar`): viável? (Fase 5.)

### Ferramentas

- Já disponível: inventário do REA (`open_binary`/`inspect_artifact`), parsers próprios de manifesto/DEX
  em `tools/re/` (strings e chamadas por método), REA via ADB no carro (`list_adb_devices`,
  `read_adb_logcat`, `inspect_adb_display`) quando o notebook estiver conectado.
- JADX 1.5.6 instalado em `~/tools/jadx` (aprovado em 2026-10-22; SHA-256 conferido). As ferramentas
  Android do REA continuam desligadas (exigem o JAR headless MCP configurado no servidor do REA).

### Entregável

`SEALNAV_RE.md`: cada pergunta como respondida, parcial ou aberta, com a evidência e a fonte, mais uma
lista priorizada do que reimplementar na Fase 3.

---

## Fase 3 — Melhorias no Painel Livre (a partir de `SEALNAV_RE.md`)

Itens 1–6 feitos na v0.2.0 e testados no emulador (detalhes em `SEALNAV_RE.md`, "Levado ao Painel
Livre na v0.2.0"):

1. ✅ ADB persistente com novas tentativas no boot.
2. ✅ Ativação: limpeza de estado velho → código de tamanho só quando seguro → 16 → 35, pausas de 1 s.
   Regra nova: o automático só manda 30 com modelo Song Plus/Seal U **e** painel medido de 12,3";
   escolha manual bloqueada se contradizer o painel medido; 1 nunca.
3. ✅ Tela aberta pelo shell com `--windowingMode 1`, depois janela livre, depois direto.
4. ✅ Carro/câmera por quadro, limite de fps 60/30 andando e 5 parado, vigia de primeiro quadro,
   redesenho e recriação da superfície.
5. ✅ Convivência com outros apps no painel (cede após 2 tomadas em 30 s).
6. ◐ Autoiniciar: boot registrado no Diagnóstico, permissões de segundo plano pelo ADB, recomendação
   de virar tela inicial. Falta descobrir onde a BYD guarda a lista de autoinício (no carro).
7. Navegação própria com **Ferrostar + MapLibre**: busca (Photon/Nominatim), rota com linha no painel,
   recálculo. Os servidores públicos do Valhalla e do OSRM têm política de uso justo; para uso diário,
   avaliar instância própria ou provedor com chave.
8. Dados do carro por ajudante uid 2000 próprio, só leitura e autenticado; nunca `adbkeys`.
9. Manter Google Maps/Waze como fonte alternativa (o que já existe).

## Fase 3b — Interface no estilo do SealNav (v0.3.0)

Referência: layouts do APK (`activity_cockpit`, `activity_cluster`, `cc_sheet_*`) e fotos públicas em
sealnav.com (só olhadas; nada copiado — ver `SEALNAV_RE.md`, "Interface").

Feito: central com barra de cima (saudação, hora, sinais, temperatura), carro (ilustração própria ou
foto do motorista) com autonomia e tempo, cartões Mídia/Bateria/Painel/Destino, barra de baixo com
abas e atalhos; aba Navegação com busca (Photon), Casa/Trabalho/recentes e mapa; temas dia/noite;
tela em pé. Painel com cartão de manobra e de chegada em cima à esquerda da área segura (`SafeArea`,
v0.5.1), carro no centro dela a y 360, mapa claro/escuro, sem relógio (a BYD já mostra).

Diferenças que restam em relação ao SealNav:
1. **Controles de conforto na barra de baixo** (clima de cada lado, bancos, vidros, porta-malas): aprovado
   pelo usuário, virou a **Fase 4b** (depois do primeiro teste no carro).
2. Carro 3D girável (o SealNav usa Filament + modelo próprio); aqui é ilustração 2D ou foto.
3. Linha da rota, trânsito, radares e voz: vêm do Maps/Waze (no celular/central), não do painel.
4. Câmeras, Guard, dashcam (Fase 5).

## Fase 4 — HUD/painel via CAN

Registradores já documentados em `ENGENHARIA_REVERSA.md` (DashCast). Exige o ajudante uid 2000; testar no
carro com o OEM nav desligado e o HUD em modo navegação.

## Fase 4b — Controles de conforto na central (depois do primeiro teste no carro)

Pedido do usuário (2026-10-22): fechar a última diferença de interface com o SealNav — a barra de baixo
com o clima do motorista e do passageiro, e as folhas de clima, bancos e vidros.

**O que o SealNav oferece** (site + achado I do `SEALNAV_RE.md`): clima (liga/desliga, temperatura de
cada lado, ventilação, recirculação, automático, desembaçador, resfriamento máximo, compressor), bancos
(aquecimento e ventilação, dois níveis, motorista e passageiro), vidros (abertura em %), teto solar e
cortina (Seal U), porta-malas (abrir/fechar). Trava das portas só exibida. Confirmação única na primeira
vez. A escrita passa por um processo uid 2000 com protocolo autenticado.

**O que já sabemos sem o carro**: o BYD Navigator traz os moldes do SDK da BYD com as assinaturas de
leitura e escrita (lista em `ENGENHARIA_REVERSA.md`, "Conforto"): `BYDAutoAcDevice` (clima),
`BYDAutoBodyworkDevice` (vidros, teto), `BYDAutoSettingDevice` (bancos, porta-malas elétrico). Falta o
significado dos parâmetros (lado, nível, valores), que vem das constantes do SDK do carro.

**Regras de segurança** (valem para o código e para os testes):
- Só conforto, numa **lista fechada** de métodos dentro do próprio ajudante: qualquer outro pedido é
  recusado lá, não só na tela. Nunca: travas, portas, câmbio, freio, direção, modos de condução, ADAS,
  luzes externas, CAN cru.
- Vidros, teto e porta-malas **só com o carro parado** (o ajudante lê velocidade/marcha antes) e só
  enquanto o dedo está no botão ou com confirmação; nada automático.
- Confirmação única na primeira vez e um interruptor nos Ajustes que desliga todos os controles.
- Depois de cada comando, **ler o valor de volta**; se não mudou em ~2 s, avisa e não insiste.
- Limite de frequência por função (ex.: 2 comandos/s), para não inundar o barramento.
- Ajudante próprio (`app_process` como uid 2000, lançado pelo ADB local): fala só com o Painel Livre
  por socket local com token e desafio (HELLO/PROOF com nonce), sem porta de rede, e termina se o app
  sumir. Nada do código do SealNav (`carlisten.dex`, `VehicleDaemon`) e nada de `adbkeys`.

**Passos**:
1. *Antes do carro:* extrair dos moldes do Navigator as constantes (lados, níveis, códigos de retorno)
   e montar a lista fechada de métodos.
2. *No carro, só leitura:* puxar `/system/framework/bydauto*.jar` (para conferir assinaturas e
   constantes reais) e mexer nos botões da BYD enquanto o Diagnóstico lê os valores, para mapear cada
   função.
3. Ajudante uid 2000 só leitura (compartilhado com a Fase 4): barra de baixo mostrando o estado real
   do clima e dos bancos.
4. Escrita, **uma função por vez**, cada uma testada no carro parado: temperatura → liga/desliga →
   ventilação, recirculação e desembaçador → bancos → vidros → porta-malas.
5. Interface: barra de baixo igual à do SealNav (clima dos dois lados, Início no meio) e folhas de
   Clima, Bancos e Vidros.

**Pronto quando**: cada controle muda o carro, a tela mostra o valor lido de volta e nada que exige
carro parado responde com o carro andando.

## Fase 5 — Extras

Câmeras no painel ao dar seta, voz em português sem TTS da central, launcher com carro 3D (Filament).

## Issue #14 — rota nativa opcional (não lançado)

Implementada a primeira versão da Fase 3, item 7, com OSRM HTTPS + MapLibre e acompanhamento
Kotlin, sem Ferrostar: escolha Painel Livre/Maps/Waze, busca/Casa/Trabalho/recentes, compartilhamento
e intents `geo:`/`google.navigation:`, linha azul, próxima manobra, restante/chegada e recálculo.
A decisão do dono em #14 amplia a meta anterior, mantendo os apps externos. Não lê a linha da rota
do Maps/Waze: o percurso nativo é independente e pode ser diferente. Sem voz/trânsito/offline.
O menu Simples/Pequeno/Cheio/OFF e seu debug (#9/#15) pertencem ao trabalho separado do Claude.

Checklist adicional no carro (pelo dono, começando parado; sem acesso ADB do agente):

- [ ] Instalar build desta etapa; em Navegação escolher **Painel Livre**, buscar um endereço e tocar
  no resultado. Com localização permitida/GPS recente e internet, conferir cálculo, linha azul e
  manobra no painel, sem precisar abrir Maps/Waze nem liberar suas notificações.
- [ ] Repetir Casa, Trabalho e recente. Verificar modo Só com rota / Sempre; em Manual a rota fica
  pronta mas só Mostrar no painel ativa. Com conteúdo experimental externo selecionado, a rota
  nativa deve abrir o integrado. Conferir log sem ativação AutoContainer duplicada.
- [ ] Compartilhar coordenadas e endereço; testar `geo:0,0?q=lat,lon`, `geo:lat,lon` e
  `google.navigation:q=...`. Confirmar escolha entre as três fontes; texto deve permitir escolher
  resultado correto. Link curto não resolvido deve avisar, sem abrir destino errado.
- [ ] Com navegação real e observação segura, conferir manobra antes/depois da curva, rotatória,
  distâncias, hora de chegada, linha e carro alinhados. Comparar cruzamentos, retornos e pistas
  paralelas; não tratar a heurística simples como validada apenas pelos testes JVM.
- [ ] Desvio seguro: após >8 s fora da linha e >30 s do cálculo anterior, conferir recálculo;
  sem rede, aviso e instrução de fora da rota, sem repetir pedidos sem limite. Retomar internet.
- [ ] Sem permissão/GPS antigo/impreciso: não iniciar orientação inventada. Sem rede/OSRM recusando:
  erro visível; Maps/Waze permanecem selecionáveis. Novo destino deve cancelar cálculo anterior.
- [ ] Abrir outro app na central e voltar: orientação continua. Recriar tela/trocar tema: linha volta.
  Sumir display: Activity fecha se cair na central; vigia não envia AutoContainer novamente.
- [ ] Notificação de Maps/Waze não deve tomar a orientação nativa. Abrir rota externa explicitamente
  deve encerrar a nativa. Tocar na rota ativa da aba Navegação → Encerrar deve limpar linha/cartões
  e devolver o painel após o prazo. Chegada deve encerrar automaticamente.
- [ ] Matar processo/serviço e abrir: sessão anterior não é retomada automaticamente; selecionar
  destino novamente. Conferir impacto de internet/GPU na central.

Emulador/capturas pendentes: o AVD `dilink3` não iniciou neste ambiente (sem `/dev/kvm`).
Nenhum teste no carro nesta etapa. Não alterados comandos seguros, CAN/SDK, versão ou assinatura.

Validação da #14: 129 testes JVM passando (12 novos), `lintRelease` com 0 erros/109 avisos e
`assembleDebug` concluído. Consulta OSRM com coordenadas públicas de exemplo retornou `Ok`.
Entrega de intents, GPS/recálculo em execução e projeção precisam do roteiro Android/carro acima.

### Regressão da revisão local v0.5.1 (2026-10-23)

- Cobertos na JVM: ADB desconectado e recuperação, shell com saída não zero, serviço ausente,
  Parcel truncado/inválido, valores fora de 1..4, preferências antigas aditivas e ajustes extremos.
- Corrigidos overflow das margens e cartões que alcançavam o carro em áreas estreitas.
- Pendente no emulador/carro: conferir cartões e carro com ajuste lateral +400 e no modo Pequeno,
  e desligar/religar o ADB local observando o retorno ao desenho normal e a recuperação do menu.
  O agente não acessou o carro; nenhum comando novo de carro foi adicionado.

Validação local: 183 testes JVM, 0 falhas; lintDebug com 0 erros e 114 avisos; assembleDebug concluído.

## Issue #16 — revisão local da telemetria (2026-10-23)

- [x] Revisar b622959/99d7782 e concluir simulação debug e documentação.
- [x] Lista restrita de leitura autoservice 5/7; teste recusa 6 para todos os sinais/protocolos.
- [x] Regressões de Parcel hex/multilinha/ASCII e sentinela indisponível.
- [x] testDebugUnitTest: 174 testes sem falhas; lintDebug: 0 erros, 112 avisos.
- [ ] Validar cartão/animações e capturar telas no emulador.
- [ ] Dono conferir IDs, escalas e formato da transação 7 na firmware do Song Plus.
  Roteiro e tabela (itens **a confirmar**) em [TELEMETRIA.md](TELEMETRIA.md).
  Nenhum acesso ao carro pelo agente nesta etapa; nenhuma escrita autorizada.

### Carro 3D central (não publicado)

Renders locais e overlays somente visuais; detalhes e coordenadas em TELEMETRIA.md.
Conferir posição da tampa traseira direita e limites visuais de pneus com o dono, sem novos comandos.

Validado no emulator-5580: padrão, porta aberta, carregando e pneu baixo.
180 testes JVM; lintDebug 0 erros/114 avisos; assembleDebug concluído. Sem teste no carro.

## Integração 0.5.2 (teste), versionCode 8

Consolida menu ADB e área segura da 0.5.1 com telemetria só leitura e carro 3D.
Aviso/estado de telemetria limitado à área segura, no canto superior direito; testes incluem
modo Pequeno e ajustes extremos. Transação 6 continua bloqueada. Nenhuma mudança de assinatura.
Validação conjunta local: 209 testes JVM sem falhas, lintDebug com 0 erros/116 avisos e
assembleDebug concluído. Conferir visualmente navegação + aviso de telemetria,
modo Pequeno, ajuste fino e contorno debug no emulador e depois com o dono no carro.

## Rodada 3 Aura — comandos internos (não lançado)

Decisão explícita do Paulo substitui o plano de só leitura: primeira implementação pelo ADB local,
sem esperar o ajudante próprio; ajudante autenticado continua evolução futura. Nenhum acesso ao
carro nesta rodada. Catálogo e evidências em [COMANDOS.md](COMANDOS.md).

Checklist **apenas pelo Paulo, carro parado em P**, um comando por vez:

- [ ] Salvar diagnóstico/firmware/protocolo e estado inicial. Conferir controles originais disponíveis.
- [ ] Chave geral desligada: tentar clima/dock/mídia e conferir rejeição no Log do app, sem atuação.
- [ ] Ligar “Permitir comandos ao carro”; manter individuais desligados e conferir rejeição.
- [ ] No Diagnóstico liberar **somente temperatura motorista**; escolher grau inteiro próximo do atual,
  confirmar envio e comparar painel original, GET relido e log. Repetir passageiro separadamente.
  Meio grau permanece bloqueado; não experimentar multiplicadores ou float no carro.
- [ ] Desligar individual anterior e testar ventilador 0–7, um valor próximo do atual por toque.
- [ ] Separadamente: energia do clima → A/C → AUTO → recirculação → desembaçador. Observar efeito,
  comparar antes/depois no controle original e salvar GET cru. Não insistir se ficar inconclusivo.
  Não reproduzir automaticamente as sequências de inicialização do app de referência.
- [ ] Confirmar ausência de repetição em falha ADB; não provocar desconexão durante movimento.
- [ ] Separadamente liberar mídia play/pausa, próxima, anterior e volume; testar sessão real, sem sessão
  e rádio original. Registrar o que o rádio atende; sintonia FM/AM ainda não implementada.
- [ ] Bancos/porta-malas/vidros/teto devem continuar bloqueados mesmo com preferências adulteradas.
  Antes de liberar código futuro, resolver contratos de escrita, sequência e escalas. Testar P
  ausente/antigo/simulado e fora de P só em testes locais; não movimentar o carro para este roteiro.
- [ ] Bateria/carga seguem leitura. Nenhum EV/HEV/V2L/agendamento inventado.
- [ ] Ao concluir, desligar todas as liberações e a chave geral; salvar log e observações.

Não declarar modos confirmados só porque o Binder respondeu. A releitura dos modos ainda tem escala
incerta; registrar resposta e levar para testes JVM. Nenhum comando de travas, portas, câmbio,
freio, direção, ADAS ou CAN cru faz parte desta implementação.

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

### Conferência pendente — 0.5.4

- No carro: conferir cartões compactos (Área segura desligada), os quatro modos do menu e o
  seletor manual em Ajustes; obter AppLog com bruto/origem/erro e desenhos aplicados.
- Comandos enviam ao tocar; chaves com evidência ligadas por migração única. Conferir uma função
  por vez no carro parado; hipóteses continuam bloqueadas. Não houve acesso ao carro nesta etapa.

## Issues #20 e #22 — diálogo da climatização e tela cheia (2026-10-29, branch `fix/ac-audio-tela-cheia`)

Fotos do Paulo no carro, feitas depois da 0.5.4. Causa raiz e correção de cada uma em
`docs/INTERFACE-AURA.md` ("Correções 29/10/2026"); detalhe do fio em `docs/COMANDOS.md`.
A issue #21 (toast de áudio/rádio, mesma rodada de fotos) é do Codex, branch `fix/audio-toast`
— não mexida aqui.

- **#20**: AUTO/A-C/recirculação/desembaçador/energia viram toggle de um toque
  (`CarCommands.toggleValue`); ventilação ganhou +/- (`CarCommands.fanStep`) em vez da lista
  0…7. Temperatura continuava por lista nesta rodada (16…30°, fora do pedido original da issue;
  virou toque direto na 0.5.6, ver seção abaixo).
- **#22**: `Theme.PainelLivre.Aura` ganhou `android:windowFullscreen=true` (`values/aura.xml`);
  a barra de status do Android deixa de ser reservada e a Home passa a ocupar essa altura. A
  barra nativa inferior do carro não é afetada (fora da nossa janela).
- Validação local: `./gradlew testDebugUnitTest lintDebug assembleDebug`, 261 testes JVM
  (3 novos), zero falhas, lint zero erros. Testado no carro em 29/10 (ver seção 0.5.6 abaixo).

## Correção #21 — áudio sem mensagens rotineiras (29/10/2026)

- [x] Suprimir Toast de releitura de mídia/volume e de sessão sem confirmação na Aura.
- [x] Preservar ícones, volume, execução, log, bloqueios e avisos de falha/rejeição.
- [x] Impedir que a mídia escreva seu retorno no estado da janela de climatização.
- [x] Regressões JVM de feedback e integração com `LauncherActivity`.
- [ ] Conferir no carro: tocar/pausar, próxima, anterior e volume sem mensagem rotineira,
  com o estado visual atualizado quando houver sessão. Testar também rádio BYD sem sessão Android.

Escopo apenas #21; #20 e #22 seguem em outro branch. Versão mantida em 0.5.4 / código 10.

## 0.5.6 — achados do primeiro teste no carro da climatização (2026-10-29, branch `fix/ac-audio-tela-cheia`)

Teste da v0.5.5-teste no carro de Paulo, DiLink 3.0, `adb` em `172.26.89.229:5555`, 29/10 ~15:07–15:10.

- [x] **Confirmado no carro:** diálogos sumiram (#20); `ac_fan` +/- "confirmado pelo estado relido".
- [x] **Bug AUTO** (raw do fio ≠ lógico do toggle, nunca confirmava e a UI ficava sempre "ligado"):
  decodificado com `CarCommands.toWire`/`fromWire`; `ac_auto` e `ac_recirc` ganharam
  `readbackKnown=true`. Detalhe em `docs/COMANDOS.md`.
- [x] **Toques rápidos não são mais rejeitados**: fila de 1 para toggles, debounce de 350 ms com
  soma de alvo para ventilação/temperatura/bancos.
- [x] **Temperatura, bancos e porta-malas também viram toque direto**, sem lista nem Toast de
  bloqueio ao abrir (varredura completa de `AlertDialog`/`setItems`/`chooseCommand` nos comandos do
  carro — `chooseCommand` foi removido). Bancos e porta-malas continuam bloqueados pelo
  `CarCommands` (hipótese, sem evidência) — só a UI deixou de perguntar antes.
- [x] Validação local: `./gradlew testDebugUnitTest lintDebug`, 271 testes JVM, zero falhas, lint
  zero erros (80 avisos).
- [ ] **Próximo teste no carro** (pendente, depende do Paulo): AUTO mostrando o estado real depois
  de ligar/desligar pelo app e pelo painel nativo; recirculação com o aviso "confirmado" (antes
  dizia "não confirmado" apesar de funcionar); toques rápidos em sequência no fan/temperatura sem
  nenhuma mensagem de "aguarde"; botões −/+ de temperatura motorista/passageiro; toque nos botões de
  banco/porta-malas (esperado: nenhum Toast de bloqueio, nenhum efeito real no carro, já que
  continuam hipótese); atalho de temperatura no dock abrindo a Climatização em vez de lista.
- [ ] Issue #24 (Waze no painel) é diagnóstico separado, não faz parte desta rodada.
