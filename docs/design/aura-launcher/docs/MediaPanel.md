# MediaPanel

Janela de mídia: capa, faixa, progresso, anterior / play-pausa / próxima, fonte e volume.

**Fornece:** `store` (lê e escreve `state.media`).

- Play/pausa é o único botão `accent` e o maior (`touch-primary`).
- Fontes: Rádio, Bluetooth, USB. No rádio a barra mostra “Ao vivo” e anterior/próxima trocam de estação.
- Anterior volta ao início da faixa se já passou de 10% e, no segundo toque, vai para a faixa anterior.
