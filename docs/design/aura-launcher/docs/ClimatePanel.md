# ClimatePanel

Janela de climatização: temperatura por lado, ventilação, modos e bancos aquecidos/ventilados.

**Fornece:** `store` (lê e escreve `state.climate`). Use `standalone: true` fora do launcher.

- Temperatura de 16° a 30°, passo 0,5°. Sincronizar copia o motorista para o passageiro.
- Ventilação 1–7; mexer manualmente desliga o AUTO.
- Modos: AUTO, A/C, Recircular, Desembaçador dianteiro e traseiro, Sincronizar.
- Bancos: cada toque sobe um nível (1–3) e volta a desligado. Aquecer e ventilar no mesmo banco são exclusivos.
- Desligado: o conteúdo fica esmaecido e as temperaturas somem do dock.
