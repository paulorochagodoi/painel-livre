# Plano — Painel Livre

Atualizado: 2026-10-22. Estado: **v0.3.0** = Fase 3 itens 1–6 (v0.2.0) + **interface redesenhada no
estilo do SealNav** (central tipo cockpit e painel com o layout dele). 41 testes de unidade, testada
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
4. Conferir se os cartões do painel ficam livres dos instrumentos da BYD (faixas de cima e de baixo);
   se não, ajustar as margens nos Ajustes. Conferir a tela central deitada e em pé, de dia e de noite.
5. Rota real no Google Maps: conferir seta/distância/rua (o parser foi testado com textos imitados).
6. Conferir o mapa na GPU do carro (no emulador apareceram traços amarelos que parecem artefato do
   SwiftShader). Diagnóstico mostra os quadros/s do mapa: andando deve ficar perto do limite (60 ou 30);
   no emulador ficou em ~15 porque o PC renderiza por software.
7. Desligar e ligar o carro: o Diagnóstico mostra "Último boot recebido"? O serviço sobe sozinho?
   Se não, testar "Usar como tela inicial".

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
tela em pé. Painel com cartão de manobra e de chegada em cima à esquerda, carro no centro a ~59% da
altura, mapa claro/escuro, sem relógio (a BYD já mostra).

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
