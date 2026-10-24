# ShortcutTiles

Quatro atalhos lado a lado — Navegação, Bateria e autonomia, Climatização e Mídia — que abrem as janelas.

**Fornece:** `store`. O atalho aberto fica em `state.ui.open` (`'nav' | 'battery' | 'climate' | 'media' | null`).

- Cada tile já mostra a resposta: minutos até Casa e Trabalho (ou a rota ativa), km e %, temperatura e modo, faixa tocando.
- O tile de Mídia tem play/pausa e próxima diretos, sem abrir a janela.
- Tile ativo: borda dupla `accent`. Mantenha a ordem: Navegação, Bateria, Clima, Mídia.
