# Aura Launcher — exportação

Launcher em HTML/CSS/JS puro (sem dependências), desenhado em 1280 × 720 e escalado para qualquer tela.

## Arquivos

- `index.html` — abre o launcher em tela cheia.
- `css/tokens.css` — cores (temas claro e escuro), espaçamentos, raios, tamanhos e sombras.
- `css/aura.css` — estilos de todos os componentes.
- `css/shell.css` — ajuste à tela e bloqueio de seleção de texto.
- `js/aura.js` — componentes (`window.Aura`): carro, atalhos, dock, janelas, busca e ícones.
- `js/shell.js` — monta a tela, escala para o display e lembra o tema escolhido.
- `js/aura.d.ts` — tipos da API e do estado.
- `docs/` — regras do design system e de cada componente, mais o `tokens.json`.

## Como testar

Abra `index.html` no navegador (ou o arquivo único `aura-launcher.html`). Toque nos atalhos, nas portas, no capô, no porta-malas e na tampa de carga do desenho. O botão no canto superior direito alterna claro/escuro.

## Como rodar na central (Android)

O launcher é uma página web. Para virar a tela inicial da central, ele precisa de um app Android que o carregue numa WebView e se declare como launcher (`android.intent.category.HOME`). Opções:

1. Um app WebView próprio apontando para `file:///android_asset/index.html` — é o caminho para ler dados reais do carro.
2. Um app de quiosque que aceite arquivo local como página inicial, só para testar no carro.

## Ligando aos dados reais do carro

Todo o estado fica num único `store` (`window.auraStore`). O app Android só precisa atualizá-lo:

```js
auraStore.update(s => {
  s.vehicle.doors.fl = true;        // porta dianteira esquerda aberta
  s.vehicle.windows.rl = 40;        // vidro traseiro esquerdo 40% aberto
  s.vehicle.port = 'charging';      // 'closed' | 'open' | 'charging'
  s.battery.soc = 62;
  s.climate.tL = 22.5;
});
```

Os comandos feitos na tela (temperatura, bancos, mídia, porta-malas) também passam pelo `store`. Use `auraStore.sub(s => …)` para enviá-los ao carro. Endereços, rádios e músicas são fictícios.

## Fontes

As fontes Sora e Albert Sans vêm do Google Fonts quando há internet; sem conexão, a fonte do sistema é usada. Para ficar 100% offline, baixe os .woff2 das duas famílias, coloque em `fonts/` e troque o `<link>` do Google Fonts por `@font-face` em `css/tokens.css`.
