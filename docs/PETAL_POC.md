# Prova de conceito: Petal Maps (Huawei) como provedor opcional

Branch `poc/petal-maps`. **Não é para uso no dia a dia.** Serve só para ver se o Petal funciona
tecnicamente num Android 10 genérico (DiLink 3), sem HMS Core, comparado ao Photon/OSRM.

## Aviso sobre os termos

O *HUAWEI Map Service Agreement* (Map Kit, Site Kit e Navi Kit) proíbe uso em carro e em navegação.
A cláusula 2.1.1 veta "Automotive Usage" e "Navigation Functionality", e a 2.1.2 veta sistemas
embarcados no veículo, com o exemplo de um app na central multimídia. O documento também proíbe
transformar o tempo de trajeto em voz (2.1.4) e limita o cache a 30 dias (2.1.10).
Fonte: <https://terms-drcn.platform.dbankcloud.cn/agreementservice/developer/getAgreementTemplate?agrType=1070&country=ove&language=en_us&version=2022091401>.
O dono foi avisado e pediu o teste como prova de conceito. Por isso tudo fica **desligado por padrão**.

## O que entrou

| Função | Petal (opcional) | Padrão e reserva automática |
|---|---|---|
| Busca | Site Kit REST, Keyword Search (`siteapi.cloud.huawei.com/.../searchByText`) | Photon |
| Rota e ETA | Directions REST, driving (`mapapi.cloud.huawei.com/.../routeService/driving`), ETA por `durationInTraffic` | OSRM (servidor de demonstração) |
| Desenho do mapa | Tile API raster (`.../mapService/getTile`) num estilo MapLibre mínimo | OpenFreeMap |

- Código: `nav/petal/PetalApi.kt` (Kotlin puro: pedidos e leitura das respostas),
  `nav/petal/MapProviders.kt` (escolha do provedor, reserva automática e comparação) e `nav/petal/MapTiles.kt`.
- **Reserva automática:** se o Petal falhar (sem rede, chave inválida, `returnCode` diferente de 0,
  sem resultado ou sem rota), a busca cai no Photon e a rota cai no OSRM. O registro anota o motivo.
- **ETA com trânsito:** o tempo de cada trecho é multiplicado por `durationInTraffic / duration`, e o
  `RouteTracker` (o mesmo do OSRM) passa a contar o tempo restante com trânsito.
- **Tiles:** são raster e não têm tema escuro. As ruas não ficam mais largas (`MapStyler` só mexe em vetores).
- **Ajustes → "Mapas: prova de conceito Petal"**:
  - "Usar Petal na busca e na rota", desligado por padrão;
  - "Mapa do Petal (tiles raster)", desligado e dependente do anterior;
  - "Chave do Petal", que mostra de onde veio a chave sem exibir o valor;
  - "Comparar Photon/OSRM × Petal".
- **Comparação:** faz a mesma busca nos dois provedores e mostra os resultados lado a lado.
  Depois calcula a rota do GPS até o primeiro resultado de cada um e mostra distância, tempo sem
  trânsito, tempo com trânsito e latência de cada chamada. O texto também vai para o registro do
  debug (fonte `mapas`) e para o `AppLog`. A comparação não usa a reserva automática: cada provedor
  aparece como respondeu.

## A chave nunca vai para o repositório

O app procura a chave nesta ordem:
1. arquivo `/sdcard/Android/data/br.painellivre/files/petal_api_key.txt`, enviado por
   `adb push petal_api_key.txt /sdcard/Android/data/br.painellivre/files/`;
2. `BuildConfig.PETAL_API_KEY`, que vem da linha `petal.apiKey=...` do `local.properties` ou da
   variável de ambiente `PETAL_API_KEY` na hora do build.

`local.properties` e `petal_api_key.txt` estão no `.gitignore`. A URL com a chave nunca é registrada.
Uma chave dentro do APK pode ser extraída por quem tiver o arquivo, então use uma chave só deste
teste e restrinja as APIs dela (veja abaixo).

## Teste sem conta (27/11/2026)

Não existe modo de teste sem chave. Chamamos os três endpoints com uma chave inválida: busca, rota e
tile responderam **HTTP 401** e `{"returnCode": "6","returnDesc": "REQUEST_DENIED"}`. Nesse caso o app
registra o erro e volta ao Photon/OSRM.

## Pontos a validar com a chave real

- A busca em português (`language: "pt"`) e o raio de 50 km em volta do GPS.
- **Hipótese das manobras:** tratamos a `action` do trecho *i* como a manobra no fim dele, já que o
  último trecho vem com `end`. Se as setas aparecerem adiantadas ou atrasadas um trecho, inverter em
  `PetalApi.parseDriving`.
- Se `durationInTraffic` vem preenchido no Brasil. Se vier 0, o app usa o tempo normal.
- Os tiles na região de São Caetano do Sul e no painel de 1920×720.

## Passo a passo para criar a chave (o que depende do dono)

1. **Conta de desenvolvedor Huawei:** criar um HUAWEI ID em <https://developer.huawei.com> e fazer a
   verificação de identidade como **pessoa física**. A documentação pede documento oficial (RG,
   passaporte ou CNH) mais um documento bancário, todos com o nome completo, e a análise leva de 1 a 2
   dias úteis (<https://developer.huawei.com/consumer/en/doc/start/ibca-0000001062388135>).
2. **AppGallery Connect** (<https://developer.huawei.com/consumer/en/service/josp/agc/index.html>):
   - My projects → **Add project**, com o nome "Painel Livre PoC";
   - escolher o **local de processamento de dados** pedido pelo console (o Brasil não aparece como
     opção; usar o que o console oferecer);
   - **Add app**: plataforma Android, dispositivo Mobile phone, pacote **`br.painellivre`**.
3. **Manage APIs**: ligar **Map Kit** e **Site Kit**. As rotas fazem parte do Map Kit/Navi Kit;
   ligar o Navi Kit também, se aparecer.
4. **Impressão digital do certificado** (Project settings → General information → SHA-256
   certificate fingerprint). É o certificado de release que usamos (`CN=Painel Livre, O=Uso pessoal, C=BR`):
   `72:90:20:4A:01:FA:20:68:42:06:E3:4D:51:6D:0A:EE:A9:A2:45:17:DC:D3:71:B7:8E:88:23:22:15:2E:13:D9`.
   O valor foi conferido com `apksigner verify --print-certs` nos APKs 0.5.7 e 0.5.8-teste. Ele só é
   obrigatório para o SDK Android; como aqui usamos REST, serve para restringir a chave.
5. **Chave de API**: em Project settings → General information → App information, copiar a
   **API key**. Se o console permitir, restringir a chave às APIs de Map Kit e Site Kit.
6. **Cartão e cobrança**: a tabela oficial dá cota gratuita para pessoa física, sem publicar o
   tamanho dela. Para empresa, as APIs pagas exigem ativar o plano pay-as-you-go no projeto, que tem
   um cupom mensal de US$ 300 (<https://developer.huawei.com/consumer/en/doc/HMSCore-Guides/about-charging-0000001051637068>).
   Para esta prova de conceito, **não ativar o plano pago**. Se a cota gratuita acabar, as chamadas
   falham e o app volta ao Photon/OSRM.
7. Entregar a chave ao agente por canal privado, nunca no chat do GitHub. Ela vai para o
   `local.properties` do notebook ou para o `petal_api_key.txt` da central.
