# Histórico de versões

Os APKs de release são assinados com a chave local do projeto (`CN=Painel Livre, O=Uso pessoal, C=BR`,
certificado SHA-256 `7290204a…13d9`) e instalam um por cima do outro. Eles não ficam no repositório.
Nenhuma versão foi testada no carro ainda: todos os testes foram no emulador (`docs/AMBIENTE.md`).

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
