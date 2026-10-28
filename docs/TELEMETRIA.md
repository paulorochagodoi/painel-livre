# Telemetria do carro (issue #16)

Atualizado: 2026-10-23. **Este leitor permanece somente leitura. Comandos separados em COMANDOS.md (rodada 3). Nenhum comando foi testado no carro.**
O diagnóstico anterior da issue local contém algumas respostas reais; isso não valida o restante do catálogo.

## Como o app lê

- Caminho: `service call autoservice <tx> i32 <dispositivo> i32 <fid>` pelo ADB local (uid 2000), o mesmo
  canal do `sendInfo` do painel (`SelfAdb`). O uid do app não serve: as permissões `BYDAUTO_*_GET` não podem
  ser dadas por `pm grant` na DiLink 3 (diagnóstico da issue #16).
- Binder `autoservice`, interface `android.gui.BYDAutoServer`. Transações:

| tx | Método | No Painel Livre |
|---|---|---|
| 5 | `getInt(dev, fid)` | usado |
| 7 | leitura (buffer na issue); interpretada como float IEEE-754 pelo app, **a confirmar** | usado |
| 6 | `setInt(dev, fid, valor, binder)` — **escrita** | somente `CarCommands`, com chave geral + individual, whitelist e limites; o leitor continua recusando |
| 8, 10, 12, 40, 42 | buffer, vetores, controle remoto, CAN | proibido (aridade errada já derrubou o `autoservice` em outro carro) |

  A issue chama 7 de leitura de buffer; a implementação herdada interpreta sua palavra como float.
  Essa interpretação é **a confirmar** na firmware do carro. O uso relatado do SealNav (`7 i32 1014 i32 1246777400`,
  a bateria) é a referência para essa interpretação; não é garantia para outras firmwares.
- Antes da primeira leitura o app roda `service list | grep autoservice` e só segue se a interface for
  exatamente `android.gui.BYDAutoServer` (os números de transação pertencem a essa interface). Senão:
  "leitura bloqueada por segurança". Junto lê `ro.car.protocol`/`sys.car.protocol` (CAN ou CAN-FD).
- Endereço: se a central tiver a constante `BYDAutoFeatureIds$Grupo.NOME` (lida por reflexão, só a
  constante), ela vence o padrão da tabela; a BYD calcula alguns ids conforme CAN/CAN-FD ao carregar a
  classe. Sem nome conhecido: padrão CAN-FD (o mesmo que o SealNav usa neste carro) ou o alternativo CAN.
- Uma varredura = um único comando de shell, com `echo @@PL<i>` antes de cada leitura (uma leitura que não
  imprime nada não desloca as outras). Intervalo 5/10/30 s (padrão 10), **só enquanto** a tela inicial ou o
  aviso do painel está visível e a opção **Ajustes → Dados do carro → Ler dados do carro** está ligada
  (desligada por padrão).

## Valores que não são dados

| Resposta | Significado | No app |
|---|---|---|
| `0xffffd8e5` (−10011) | sem dado nesta geração / com o carro parado | indisponível |
| `0xffffd8e3` (−10013) | transação ou direção errada | indisponível |
| `0x0000ffff` (65535) | ligação fid↔CAN não estabelecida (ou "não equipado") | indisponível |
| `0x000fffff` | 20 bits "não inicializado" | indisponível |
| `0xbf800000` (−1,0f), NaN, infinito | float inválido | indisponível |
| status ≠ 0 na 1ª palavra do Parcel | exceção do Binder | indisponível |
| "Service autoservice does not exist" | central sem o serviço (emulador, outra marca) | "Esta central não tem o serviço" |

Além disso cada tipo tem faixa plausível (`TelemetrySnapshot.decode`): porta só 0/1, pressão 50–600 kPa,
temperatura ajustada 14–36 °C etc. **Sem dado nunca vira zero nem "fechado".** Leituras parciais também não permitem afirmar
“Tudo fechado” ou “Vidros fechados”: todos os respectivos estados precisam estar disponíveis e fechados.

## Tabela de IDs

**A confirmar no carro:** todos os IDs, escalas e estados abaixo, exceto a resposta de câmbio P = 1
registrada na issue. “Público” e “hipótese” não significam validação neste Song Plus. R/N/D também
continuam **a confirmar**, sem necessidade de engatar marchas durante este teste.

Confiança: **carro** = lido no carro do dono e coerente; **público** = nome no catálogo BYD e/ou medição
publicada em outro BYD (ainda falta conferir na DiLink 3 do Song Plus); **hipótese** = só o rótulo do
SealNav (referência de interoperabilidade) ou significado dos valores não confirmado.

### Portas, trava e carga

| Chave | Dado | dev / fid | tx | Unidade / valores | Confiança | Nome no catálogo |
|---|---|---|---|---|---|---|
| `door_fl` | Porta dianteira esquerda | 1001 / `0x29400008` | 5 | 0 fechada, 1 aberta | público | `Bodywork.BODYWORK_LEFT_HAND_FRONT_DOOR` |
| `door_fr` | Porta dianteira direita | 1001 / `0x2940000A` | 5 | idem | público | `…RIGHT_HAND_FRONT_DOOR` |
| `door_rl` | Porta traseira esquerda | 1001 / `0x2940000C` | 5 | idem | público | `…LEFT_HAND_REAR_DOOR` |
| `door_rr` | Porta traseira direita | 1001 / `0x2940000E` | 5 | idem | público | `…RIGHT_HAND_REAR_DOOR` |
| `hood` | Capô | 1001 / `0x2940001C` | 5 | idem | público | `Bodywork.BODYWORK_HOOD` |
| `trunk` | Porta-malas | 1001 / `0x2940001A` | 5 | idem | público | `Bodywork.BODYWORK_LUGGAGE_DOOR` |
| `lock` | Trava das portas | 1001 / `0x40700008` | 5 | 1 destrancado, 2 trancado | **hipótese** | — |
| `charge_lid` | Tampa do carregador | 1001 / `0x3D902022` | 5 | 0 fechada, 1 aberta | **hipótese** | — |
| `gun` | Cabo de carga | 1009 / `0x34400032` | 5 | 1 nenhum, 2 AC, 3 DC, 4 AC+DC, 5 V2L | público | `Charging.CHARGING_GUN_CONNECT_STATE` |
| `bms` | Estado da carga | 1009 / `0x34400018` (CAN: 876611608) | 5 | 1 carregando, 2 terminou, 13 pausada | público | `Charging.CHARGING_BATTERRY_DEVICE_STATE` |
| `charge_kw` | Potência de carga | 1009 / `0x32300018` | 7 | kW | público | `Instrument.CHARGING_CHARGE_POWER_DD` |
| `charge_h` / `charge_min` | Tempo restante | 1009 / `0x32300028` / `0x32300030` | 5 | horas / minutos | público | `…CHARGE_REST_HOUR_DD` / `…_MINUTE_DD` |

Portas dianteiras: o SealNav troca `0x29400008`/`0x2940000A` quando o carro é de mão inglesa; no Brasil
(mão esquerda) "dianteira esquerda" é a do motorista.

### Bateria, autonomia, condução

| Chave | Dado | dev / fid | tx | Unidade | Confiança | Nome |
|---|---|---|---|---|---|---|
| `soc` | Bateria (SOC) | 1014 / `0x4A505038` (CAN: 1033543720) | 7 | % (float) | público + SealNav lê o mesmo | `Statistic.STATISTIC_ELEC_PERCENTAGE` |
| `ev_range` | Autonomia elétrica | 1014 / `0x4A50203E` (CAN: 1033203771) | 5 | km | público + SealNav | `Statistic.STATISTIC_ELEC_DRIVING_RANGE` |
| `fuel_range` | Autonomia a combustível | 1014 / `0x4A504038` | 5 | km | público + SealNav | `Statistic.STATISTIC_FUEL_DRIVING_RANGE` |
| `fuel_pct` | Combustível | 1014 / `0x4A507040` | 5 | % | público | `Statistic.STATISTIC_FUEL_PERCENTAGE` |
| `volt12` | Bateria 12 V | 1001 / `0x43400028` | 7 | V | público + SealNav lê o mesmo | — |
| `gear` | Câmbio | 1011 / `0x21200038` | 5 | 1 P, 2 R, 3 N, 4 D | **carro** (P = 1 parado, issue #16) | `Gearbox.GEARBOX_AUTO_MODE_TYPE` |
| `power_kw` | Potência da bateria | 1012 / `0x14400020` | 5 | kW (+ consumo, − regeneração) | público | `ENGINE_POWER` |

### Ar-condicionado e bancos

| Chave | Dado | dev / fid | tx | Unidade / valores | Confiança |
|---|---|---|---|---|---|
| `ac_temp_driver` | Temperatura ajustada (motorista) | 1000 / `0x40400028` | 5 | °C | público |
| `ac_temp_pass` | Temperatura ajustada (passageiro) | 1000 / `0x40400030` | 5 | °C | público |
| `temp_out` | Temperatura externa | 1000 / `0x40400038` | 5 | °C | público |
| `temp_in` | Temperatura interna | 1000 / `0x3D800030` | 5 | °C | público (`Ac.AC_TEMP_INSIDE`) |
| `ac_on` | A/C ligado | 1000 / `0x40400010` | 5 | cru | hipótese |
| `ac_fan` | Ventilador | 1000 / `0x4040001C` | 5 | nível | hipótese |
| `ac_recirc` | Recirculação | 1000 / `0x40400014` | 5 | cru | hipótese |
| `ac_mode` | Saída do ar | 1000 / `0x40400018` | 5 | cru | hipótese |
| `ac_compressor` | Compressor | 1000 / `0x40400009` | 5 | cru | hipótese |
| `ac_auto` | Automático | 1000 / `0x40400012` | 5 | cru | hipótese |
| `ac_defrost` | Desembaçador dianteiro | 1000 / `0x40400016` | 5 | cru | hipótese |
| `seat_heat_driver` | Aquecimento banco motorista | 1023 / `0x4FA0000B` | 5 | 1 desligado, 2… nível (valores: hipótese) | público (nome) |
| `seat_vent_driver` | Ventilação banco motorista | 1023 / `0x4FA00008` | 5 | idem | público (nome) |
| `seat_heat_pass` | Aquecimento banco passageiro | 1023 / `0x4FA00013` | 5 | idem | público (nome) |
| `seat_vent_pass` | Ventilação banco passageiro | 1023 / `0x4FA00010` | 5 | idem | público (nome) |

Os nomes dos bancos são `Setting.SET_…_SEAT_HEATING_STATE`/`…VENTILATING_STATE`: o `SET_` é do grupo
Setting (estado lido), não id de escrita (`…_SET`).

### Vidros, teto e pneus

| Chave | Dado | dev / fid | tx | Unidade | Confiança |
|---|---|---|---|---|---|
| `win_fl` / `win_fr` / `win_rl` / `win_rr` | Abertura dos vidros | 1001 / `0x38800018` / `0x4B900010` / `0x38800020` / `0x38800030` | 5 | % aberto | hipótese (rótulo `openPct` do SealNav) |
| `sunroof` | Teto solar | 1001 / `0x41A00030` | 5 | % | hipótese |
| `sunshade` | Cortina do teto | 1001 / `0x41A00010` | 5 | % | público (`Body.BODYWORK_SUNSHADE_PANEL_PERCENT`) |
| `tyre_p_*` | Pressão dos pneus DE/DD/TE/TD | 1016 / `0x99000124` `…28` `…2C` `…30` | 5 | kPa (287 = 2,87 bar) | público |
| `tyre_t_*` | Temperatura dos pneus | 1007 / `0x4A50A018` `…24` `…30` `…3C` | 5 | °C | público (CAN-FD) |

### Só na leitura completa do debug (candidatos)

`0x43100008`/`0x4310000C` (teto/cortina alternativos; responderam `0xffffd8e5` na issue), vidros
alternativos `0x38800010`/`0x38800028`/`0x4B900018`, 1023/`0x12D00020` (SealNav: "power"), 1023/`0x4290000E`
(respondeu `0xffff` na issue), cinto 1007/`0x2610001B`, porta-malas do painel 1007/`0x26100018`, aviso da
tampa de carga no painel 1007/`0x3D902022`, tampa de carga do catálogo DL3 (`0x2400003A`, AC `0x4FB00020`,
DC `0x4FB00018`), cabo de carga "Song Plus" 1009/`0x34400832`, carga do BMS ×10 1014/`0x44700028`.

## Referências registradas na etapa anterior

Os projetos abaixo foram citados no trabalho herdado; suas tabelas não foram revalidadas nesta revisão.
A fonte local disponível é /tmp/issue-telemetria.md; o catálogo em Kotlin é a lista efetivamente lida.

## Fontes

- Diagnóstico por ADB no carro do dono (issue #16, 2026-10-23): transporte, `1011/0x21200038 = 1`,
  `0x43100008`/`0x4310000C` = `0xffffd8e5`, `1023/0x4290000E = 0xffff`.
- SealNav 0.37.512 (sala limpa, só como referência de **quais** ids existem; nenhum código copiado):
  tabelas de portas/capô/porta-malas, clima, bancos, vidros, teto, trava, cabo/BMS, tampa de carga.
- Públicas (GitHub): `yash-srivastava/Overdrive-release` (`BydFeatureIds.java`: nomes das portas,
  "0 = fechada, 1 = aberta" na DiLink 3.0, bancos, carga), `AndyShaman/BYDMate` (`FidRegistry.kt`,
  `SentinelDecoder.kt`: transações 5/7, sentinelas, cabo/BMS), `shihabal3amri/DiPlay` (`BydBattery.kt`,
  `BydTyres.kt`, `BydVehicleFields.kt`: SOC/autonomia CAN e CAN-FD, pneus), `xor777/denza-lab`
  (`docs/vehicle-data-findings.md`: tabela medida, escalas, sentinelas, "nunca 6"), `ID-VerNe/BYD-dev`
  (tabela de transações), `akyuznet/ESMER-UI` (DiLink 3.0: −10011/65535 = não equipado).

## Riscos e regras

- O leitor só usa 5 e 7. Por decisão explícita do dono, a escrita 6 passa exclusivamente por `CarCommands` (COMANDOS.md). Outras transações continuam bloqueadas.
- Só ids desta tabela (`TelemetryCatalog`), sempre `i32 dev i32 fid`. Nada de varrer o catálogo no carro.
- Interface conferida antes; ADB ausente, serviço ausente ou interface diferente = sem leitura, com aviso.
- Polling leve e só com tela visível; o painel só lê se o aviso estiver ligado.

## Para conferir no carro

Roteiro em `docs/PLANO.md`, issue #16. Ao confirmar um id, mudar a confiança em `TelemetryCatalog` e aqui.

## Como testar

### Local, sem carro

Com JDK 17 e SDK configurados, executar:

```bash
JAVA_HOME=$HOME/.jdks/jdk-17.0.21+9 ANDROID_HOME=$HOME/Android/Sdk ./gradlew testDebugUnitTest lintDebug
```

`TelemetryTest` cobre respostas Parcel hex curtas, com espaços, com offsets, várias linhas,
exceção, serviço ausente, texto ASCII que parece hex e saída truncada. A sentinela
`0xffffd8e5` deve produzir valor indisponível para **todos** os sinais, nunca zero/fechado.
Os testes tentam montar código 6 para cada endereço e protocolo e injetá-lo no lote: tudo deve falhar.
O lote normal e a leitura completa do debug também são conferidos.

Na build debug, em **Abrir o registro do debug → Simular dados do carro**, um toque abre a lista de cenários descrita na seção Carro 3D abaixo. Para observar na central,
ligar **Ler dados do carro**; para o painel, ligar também o aviso. Dados são fictícios e não validam IDs.
O polling simulado não chama autoservice; **Ler dados do carro** no debug continua sendo uma leitura
real, não uma simulação. Ao desligar a simulação, os dados falsos são apagados imediatamente.
Capturas e conferência visual em emulador continuam pendentes nesta etapa.

### No Song Plus, somente pelo dono

1. Com carro parado em P, ligar a central e o ADB local pelo fluxo já existente. Não conceder
   permissões, alterar propriedades, executar setters ou experimentar números de transação.
2. Ligar modo de debug e usar **Ler dados do carro** uma vez. Salvar o registro com firmware,
   protocolo, endereço efetivo, tx, palavra hex e valor mostrado. Interface diferente deve bloquear;
   serviço ausente deve avisar; sentinela deve mostrar indisponível.
3. Comparar SOC, autonomias, pressão/temperatura dos pneus e clima com os instrumentos originais.
   Usar os controles originais, uma mudança por vez, para conferir temperatura, A/C, modo, ventilador,
   recirculação e níveis dos bancos. Não interpretar zero cru como função desligada sem confirmação.
4. Ainda parado, observar cada porta, capô, porta-malas, trava, vidros e teto pelos controles originais,
   com cuidado e sem ninguém no caminho. Comparar antes/depois; registrar posição e sentido da escala.
   A leitura do app não deve mover nem acionar nada. Não confiar no cartão como aviso de segurança.
5. Em uma carga habitual segura, comparar tampa, cabo, estado, kW e tempo restante. Não manipular
   conector energizado para produzir cenários; não testar DC/V2L se não fizer parte do uso habitual.
6. Ligar leitura periódica (começar com 30 s), conferir cartão central e aviso opcional do painel.
   Ao sair das telas, a leitura periódica deve parar; diagnóstico e botão debug são leituras pontuais.
7. Desligar a opção ao terminar. Registrar indisponíveis e divergências como **a confirmar**;
   não tentar outros IDs/transações no carro. Levar as respostas para novos testes JVM antes de
   alterar interpretação ou confiança.

Conferir especialmente 7 (formato float/buffer), trava 1/2, carga/BMS, tampa, níveis dos bancos,
percentuais dos vidros/teto, IDs CAN versus CAN-FD e unidades dos pneus.
A conferência do descriptor é uma barreira adicional, **não prova** que uma firmware preserva
o contrato das transações. A autorização desta tarefa limita o transporte a 5 e 7 e não autoriza
execução pelo agente no carro nem execução de escrita pelo agente. A decisão posterior autoriza implementar comandos, conforme COMANDOS.md.

## Carro 3D na tela inicial (não publicado)

A ilustração central usa renders WebP locais do modelo do Song Plus fornecido pelo dono,
sem biblioteca 3D. Layout, cartões, barra inferior e mapa do painel permanecem iguais.
A foto pessoal, quando configurada, continua tendo preferência. Sem destaque, aparece
a vista 3/4; portas, capô, porta-malas, tampa de carga, carga, vidro/teto/cortina abertos
ou pressão fora da faixa visual trocam para a vista superior horizontal, com crossfade de 250 ms.
Os overlays indicam estados: não são renders de peças articuladas. Trava desconhecida não mostra cadeado.

`CarOverlay.kt` reúne todas as posições em frações da imagem **inteira** car_top.webp:
frente à esquerda, lado esquerdo do veículo embaixo, direito em cima. `openings` guarda
dobradiça/ponta das portas e faixas do capô/porta-malas; `windows` guarda faixas dos vidros,
teto e cortina; `tyres` guarda o centro de cada pneu. `charger`, `lock` e `baseLock` são âncoras.
O encaixe usa a mesma transformação da imagem, mantendo os overlays alinhados ao redimensionar.
Pressão disponível aparece em bar e cor verde; indisponível fica cinza com travessão;
abaixo de 220 ou acima de 310 kPa fica âmbar e ativa destaque. Esses limites são apenas
critérios visuais configuráveis em constantes, não a recomendação da BYD nem um alarme TPMS lido.
Não há sinal de alarme de pneu separado no catálogo atual.

Tampa de carga: âncora configurável `charger = (.84, .15)`, na traseira direita.
Referência consultada: [localização descrita para Song Plus DM-i](https://www.bitauto.com/ask/10016625117/).
O [manual oficial BYD](https://www.byd.com/content/dam/byd-site/mea-public/after-sales/manual/gcc/BYD_SONG_PLUS_DM_i_Owner%E2%80%99s_Manual.pdf)
foi localizado, mas não confirmou a posição para o ano brasileiro 2025 nesta etapa;
manter como provisória até conferência pelo dono. Não usar a antiga posição traseira esquerda do desenho pequeno como referência.

`SongCarView` só afeta o carro central. Dois bitmaps (1200 px de largura, cerca de 6,5 MiB
ARGB somados) ficam em cache compartilhado, sem decodificar durante desenho/animação.
Somente a transição e o pulso de carga geram quadros; ocultar/desanexar interrompe animações.
A telemetria existente fornece os estados; nenhuma leitura, comando ou transação foi acrescentada.

Debug → Simular dados do carro abre uma lista de cenários: padrão trancado, carga, pneu baixo,
vidro/teto, tampa de carga, cada porta, capô e porta-malas, além de desligar.
Ligar “Ler dados do carro” para manter a simulação na tela; os dados são fictícios.
Testes JVM `CarOverlayTest` cobrem decisão de vista, mapeamento independente, limites
de pressão e valores desconhecidos. Validação no emulador não valida o carro.

Validação desta etapa (23/10/2026): 180 testes JVM passando, `lintDebug` sem erros
e com 114 avisos, `assembleDebug` concluído. APK instalado no **emulator-5580** após
backup do APK e dados em `/tmp/pl-car3d-ref/emulador-original.apk` e
`emulador-dados-original.tar`. Capturas da vista padrão, porta, carga e pneu em alerta:
`docs/telas/carro-3d-*.png` e `/tmp/pl-car3d-ref/print-*.png`.
A posição da tampa e os estados reais continuam pendentes de conferência pelo dono.

## Rodada 3 — releitura após comando

`VehicleTelemetry.refresh` faz uma leitura real pontual, independente do polling visível, e mescla
só os sinais relidos no StateFlow. A confirmação usa o snapshot dessa leitura, nunca o cache anterior.
Temperatura/ventilador podem confirmar igualdade; os modos de clima ainda têm codificação GET
incerta e sempre mostram “Não confirmado pelo carro”, com valor cru no registro. Simulação não
pode validar ambiente nem posição P para uma escrita. A leitura de câmbio é pontual e recente;
falha/valor desconhecido/simulado bloqueia movimento. IDs de escrita são diferentes dos GETs.
`TelemetryTest` mantém o bloqueio do setter nos lotes do leitor; `CarCommandsTest` confere a nova
fronteira, limites, chaves, log, exclusividade da tx 6, intervalo e falta de confirmação.

Validação da rodada 3: 248 testes JVM, zero falhas; `testDebugUnitTest lintDebug assembleDebug`
com JDK 17. Lint sem erros (79 avisos). No emulator-5580 foram conferidos a janela de clima,
seletor, confirmação e rejeição com a chave desligada, registrada no log; lista de liberações
individuais e recusa de hipótese. Capturas `docs/telas/aura-r3-*.png`. Nenhum teste no carro.
