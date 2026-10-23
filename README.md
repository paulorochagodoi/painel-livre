# Painel Livre

Alternativa aberta ao SealNav para BYD com DiLink 3 "dual-OS" (alvo: Song Plus DM-i 2025, multimídia
13.1.33). Feito a partir de engenharia reversa do BYD Navigator (Telenav), do DashCast e do SealNav —
veja [docs/ENGENHARIA_REVERSA.md](docs/ENGENHARIA_REVERSA.md) e [docs/SEALNAV_RE.md](docs/SEALNAV_RE.md).
Plano e próximos passos em [docs/PLANO.md](docs/PLANO.md).

> **Estado:** v0.3.0, testada só no emulador. O primeiro teste no carro ainda vai acontecer — use por
> sua conta e risco.

A ideia é ser o SealNav aberto: mesmas telas e o mesmo jeito de usar, com uma diferença de
propósito — **a rota vem do Google Maps ou do Waze**. Telas em [docs/telas](docs/telas).

O que faz:

- **Mapa no painel de instrumentos**, no layout do SealNav: cartão da próxima manobra em cima à
  esquerda (seta, distância, instrução, rua), cartão com chegada/tempo/distância embaixo dele e o
  carro no centro, tudo dentro da faixa que os instrumentos da BYD deixam livre. Mapa
  OpenStreetMap (OpenFreeMap, sem chave), claro de dia e escuro à noite.
- **Turn-by-turn do Google Maps e do Waze**: lê a notificação de navegação deles (seta, distância,
  rua, resumo).
- **Tela central estilo cockpit**: saudação, hora e sinais em cima; o carro (ilustração própria, ou
  uma foto sua) com a autonomia e o tempo; cartões de Mídia (o que está tocando, com
  anterior/tocar/próxima), Bateria, Painel e Destino (Casa/Trabalho); barra de baixo com Início,
  Navegação, Mídia, Telefone, Apps e Ajustes. Temas claro e escuro, tela deitada ou em pé.
- **Telefone e tempo**: discador próprio (liga pelo sistema ou abre o app de telefone do carro) e,
  tocando no tempo, a previsão das próximas horas e dos próximos dias (Open-Meteo).
- **Aba Navegação**: busca de endereço (Photon/OpenStreetMap), lugares guardados e recentes, mapa
  com a posição do carro; escolha Google Maps ou Waze e a rota abre nele.
- **Automático**: liga o mapa no painel quando uma rota começa e devolve o painel original da BYD
  alguns segundos depois que ela termina (configurável: sempre / só na navegação / manual).
- **Robustez no estilo do SealNav** (v0.2.0): conexão ADB única com novas tentativas no boot;
  ativação com limpeza de estado velho, código de tamanho do painel (só quando seguro) e pausas de
  1 s; tela aberta pelo shell em tela cheia; carro e câmera animados a cada quadro com limite de fps;
  vigia que redesenha/recria o mapa se travar; e convivência com outros apps no painel.
- **Diagnóstico**: mostra se a central é dual-OS ou single-OS, versão do MCU do painel, displays,
  serviço AutoContainer, decisão do tamanho do painel, fps do mapa, GPS e dados do carro, com botão
  de copiar.

O que NÃO faz (ainda):

- Setas no HUD de para-brisa / no painel via CAN. O protocolo está documentado, mas exige um processo
  auxiliar com uid 2000 e contexto de permissões do sistema — Fase 4 do plano.
- Controles de conforto (clima, bancos, vidros, porta-malas) como os do SealNav — Fase 4b do plano,
  depois do primeiro teste no carro.
- Não desenha a linha da rota no mapa do painel: o Google Maps/Waze não expõem a geometria da rota.
  A navegação própria (Ferrostar + MapLibre, com a linha no painel) é o item 7 da Fase 3.

## Requisitos no carro

1. DiLink 3 **dual-OS** (se o DashCast ou o SealNav já projetaram no seu painel, é o seu caso).
2. **ADB por TCP ligado** (porta 5555) — o mesmo requisito do DashCast/SealNav.
3. Google Maps e/ou Waze instalados.

## Instalar

Os APKs não ficam no repositório: compile (abaixo) ou peça o APK de release ao dono do projeto. Do
notebook, na mesma rede Wi-Fi da central (ou pelo cabo):

```bash
adb connect IP_DA_CENTRAL:5555
```

```bash
adb install -r PainelLivre-v0.3.0-release.apk
```

Primeiro uso, na central:

1. Abra o **Painel Livre**. Vai aparecer "Permitir depuração USB?" — marque **Sempre permitir** e OK.
   (É o app conectando no ADB da própria central, como o DashCast faz.)
2. Se aparecer o aviso "Falta permitir…" em cima do carro, toque em **Configurar** (localização,
   dados do carro, notificações do Maps/Waze e segundo plano, tudo pelo ADB).
3. Em **Ajustes → Simular rota**, veja o painel funcionando parado. Ajuste as **margens** nos Ajustes
   se os instrumentos da BYD cobrirem parte dos cartões.
4. Inicie uma rota no Google Maps ou no Waze — o painel liga sozinho.
5. Para subir junto com o carro: deixe **Iniciar junto com o carro** ligado e, se a BYD tiver uma
   lista de "autoiniciar" nas configurações, inclua o Painel Livre. O caminho mais garantido é
   **Usar o Painel Livre como tela inicial** (Configurações). O Diagnóstico mostra se o boot chegou.

Para voltar ao normal a qualquer momento: botão **Painel original** (ou desinstale o app).

## Compilar

Requer JDK 17 e Android SDK (plataforma 34). Detalhes, assinatura e emulador em
[docs/AMBIENTE.md](docs/AMBIENTE.md).

```bash
./gradlew testDebugUnitTest assembleRelease
```

O APK sai em `app/build/outputs/apk/release/`. A assinatura usa `keystore/keystore.properties`
(gerado localmente, fora do git); sem ele, usa a chave de debug.

## Documentação

| Arquivo | Para quê |
|---|---|
| [AGENTS.md](AGENTS.md) | Guia para agentes de IA e pessoas que continuam o projeto: contexto, regras, como validar |
| [CHANGELOG.md](CHANGELOG.md) | O que mudou em cada versão |
| [docs/PLANO.md](docs/PLANO.md) | Fases, estado e checklist do teste no carro |
| [docs/ARQUITETURA.md](docs/ARQUITETURA.md) | Como o código está organizado |
| [docs/AMBIENTE.md](docs/AMBIENTE.md) | Ferramentas, compilação, emulador e roteiros de teste |
| [docs/ENGENHARIA_REVERSA.md](docs/ENGENHARIA_REVERSA.md) | O que se sabe do carro (BYD Navigator, DashCast, SDK BYD) |
| [docs/SEALNAV_RE.md](docs/SEALNAV_RE.md) | Estudo do SealNav em sala limpa |
| [docs/telas](docs/telas) | Capturas das telas |

## Segurança

O app só envia ao serviço `AutoContainer` os comandos 16 e 35 (ligar projeção / criar display), 18 e 0
(restaurar) e, quando seguro, um código de tamanho do painel. O 1 (desconecta o Qt) nunca é enviado.

O código de tamanho (30 = 12,3", 31 = 10,25", 29 = 8,8") é o que o SealNav manda antes de ligar a
projeção, mas o código errado já deixou painéis de 8,8" em "modo simples" (registro do DashCast). Por
isso o Painel Livre só manda:

- no **automático**: o 30, e só se o modelo for Song Plus/Seal U **e** o painel já tiver sido medido
  com geometria de 12,3" (1920×720). Na primeira ativação, antes de medir, não manda nada;
- **escolhido nas Configurações**: o código escolhido, bloqueado se não combinar com o painel medido
  (por exemplo, 30 num painel de 1280×480 nunca sai).

A decisão e o motivo aparecem em Diagnóstico → **Tamanho do painel**. "Não enviar" volta ao
comportamento do DashCast. Nada escreve no CAN. Use por sua conta e risco; não é afiliado à BYD.

## Créditos

- [DashCast](https://github.com/Kiroha/byd-dashcast) (MIT) — protocolo do AutoContainer, nomes dos
  displays e heurísticas de notificação foram aprendidos lendo o código e a documentação dele.
- [MapLibre Native](https://github.com/maplibre/maplibre-native) (BSD-2), [OpenFreeMap](https://openfreemap.org)
  e © colaboradores do [OpenStreetMap](https://www.openstreetmap.org/copyright).
- [dadb](https://github.com/mobile-dev-inc/dadb) (Apache-2.0).
- Ícones [Material Icons](https://fonts.google.com/icons) (Apache-2.0). Tempo: [Open-Meteo](https://open-meteo.com)
  (posição arredondada a ~10 km). Busca de endereços: [Photon](https://photon.komoot.io) (OpenStreetMap).
- SealNav — estudado só por comportamento e protocolo (sala limpa, ver `docs/SEALNAV_RE.md`); nenhum
  código, imagem, voz, chave ou servidor dele é usado.

Licença: [MIT](LICENSE). Não é afiliado à BYD nem ao SealNav.

### Rota nativa (em desenvolvimento, issue #14)

Na aba Navegação escolha **Painel Livre**, busque o endereço e toque no resultado. Casa, Trabalho e
recentes usam a mesma escolha. A linha da rota e a próxima manobra aparecem no mapa do painel;
Maps e Waze continuam disponíveis. No modo manual, toque também em Mostrar no painel.
Para encerrar, toque no cartão de rota ativa na aba Navegação e em **Encerrar**.

O cálculo nativo usa o [OSRM público](https://project-osrm.org/) por HTTPS, sem chave. Envia ao
`router.project-osrm.org` as **coordenadas precisas de origem e destino** (necessárias para calcular
as ruas), sem nome, VIN ou identificador pessoal. Precisa de internet e GPS recente; o servidor
público não garante disponibilidade. Não tem trânsito ao vivo, voz nem mapa/rota offline.
A rota é independente da calculada pelo Maps/Waze. Para uso diário, avaliar instância própria.

O app recebe compartilhamento de texto/coordenadas e intents `geo:`/`google.navigation:` e pede
qual mapa usar. Endereço textual abre a busca para você conferir o resultado. Links curtos do Maps
ou Waze ainda não são expandidos: nesse caso busque o endereço ou compartilhe coordenadas.
Fechar só a tela mantém a orientação; encerrar o processo/serviço perde a rota atual.
Esta função ainda precisa de validação no carro; veja o checklist em `docs/PLANO.md`.
