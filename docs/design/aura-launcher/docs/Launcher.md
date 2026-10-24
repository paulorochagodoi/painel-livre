# Launcher

Tela inicial completa do launcher: relógio, carro com estado das aberturas, quatro atalhos e dock.

**Fornece:** um elemento vazio de 1280 × 720 e, opcionalmente, um `store` criado com `Aura.createStore()`.

```js
Aura.mountLauncher(el, { store })
```

- Escale o elemento inteiro (`transform: scale()`) para outras resoluções; não reorganize as zonas.
- Toque num atalho para abrir sua janela; toque fora, no X ou no atalho de novo para fechar.
- Toque em portas, capô, porta-malas ou tampa de carga do desenho para simular estados (em produção, esses estados vêm do carro).
- Não coloque widgets extras no herói: o espaço ao redor do carro é dos chips de estado.
