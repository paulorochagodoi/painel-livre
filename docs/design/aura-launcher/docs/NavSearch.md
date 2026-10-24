# NavSearch

Busca completa de destino com lista de resultados à esquerda e mapa com rota à direita.

**Fornece:** `store`. Lê `ui.searchSel` (destino escolhido) e `ui.searchCat` (categoria).

- Campo com foco automático, busca sem acento e por categoria; resultados ordenados por distância.
- Sem busca: Favoritos e Recentes. Com destino: rota em `accent`, folha inferior com tempo em `ok`, distância, chegada e o botão Iniciar.
- Iniciar fecha a busca e coloca a rota no tile de Navegação.
- O mapa é ilustrativo; troque por um SDK de mapas mantendo as cores `map-*`.
