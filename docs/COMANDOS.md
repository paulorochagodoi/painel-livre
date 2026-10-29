# Comandos ao carro — interface Aura

2026-10-24, v0.5.4. A decisão do Paulo autoriza enviar diretamente ao tocar no valor, sem
diálogo adicional de confirmação. Chave geral e funções com evidência vêm ligadas por padrão. **Nenhuma escrita foi executada no carro nesta rodada.**
Confirmação no código de referência não significa confirmação na firmware do Song Plus do dono.

## Transporte e fronteira de segurança

O mecanismo observado no artefato local do SealNav monta `service call autoservice 6 i32 dev i32 fid i32 valor`
pelo ADB local. Não se deve executar essa expressão fora de `CarCommands`. A transação 5 lê inteiros;
a interpretação de 7 como float continua pendente em TELEMETRIA.md. Nenhuma outra transação de escrita
é autorizada. IDs de leitura e de escrita **não são intercambiáveis**.

A conferência de `android.gui.BYDAutoServer` é necessária, mas não prova compatibilidade da firmware.
Chave geral, interruptor individual, lista fechada e limites são condições cumulativas. Comando
indisponível/hipótese permanece bloqueado mesmo com as chaves ligadas. Não há shell livre na interface.
Movimento de parte do carro exige leitura real, recente e disponível de P; simulação não é prova de P.
Não há repetição automática após timeout: um retorno Binder não prova que o carro mudou de estado.
Registrar envio/rejeição com hora, valor e resultado; reler e distinguir confirmação, divergência e
estado indisponível. A interface só deve mostrar o valor real como confirmado depois dessa leitura.

## Catálogo de interoperabilidade

“Confirmado na fonte” abaixo significa fluxo de UI → setter → valor observado estaticamente no
artefato de referência, **não teste no carro**. Esses itens podem ser testados individualmente pelo
Paulo; “hipótese” não pode ser habilitada até resolver a evidência. Os valores são inteiros no fio.

| Função | dev | feature de escrita decimal / hex | Valores observados | Confiança / origem |
|---|---:|---|---|---|
| Temperatura motorista | 1000 | 501219368 / `0x1DE00028` | 16…30, graus inteiros | confirmado na fonte E1/E2 |
| Temperatura passageiro | 1000 | 501219376 / `0x1DE00030` | 16…30, graus inteiros | confirmado na fonte E1/E2 |
| Ventilador | 1000 | 501219340 / `0x1DE0000C` | 0…7 | confirmado na fonte E1/E3 |
| Energia da climatização | 1000 | 501219364 / `0x1DE00024` | 0 desliga, 1 liga | confirmado na fonte E3; UI de referência combina outros comandos |
| A/C (compressor) | 1000 | 501219344 / `0x1DE00010` | 0 desliga, 1 liga | confirmado na fonte E1/E2, callback do botão A/C |
| AUTO | 1000 | 501219352 / `0x1DE00018` | 1 desliga, 2 liga | confirmado na fonte E1/E2 |
| Recirculação | 1000 | 501219355 / `0x1DE0001B` | 0 desliga, 1 liga | confirmado na fonte E2 |
| Desembaçador dianteiro | 1000 | 501219362 / `0x1DE00022` | 0 desliga, 1 liga | confirmado na fonte E2 |
| Aquecimento motorista | 1001 | 1125122068 / `0x43100014` | 2/3 níveis 1/2; desligar usa 1 e 0 | **hipótese operacional**, sequência E4 não validada |
| Ventilação motorista | 1001 | 1125122064 / `0x43100010` | idem | **hipótese operacional**, E4 |
| Aquecimento passageiro | 1001 | 1125122076 / `0x4310001C` | idem, precedido de 6 no fluxo observado | **hipótese operacional**, E4 |
| Ventilação passageiro | 1001 | 1125122072 / `0x43100018` | 2/3 níveis 1/2; desligar usa 1 e 0 | **hipótese operacional**, E4 |
| Porta-malas | 1001 | 1125122080 / `0x43100020` | booleano → 1 ou 3; sentido ainda sem corroboração suficiente | **hipótese**, E5; bloqueado |
| Vidros/teto | — | sem contrato de escrita validado para este carro | não definido | **hipótese**, bloqueado |
| Bateria/carga | — | sem contrato validado; há setters de referência sem confirmação suficiente | não definido | **hipótese**, bloqueado |
| Rádio FM/AM BYD | — | nenhum intent/API de sintonia confirmado nesta investigação | não definido | **hipótese**, bloqueado |
| Reprodução Android | — | MediaSession / MediaController | play, pause, anterior, próxima conforme sessão | API Android, sem feature BYD; depende de sessão e permissão |
| Volume Android | — | AudioManager, STREAM_MUSIC | 0…máximo informado pela plataforma | API Android; não prova controle do amplificador/radio BYD |

Passos de 0,5 °C são um requisito desejado, mas o setter encontrado recebe graus inteiros. **Não**
multiplicar por 2, usar bits float ou arredondar silenciosamente: meio grau fica bloqueado até fonte
ou teste específico confirmar a escala. Também não interpretar `SET_` no nome de grupo Setting como
ID de escrita: o getter dos bancos é dev 1023/`0x4FA…`, enquanto o setter observado usa dev 1001.

## Registro de evidências locais (sala limpa)

Os arquivos abaixo são artefatos privados já presentes fora do git. Nenhum trecho de implementação,
recurso visual, servidor ou chave de terceiros é incorporado ao Painel Livre. As tabelas registram
somente mecanismo, endereços e protocolo para implementação própria.

Artefato disponível: `/tmp/sealnav-apk/sealnav.apk`, SHA-256
`7695e342fb8d50161701aaa6f39aa88b160e546871cebc9e3198033be94085b5`.
A documentação herdada o identifica como SealNav 0.37.512. A ligação exata entre esse APK e a saída
JADX herdada não foi refeita nesta rodada: nomes/linhas abaixo são a evidência estática local consultada.
Prefixo **S**:
`/tmp/claude-1000/-home-paulo-Documentos-REA-BYD/bd1ffaec-03c4-4b8b-bf06-a4e617ba68e0/scratchpad/sn_jadx/sources/`.

- **E1**: `S/p000/F10.java`, `m841Y` (linha 731) monta tx 6; `m877S` (1900)
  distingue D/P e limita 16…30; `m880V` (1919), `m881W` (1925), `m882X` (1931)
  codificam AUTO, A/C e ventilador. Os nomes ofuscados isolados não estabelecem a função.
- **E2**: `S/com/example/bydcluster/nav/cockpit/CockpitActivity.java`, configuração da janela de clima
  (2820…2955), `m3971H4` (recirc) e o callback AUTO, mais `S/p000/C2529lc.java` cases 0 e 2,
  `S/p000/C3250ub.java` cases 11 e 15: relacionam controles nomeados aos setters e booleanos.
- **E3**: `CockpitActivity.m4060b` (12617) liga com AUTO desligado, power=1, A/C ligado e ventilador
  1…7; `m4053a` (11540) desliga via `S/p000/C3376wb.java` case 20: AUTO off, A/C off,
  ventilador 0 e power=0. `S/p000/C1675ac.java` case 1 contém o envio de power=1.
- **E4**: `CockpitActivity.m4115m3` (14840…14890) seleciona H/V, motorista/passageiro, desliga
  a outra função e faz sequência de valores 1 e 0. No passageiro aquecido envia também 6 antes do
  nível. Isso confirma os endereços, mas não autoriza simplificar a sequência para um único setter.
- **E5**: `S/p000/E10.java:m710a` monta dev 1001/fid 1125122080 com 1/3.
  `S/p000/C2530ld.java` verifica câmbio (dev 1011/fid 555745336, P=1) antes de um ramo.
  O fluxo não foi integralmente corroborado; abrir e fechar ficam bloqueados, ambos exigirão P.

REA `binary_session` foi recusado automaticamente pelo cliente: “MCP tool call requires approval,
but approval policy is never”. A inspeção continuou pela leitura estática de saída local já existente,
sem reparar configuração, sem executar APK e sem acessar o carro. Não há Evidence ID REA disponível.

## Releitura e lacunas

Os GETs de clima estão em TELEMETRIA.md (`0x404…`). A referência faz UI otimista e mantém caches;
esses caches não são confirmação do carro e não devem ser reproduzidos como validação.
Temperaturas e ventilador podem ser comparados ao valor solicitado quando GET retorna número válido.
Power, A/C, AUTO, recirc e desembaçador têm GETs conhecidos, mas o catálogo herdado chama seus estados
crus de hipótese: registrar a resposta bruta e manter “sem confirmação” se a escala não for segura.
Não igualar automaticamente valor SET ao GET, principalmente AUTO (1/2 no SET).

Antes de considerar comprovado no Song Plus, Paulo deve validar um comando por vez com carro parado,
comparar no controle original e capturar GET antes/depois. O roteiro completo fica em PLANO.md.
Nenhuma verificação foi feita no IP do carro nesta rodada.

## Preferências na 0.5.4

`Prefs.migrateCommands` grava `car_commands_migration=1` em uma única edição junto das chaves:
`car_commands_enabled=true` e `car_command_<chave>=confirmed` do catálogo fechado. Isso também
liga os antigos padrões desligados na atualização, conforme pedido do dono. A migração roda antes
de carregar Ajustes e antes de executar comandos/abrir a lista de funções no Diagnóstico.
Depois dela, desligar uma chave é respeitado nas próximas aberturas. Hipóteses recebem false e
não podem ser habilitadas pelo runtime, mesmo que alguém altere a preferência externamente.

A escolha do valor na climatização permanece só para a temperatura (16…30°, 15 valores);
o diálogo “Enviar ao carro?” foi removido em 24/10 e, em 29/10 (issue #20, foto no carro), a lista
de valores saiu também de AUTO, A/C, recirculação, desembaçador e energia geral — viram toggle
de um toque (`CarCommands.toggleValue`, lê o estado atual em `CarReadouts.climate` e manda o
oposto) — e a ventilação ganhou botões +/- (`CarCommands.fanStep`) em vez da lista 0…7.
O toque passa por `CarCommandRuntime` (fila única) e `CarCommands`: whitelist, limites inteiros,
intervalo mínimo de 1000 ms, ambiente e chaves conferidos antes do envio. Partes móveis exigem P
real e recente; bancos, vidros, teto, porta-malas e rádio BYD continuam bloqueados.
Cada tentativa/envio/resultado entra no AppLog e o estado é relido sem repetir um envio inconclusivo.
Os testes JVM cobrem migração e desligamento posterior, hipóteses, limites, P, frequência, log,
releitura e o caminho da interface ao runtime. Emulador não comprova comportamento na firmware BYD.

## Achados do teste no carro em 29/10 (0.5.6)

Teste da v0.5.5-teste no carro de Paulo (DiLink 3.0, 15:07–15:10): diálogos sumiram e o fan +/-
confirmou certo. Três problemas apareceram e foram corrigidos na 0.5.6.

- **AUTO (raw ≠ lógico):** o fio manda 1=desliga/2=liga (tabela acima), mas a leitura comparava o
  valor cru com o lógico do toggle e nunca batia — a UI ficava sempre "ligado" mesmo com Paulo
  confirmando AUTO desligado na tela nativa. `CarCommands.toWire`/`fromWire` decodificam o `onWire`
  nos dois sentidos; `CarReadouts.climate` e a confirmação de `CarCommands.execute` usam o valor
  decodificado. `ac_auto` e `ac_recirc` (recirculação já batia no carro) ganharam `readbackKnown=true`.
  `ac_on`, `ac_compressor` e `ac_defrost` continuam sem `onWire` (raw = lógico, 0/1, conforme a tabela
  "Catálogo de interoperabilidade" acima) — nenhum mapa novo foi inventado sem evidência.
- **Toque durante um envio em andamento não é mais rejeitado** (pedido do Paulo). Toggles (AUTO, A/C,
  recirculação, desembaçador, energia, banco, porta-malas) ficam numa fila de 1 desejo
  (`LauncherActivity.pendingCommand`); ventilação e temperatura somam no alvo e só saem ~350 ms depois
  do último toque (`stepCommand`), respeitando o limite de 1 envio/s do `CarCommands`. Nunca aparece
  Toast/log de "ocupado".
- **Temperatura, bancos e porta-malas ainda abriam diálogo/Toast de bloqueio.** Temperatura
  (`ac_temp_driver`/`ac_temp_pass`) ganhou botões `tempDown`/`tempUp` no cartão da Climatização
  (`stepTemp`, passo de 1° do próprio `Spec`); o atalho de temperatura no dock só mostra a leitura e
  abre a Climatização (não manda comando). Bancos (`seat_heat_*`, `seat_vent_*`) e porta-malas
  (`trunk`) viraram toque direto (`toggleSeat`, `toggleTrunk`) — continuam bloqueados pelo
  `CarCommands` por serem hipótese (sem `dev`/`fid` ou sem P), só a UI deixou de abrir diálogo/Toast
  antes de tentar. `chooseCommand` e todo `AlertDialog.setItems` de comando foram removidos.
