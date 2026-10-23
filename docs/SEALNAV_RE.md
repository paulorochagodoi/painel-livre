# SealNav — engenharia reversa (por que funciona melhor que o DashCast)

Data: 2026-10-22. Alvo: `SealNav.apk` — `com.sealnav` 0.37.512 (vc 1922), SHA-256
`7695e342fb8d50161701aaa6f39aa88b160e546871cebc9e3198033be94085b5`, assinado com chave de debug comum.
Ferramentas: inventário REA, JADX 1.5.6 (SHA-256 do zip conferido com o GitHub), parsers próprios.
Análise **estática** (nada foi executado); itens marcados [aberto] precisam do carro.

Regras: este documento descreve **comportamento e protocolo**, não reproduz código. O Painel Livre
continua escrito do zero; nada de servidores (`sealnav.com`, `relay.sealnav.com`), perfis, imagens, voz
ou chaves do SealNav, e nada que contorne a ativação dele.

## Respostas

| # | Pergunta | Estado | Achado |
|---|---|---|---|
| 1 | Por que o painel não cai / se recupera | **respondida** | A, B, C, F, G |
| 2 | Como desenha no painel | **respondida** | E, F |
| 3 | Como mantém velocidade/bateria visíveis | **respondida (fotos públicas)** | os instrumentos da BYD são desenhados por cima da projeção; ver "Interface" |
| 4 | O que é `cluster_profile_v3.json` | **respondida** | D |
| 5 | GPS em processo separado | **respondida** | J |
| 6 | Dados do carro como uid 2000 | **respondida** | I |
| 7 | Configuração automática / `adbkeys` | **respondida** | H, K |
| 8 | Câmeras no painel | parcial | ajudante `CamProbe` via `bmmcamera.jar` (fase 5) |

## Achados

**A — Conexão ADB persistente.** Uma única conexão dadb com `localhost:5555` fica aberta e é
compartilhada sob trava. Na criação tenta até 15 vezes com 2 s de intervalo (cobre o boot, quando o adbd
ainda não subiu). Se um comando falha, fecha, reconecta uma vez e repete. *Painel Livre hoje:* abre uma
conexão por comando, sem novas tentativas.

**B — Sequência de ativação.** `service call <AutoContainer|auto_container> 2 i32 1000 i32 <código> s16 ""`
(mesmo protocolo do DashCast). Ordem: [limpeza, ver C] → **código de tamanho do painel** → 1 s → marca
"projeção ligada" → **16** → 1 s → **35**. Trava de segurança: recusa 1 e qualquer código fora de 2–63.
Restaurar = 18 → 0.

**C — Limpeza de estado velho.** Uma vez por execução do app: se ficou marcado "projeção ligada" de uma
sessão anterior e não há display de painel, manda 18 → 1 s → 0 → 1 s antes de ativar. Resolve o caso
"carro desligado com a projeção ligada". Pausas de 1 s entre comandos (o DashCast usa 3 s).

**D — Tamanho do painel por modelo.** Prioridade: valor escolhido pelo usuário → perfil do servidor
(chaves `supported`, `screen`, `proj_on`=16, `di_mode`=35; cache de 7 dias) → detecção local:
- por nome do modelo nas propriedades do sistema: Seal U / **Song Plus** / DM-i → **30** (12,3");
  Atto / Yuan → 29 (8,8"); Dolphin / Seal → 31 (10,25");
- pela geometria do display do painel (proporção ≥ 2 e altura < 1000: largura ≥ 1800 → 30, senão 29);
- senão assume 30 (confiança baixa — arriscado em painel 8,8").

O DashCast, por padrão, não manda o código de tamanho. **Provável causa principal da diferença no Song
Plus** (geometria correta do vídeo projetado). [aberto: confirmar no carro]

Detalhes conferidos depois (tabela de textos ofuscados decodificada só para ler nomes de
propriedades): o texto examinado é `ro.product.name` + `Build.MODEL` + `Build.PRODUCT` +
`Build.DEVICE` + `Build.FINGERPRINT`, em minúsculas, procurando os nomes acima por substring; a
tabela de modelos testa 12,3" antes de 10,25" (por isso "seal u" vence "seal"). Nas constantes, o 30
se chama `CMD_SCREEN_SIZE_SEAL_EU` e o 35 `CMD_DI40_MODE`. A ativação manda tamanho → 16 → 35 mesmo
quando o display do painel já existe. Ele também lê `persist.sys.car.type` em outro ponto (uso não
investigado).

**E — Abertura da tela no painel.** Pelo shell primeiro: `am start --display N --windowingMode 1` com
flags NEW_TASK|REORDER_TO_FRONT (ou NEW_TASK|MULTIPLE_TASK|CLEAR_TASK ao reiniciar). Se a tela cheia
falha, tenta janela livre (modo 5) e redimensiona a tarefa para o tamanho do display. Convivência: se
outro app volta ao painel 2 vezes em 30 s, o SealNav "sai da frente" em vez de brigar; o motorista pode
pedir de volta.

**F — Renderização suave.** Câmera e cursor do carro animados a cada quadro (Choreographer, no ritmo do
display), com casamento do GPS com a via (Valhalla "meili"), sensores de giro para o rumo, estimativa
sem GPS ("coast") e ritmador de quadros. Limite de fps no MapLibre: até 60 em movimento, **5 parado**.
Créditos do mapa: OpenStreetMap e TomTom (tráfego, via servidor deles). Rotas: Ferrostar + Valhalla/OSRM
públicos.

**G — Vigia de congelamento** (thread própria, a cada 1,5 s), ações escalonadas:
1. sem primeiro quadro em 10 s → reconecta a projeção (uma vez; padrão ligado);
2. thread principal parada > 5 s → registra;
3. carro parado (pose sem mudar por 5 s) → normal, nada a fazer;
4. andando e sem quadros > 5 s → força redesenho; sem resposta em 1,5 s → redesenha de novo;
5. persistindo → recria a superfície OpenGL, no máximo 3 vezes a cada 10 min (só com "auto-heal").

**H — Autoiniciar.** Um serviço de acessibilidade abre as configurações da BYD, acha a linha do SealNav na
lista de "autoiniciar" e liga o interruptor. Por isso ele sobe com o carro. *Painel Livre hoje:* depende
do BOOT_COMPLETED, que a BYD pode bloquear para apps fora dessa lista. [aberto: onde a BYD guarda essa
lista — talvez dê para ligar pelo ADB sem acessibilidade]

**I — Dados do carro como uid 2000.** Ajudante em `app_process` cria o contexto de sistema
(`ActivityThread.systemMain().getSystemContext()`) e o envolve num `ContextWrapper` que responde
"permitido" às checagens de permissão; então usa `BYDAuto*Device.getInstance(...)` e `registerListener`.
Leitura separada da escrita: a escrita (controles de conforto, luz ambiente) fica num daemon com
**protocolo autenticado** (HELLO/PROOF com nonce e token, PING/PONG, escrita que pode ser recusada).

**J — "GnssHelperService".** Processo separado que pede GPS por 60 s, grava quantos fixes vieram e o tempo
até o primeiro, e se encerra: aquecimento e teste do GNSS, não a fonte principal de posição.

**K — `adbkeys` (risco).** O ajudante de câmera, como shell, chama o `IUsbManager` para autorizar uma
chave ADB sem o aviso na tela (`allowUsbDebugging`) ou apagar **todas** as chaves autorizadas
(`clearUsbDebuggingKeys`). Não adotar: quem tiver esse caminho autoriza acesso total à central.

## Interface (para a v0.3.0 seguir o mesmo jeito de usar)

Fontes: estrutura dos layouts do APK e fotos públicas em sealnav.com (página inicial, "Cockpit" e
"Navegação no painel"). Só observação: nenhuma imagem, ícone, texto longo ou código foi copiado.

- **Central (`activity_cockpit`)**: imagem de fundo; barra de cima com avatar + saudação + estado da
  trava, hora + data no centro, sinais (4G, Wi-Fi, Bluetooth, GPS) e temperatura à direita; carro 3D no
  centro com a autonomia grande à esquerda (elétrica · combustão) e o tempo à direita; quatro cartões
  (mídia com anterior/tocar/próxima, bateria com barra, modo de condução, destino com Casa/Trabalho);
  barra de baixo com clima do motorista (liga, A/C, recirculação, desembaçador, ventilação, banco,
  temperatura), abas no meio (Navegação, Mídia, Telefone, Conectar, Apps, Configurações) e clima do
  passageiro. Folhas sobrepostas para clima, bateria, vidros, cenas, configurações, casa/trabalho.
  Temas dia e noite, cinco papéis de parede.
- **Painel (`activity_cluster`)**: MapView + uma View de desenho por cima. Nas fotos do Seal U: os
  instrumentos da BYD ficam por cima da projeção (faixa de cima com hora/P/ECO/temperatura, faixa de
  baixo com kW/bateria/autonomia/velocidade, alertas à direita). O cartão da manobra (quadrado azul com
  seta branca, distância, instrução, rua e "depois") fica em cima à esquerda, o cartão de chegada
  (chegada, distância, tempo) logo abaixo, o carro no centro embaixo e a placa de velocidade em cima. O
  site diz que ele desenha "dentro da faixa realmente visível, medida no carro" (a moldura esconde
  parte dos 1920×720). **Isso responde a pergunta 3**: a velocidade e a bateria continuam visíveis
  porque o Qt da BYD desenha por cima; o app só precisa deixar essas faixas livres.

## Levado ao Painel Livre na v0.2.0 (código próprio, testado no emulador)

| Achado | Como ficou no Painel Livre |
|---|---|
| A | `SelfAdb`: uma conexão compartilhada, reconecta uma vez se cair; `ensureReady` com 15 × 2 s no início do serviço (boot) e 3 tentativas antes de ativar; para na hora se a chave for recusada |
| B, C | `ClusterController.activate`: limpeza 18 → 0 uma vez por execução se a projeção ficou ligada sem display; tamanho → 1 s → 16 → 1 s → 35 (sempre, como o SealNav); `projection_powered` gravado antes do 16 e apagado ao restaurar |
| D | `PanelSize`: mais restrito que o SealNav — automático só manda 30 para Song Plus/Seal U **com** painel já medido em geometria de 12,3"; nunca pela geometria sozinha (um 10,25" de 1920×720 levaria o código errado); escolha manual bloqueada se contradizer o painel medido |
| E | Abertura pelo shell `am start --display N --windowingMode 1`, depois janela livre (5) + `am task resize`, depois `startActivity` direto; convivência com `YieldPolicy` (retoma 1 vez, cede na 2ª em 30 s; "Mostrar no painel" ou rota nova retomam) |
| F | `PoseSmoother`: carro segue em frente entre fixes (até 1,5 s), correção suave em 0,6 s, salto direto acima de 150 m, parado ignora variação < 8 m; câmera por quadro (Choreographer), zoom e margem suavizados; carro como View fixa no ponto focal; limite de fps 60 (ou 30) andando, 5 parado |
| G | `FreezeWatch`: sem primeiro quadro em 10 s → reabre a tela (1 vez a cada 10 min); thread principal parada > 5 s → registra; andando e sem quadros > 5 s → redesenha, de novo em 1,5 s, depois recria a superfície (máx. 3 a cada 10 min, opção "Recuperar o mapa") |
| H | Parcial: `BOOT_COMPLETED` registrado (Diagnóstico mostra se chegou), `MY_PACKAGE_REPLACED`, `RUN_ANY_IN_BACKGROUND` e bucket "active" pelo ADB. Sem serviço de acessibilidade. A lista de autoinício da BYD continua [aberto] |

Fora desta versão: I (ajudante uid 2000), J (aquecimento do GNSS), navegação própria (Ferrostar).

## O que levar ao Painel Livre (Fase 3, em ordem) — itens 1–5 feitos na v0.2.0, 6 parcial

1. ADB persistente com novas tentativas no boot (A).
2. Ativação: limpeza de estado velho (C) → código de tamanho só quando seguro (Song Plus/Seal U por
   modelo **ou** display medido 1920×720; nunca em 1280×480; opção nas Configurações) → 16 → 35, pausas
   de 1 s, registrar "projeção ligada" e apagar ao restaurar (B, D).
3. Abrir a tela pelo shell com `--windowingMode 1`, fallback janela livre (E); manter o caminho direto.
4. Cursor/câmera por quadro e limite de fps 60/5 (F); vigia com primeiro quadro em 10 s e redesenho (G).
5. Política de convivência com outros apps no painel (E).
6. Autoiniciar com o carro: instrução ao usuário ou ajuste via ADB, sem acessibilidade (H).
7. Se surgir um ajudante uid 2000 próprio: autenticado (I) e sem `adbkeys` (K).
8. Navegação própria com Ferrostar + MapLibre (F), avaliando a política de uso dos servidores de rota.
9. Controles de conforto (achado I e página "Cockpit" do site): Fase 4b do `docs/PLANO.md`.
