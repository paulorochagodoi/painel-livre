# BatteryPanel

Janela de bateria e autonomia: % da bateria, autonomia elétrica, a combustão e total, modos e opções de carga.

**Fornece:** `store` (lê e escreve `state.battery`; lê `vehicle.port` para o estado de carga).

- Modo de energia EV/HEV e condução Eco/Normal/Sport em segmentados.
- Manter carga (SOC) de 25% a 70%, passo 5%. Limite de carga 80/90/100%. Carregamento agendado. Descarga externa (V2L).
- Remova as linhas que a versão do carro não suportar; não deixe opção que não faz nada.
