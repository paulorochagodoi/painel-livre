# VehicleStatus

Carro em vista de cima com o estado de portas, vidros, capô, porta-malas e bocal de carregamento.

**Fornece:** um elemento de 680 × 360 e um `store`. Lê `state.vehicle`: `doors.{fl,fr,rl,rr}` (aberta = `true`), `windows.{fl,fr,rl,rr}` (0–100 % aberto), `hood`, `trunk`, `port` (`'closed' | 'open' | 'charging'`), `portSide`.

- Frente para a direita; lado do motorista (esquerdo) em cima.
- Fechado = neutro. Aberto = `warn` com tracejado (capô/porta-malas), porta girada para fora ou vidro com faixa `warn` proporcional à abertura.
- Carregando = tampa em `accent` com raio e cabo descendo até o chip **Carregamento**.
- Chips: Portas, Vidros, Capô, Porta-malas, Carregamento. O chip Vidros vira ação “Fechar” quando há vidro aberto; Porta-malas também.
- Não mostre o estado só pela cor: o chip sempre diz qual peça e como está.
