Launcher para a central multimídia de SUVs elétricos e híbridos plug-in (referência: tela de 12,8"–15,6" em paisagem, desenho base 1280 × 720). O carro fica no centro com o estado de portas, vidros, capô, porta-malas e bocal de carga; abaixo, quatro atalhos — Navegação, Bateria e autonomia, Climatização e Mídia — que abrem janelas flutuantes ao lado do próprio botão. Tudo existe em tema **Claro** e **Escuro**.

## Princípios

- **Um olhar, um toque.** Quem usa está dirigindo. Cada informação precisa ser lida em menos de um segundo e cada ação feita com um toque. Nada de menus em cascata na tela inicial.
- **O carro é o herói.** O desenho central só mostra o que importa agora: tudo fechado fica neutro; qualquer abertura vira `warn` com palavra (“Aberta”, “40%”) — nunca só cor.
- **Janela perto do dedo.** Uma janela abre sempre ancorada no atalho que a chamou, com a seta apontando para ele, e fecha tocando fora, no X ou no mesmo atalho. Só uma janela aberta por vez.
- **Atalhos já trazem a resposta.** O tile mostra o dado principal (minutos até Casa, km de autonomia, temperatura, faixa tocando) antes de qualquer toque.

## Conteúdo e tom

- Português do Brasil, frases curtas, sem ponto final em rótulos. Primeira letra maiúscula só no início (“Banco motorista”, não “Banco Motorista”).
- Números no formato brasileiro: `22,0°`, `6,4 km`, `1h40`. Temperatura sempre com uma casa decimal; distância com uma casa abaixo de 10 km.
- Verbos no imperativo para ações (“Iniciar”, “Fechar”, “Encerrar”); estados no particípio (“Travado”, “Aberto”, “Carregando”).
- Sem emoji. Ícones de linha da biblioteca **Icons** em todos os lugares.

## Cores

- Fundo da tela: `bg`; horizonte decorativo: `horizon`; halo sob o carro: `platform-glow`.
- Tiles, chips e cartões em `surface` com borda `line` e sombra `shadow-tile` (no escuro a sombra some e a borda faz o trabalho). Janelas flutuantes em `surface-raised` com `shadow-pop` e raio `radius-xl`.
- Controles internos (−/+, segmentados, campo de busca) em `surface-sunken`.
- Texto principal `ink`; secundário `ink-muted`. Os dois passam 4,5:1 sobre `bg`, `surface`, `surface-raised` e `surface-sunken` nos dois temas.
- `accent` = ativo/selecionado, rota no mapa e carregamento. Texto sobre preenchimento `accent` sempre `on-accent`. Fundo de chip ativo = `accent-soft` com texto `accent`.
- `warn` / `warn-soft` = algo aberto (porta, vidro, capô, porta-malas, tampa de carga). `ok` = travado e tudo fechado. `alert` = falha ou bateria crítica. Estado nunca depende só da cor: sempre há ícone e palavra.
- Bancos: aquecer em `heat` / `heat-soft`, ventilar em `cool` / `cool-soft` — laranja contra azul, distinguíveis também por quem não enxerga vermelho/verde.
- Mapa: `map-bg`, `map-block`, `map-road`, `map-road-major`, `map-park`, `map-water`; a rota é `accent` com contorno `on-accent`.
- Anel de foco: 3px sólido em `accent`, afastado 2px.

## Tipografia

- **Sora** (`--font-display`) para números grandes: relógio (`clock`), temperatura e % nas janelas (`numeral-l`), valores nos tiles (`numeral-m`). Peso leve, algarismos tabulares.
- **Albert Sans** (`--font-ui`) para todo o resto: `title` nas janelas e tiles, `body` em listas, `label` em controles, `caption` em metadados (13px é o menor tamanho permitido).
- As duas vêm do Google Fonts: `https://fonts.googleapis.com/css2?family=Albert+Sans:wght@400;500;600;700&family=Sora:wght@300;400;600&display=swap`.

## Espaço, tamanho e forma

- Margem da tela `space-6` (32px); gap entre tiles `space-4`; padding das janelas `space-5`.
- Alvo de toque mínimo `touch-min` (56px); botão principal de janela `touch-primary` (80px). Botões secundários dentro de janelas podem ter 44px, nunca menos.
- Barra de status `status-height` (40px) e dock `dock-height` (80px) são fixas.
- Raios: `radius-sm` controles, `radius-md` cartões internos e botões do dock, `radius-lg` tiles, `radius-xl` janelas, `radius-pill` chips, switches e segmentados.

## Layout da tela inicial (1280 × 720)

| Zona | Posição | Conteúdo |
| --- | --- | --- |
| Barra de status | topo, 40px | sinal, Wi-Fi, Bluetooth · clima e bateria · troca de tema |
| Herói | 40–480 | relógio, data e temperatura à esquerda; modelo, trava e resumo à direita; **VehicleStatus** no centro |
| Atalhos | 486–624 | **ShortcutTiles**: Navegação · Bateria · Climatização · Mídia |
| Dock | 640–720 | **Dock**: início, temperatura do motorista, atalhos rápidos, temperatura do passageiro |

As janelas (**NavPanel**, **BatteryPanel**, **ClimatePanel**, **MediaPanel**) sobem a partir do tile, com altura máxima de 420px para nunca cobrir a barra de status. A busca completa (**NavSearch**) ocupa a área entre a barra de status e o dock, que continua visível.

## Movimento

- Janelas entram em 180ms (fade + 10px para cima). Portas do desenho giram em 350ms. Equalizador da mídia anima só quando está tocando.
- Com `prefers-reduced-motion` tudo isso é desligado.

## Iconografia

- Conjunto próprio em linha, grade 24px, traço 1.8, pontas arredondadas, `currentColor` (ver **Icons**). Play, pausa, anterior e próxima são preenchidos.
- Use `Aura.icon('nome')` para obter o SVG. Nunca misture com outra biblioteca de ícones.

## Usando no launcher

Carregue `tokens.css`, as fontes, `components/bundle.css` e `components/bundle.js` (JavaScript puro, sem React). Depois:

```js
const store = Aura.createStore();          // estado único: veículo, clima, mídia, bateria, navegação
Aura.mountLauncher(document.getElementById('tela'), { store });
store.update(s => { s.vehicle.doors.fl = true; });   // dados reais do carro entram por aqui
```

Cada parte também monta sozinha (`Aura.mountVehicleStatus`, `Aura.mountClimatePanel`…), compartilhando o mesmo `store`. Para trocar o tema: `Aura.setTheme('dark' | 'light')` (ou pelo botão da barra de status).

## Observações

- O carro é desenhado em vista de cima, frente para a direita: o lado de cima da tela é o lado do motorista. Essa vista mostra as quatro portas, os quatro vidros, capô, porta-malas e bocal ao mesmo tempo, o que uma vista 3/4 não permite.
- A posição do bocal de carga é configurável em `vehicle.portSide` (`'rear-right'` ou `'rear-left'`); confira no seu carro.
- As opções de bateria (EV/HEV, manter SOC, limite de carga, carga agendada, V2L) seguem as de um híbrido plug-in; esconda as que a sua versão não tiver.
- Endereços, estações e músicas são fictícios, só para demonstração.
