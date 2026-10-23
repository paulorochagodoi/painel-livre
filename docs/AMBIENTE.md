# Ambiente de desenvolvimento e testes

Atualizado: 2026-10-22. Tudo aqui foi usado de fato para chegar à v0.3.0 (Linux x86_64, 16 GB de RAM).

## Ferramentas

| Ferramenta | Versão | Onde estava na máquina original |
|---|---|---|
| JDK | 17 (Temurin 17.0.21+9) | `~/.jdks/jdk-17.0.21+9` |
| Android SDK | platforms 33/34, build-tools 34/35, platform-tools, emulator 37.2 | `~/Android/Sdk` |
| Imagem do emulador | `system-images;android-29;default;x86_64` (Android 10, igual à DiLink 3) | |
| Gradle | 8.11.1 (wrapper, com SHA-256 da distribuição) | `./gradlew` |
| AGP / Kotlin | 8.7.3 / 2.0.21 | `build.gradle.kts` |
| JADX | 1.5.6 (SHA-256 do zip conferido com o GitHub) | `~/tools/jadx` — só para estudar APKs |
| Python 3 | qualquer 3.8+ | scripts em `tools/re/` |

```bash
export JAVA_HOME=~/.jdks/jdk-17.0.21+9
export ANDROID_HOME=~/Android/Sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```

`local.properties` (fora do git) precisa de `sdk.dir=/caminho/do/Android/Sdk`.

## Compilar e testar

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew assembleDebug assembleRelease lintRelease
```

- APKs: `app/build/outputs/apk/{debug,release}/PainelLivre-v<versão>-<tipo>.apk`.
- Debug inclui `x86_64` (emulador); release só `arm64-v8a` e `armeabi-v7a` (a central é ARM).
- Pouca RAM: `gradle.properties` já tem `kotlin.compiler.execution.strategy=in-process` (o daemon
  do Kotlin caía por falta de memória) e `-Xmx3g`.
- Relatórios: `app/build/reports/tests/…` e `app/build/reports/lint-results-release.{txt,html}`
  (estado atual: 0 erros, ~27 avisos menores).

### Assinatura do release

`keystore/` está no `.gitignore`. Sem `keystore/keystore.properties`, o release sai assinado com a chave
de debug (instala, mas não atualiza um APK assinado com outra chave). Para criar uma chave:

```bash
keytool -genkeypair -v -keystore keystore/release.jks -alias painellivre -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Painel Livre, O=Uso pessoal, C=BR"
```

E `keystore/keystore.properties`:

```properties
storeFile=keystore/release.jks
storePassword=...
keyAlias=painellivre
keyPassword=...
```

A chave usada nos APKs v0.1.0–v0.3.0 tem certificado `CN=Painel Livre, O=Uso pessoal, C=BR`, SHA-256
`7290204a01fa20684206e34d516d0aeea9a24517dcd371b78e882322152e13d9`. Ela fica só na máquina do dono do
projeto; **nunca** versionar nem colar senhas em issues/PRs. Trocar de chave obriga a desinstalar o app
no carro antes de instalar a nova versão.

## Emulador que imita a DiLink 3

```bash
sdkmanager "platform-tools" "emulator" "platforms;android-34" "build-tools;35.0.0" "system-images;android-29;default;x86_64"
```

```bash
avdmanager create avd -n dilink3 -k "system-images;android-29;default;x86_64"
```

Depois, em `~/.android/avd/dilink3.avd/config.ini`: `hw.lcd.width=1920`, `hw.lcd.height=1080`,
`hw.lcd.density=160`, `hw.ramSize=2048`, `hw.keyboard=yes`.

```bash
emulator -avd dilink3 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -memory 2048 -no-snapshot-save
```

### Simular o painel e o ADB local

```bash
adb -s emulator-5554 shell settings put global overlay_display_devices 1920x720/160
```

```bash
adb -s emulator-5554 reverse tcp:5555 tcp:5555
```

- O display "Overlay #1" faz papel do display do painel **só em builds debug**
  (`ClusterController.isKnownClusterName`).
- `adb reverse` faz o `127.0.0.1:5555` de dentro do emulador chegar ao adbd do próprio emulador — é o
  "ADB de si mesmo" do carro. Sem isso o app mostra "porta 5555 fechada".
- O emulador não tem `AutoContainer`: em debug, `ClusterController.send()` só registra
  `[debug, sem AutoContainer] sendInfo(N) simulado`, para testar a sequência inteira.
- Use adb **sem root** (uid 0 é recusado pelo `am start --display`; o carro usa uid 2000).
- O GNSS do emulador não funciona: a simulação de rota (debug) injeta GPS falso.

### Ver as telas

- O painel no emulador aparece como uma janela pequena por cima da tela; ela **intercepta toques** —
  arraste para o lado: `adb shell input swipe 480 180 1420 880 800`.
- `screencap` só captura o display físico 0. Para ver o painel em tamanho real: Ajustes → Diagnóstico →
  "Pré-visualizar aqui" com a tela do emulador em `adb shell wm size 1920x720` (depois `wm size reset`).
- Tela em pé: `adb shell wm size 1080x1920`.
- `uiautomator dump` falha enquanto o mapa anima; pare a simulação ou use coordenadas.
- Na primeira tela cheia o Android mostra "Viewing full screen" e segura o foco até tocar em "GOT IT".
- Traços pretos/amarelos no mapa são artefato do SwiftShader (renderização por software), não do app.
- fps do mapa: Diagnóstico → Info da central, ou
  `adb shell dumpsys SurfaceFlinger --latency 'SurfaceView - br.painellivre/br.painellivre.cluster.ClusterActivity#0'`.
  No emulador fica em ~15 andando (limite do PC); parado, o mapa nem redesenha.

### Roteiros de teste que já passaram no emulador (v0.2.0–v0.3.0)

| Cenário | Como | Esperado no log `PainelLivre` |
|---|---|---|
| Ativação | "Mostrar no painel" | `Painel medido` → `Tamanho do painel: …` → `sendInfo(16)` → 1 s → `sendInfo(35)` → `Tela do painel aberta` → `Primeiro quadro` |
| Código 30 manual | preferência `panel_size=30` | `sendInfo(30)` antes do 16 |
| Bloqueio 8,8" | overlay `1280x480/160` + `panel_size=30` | `BLOQUEADO: código 30 não combina com o painel medido 1280x480` |
| Estado velho | `projection_powered=true`, sem overlay, abrir e ativar | `limpando (18 → 0)` antes de ativar; desiste em 20 s |
| ADB no boot | `adb reverse --remove tcp:5555`, abrir o app, religar o reverse | `ADB local pronto na tentativa N` |
| Convivência | `adb shell am start --display 1 -n com.android.settings/.Settings` duas vezes | `retomando em 3 s`, depois `cedendo o painel` |
| Restaurar | "Painel original" | `sendInfo(18)` → 1 s → `sendInfo(0)` |
| Fim de rota | parar a simulação no modo `nav` | `Rota terminou há 20s — devolvendo o painel original` |

Para mexer nas preferências sem a interface (app debug):

```bash
adb shell "run-as br.painellivre cat shared_prefs/br.painellivre_preferences.xml"
```

(escrever: `adb shell am force-stop br.painellivre`, `adb push` para `/data/local/tmp/` e
`run-as br.painellivre sh -c 'cat /data/local/tmp/p.xml > shared_prefs/br.painellivre_preferences.xml'`).

## No carro

- Ligar o ADB por TCP na central (o mesmo passo do DashCast/SealNav) e conectar o notebook na mesma
  rede: `adb connect IP_DA_CENTRAL:5555`.
- Na primeira abertura o app pede "Permitir depuração USB?" para a chave dele (`files/adbkey`):
  marcar "Sempre permitir".
- O checklist do primeiro teste está em `docs/PLANO.md` (Fase 1). O Diagnóstico tem "Copiar tudo" para
  trazer as informações de volta.
- Só observar outros apps (SealNav, DashCast): `logcat`, `dumpsys display`, `dumpsys activity
  activities`. Nada de alterar o sistema além do que o próprio Painel Livre faz.

## Estudo de APKs (`tools/re/`)

Scripts próprios em Python (não precisam de Java):

| Script | Uso |
|---|---|
| `axml.py` | `python3 tools/re/axml.py app.apk > AndroidManifest.xml` — decodifica o manifesto binário |
| `dexdump.py` | `python3 tools/re/dexdump.py app.apk saida/` — índice JSON de classes, métodos, strings e chamadas por método |
| `q.py` | `python3 tools/re/q.py saida/ class 'BYDAutoAcDevice'` — consulta o índice (`callers`, `strings`, `fields`, `class`, `sub`) |
| `apksig.py` | `python3 tools/re/apksig.py app.apk` — certificados da assinatura v1/v2/v3 (usa o `openssl`) |

Para corpo de método, JADX (`~/tools/jadx/bin/jadx -d saida app.apk`). APKs de terceiros (BYD
Navigator, DashCast, SealNav) e o que sai da descompilação **não entram no repositório**.
