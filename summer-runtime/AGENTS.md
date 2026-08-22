# summer-runtime — Runtime DI Engine

## OVERVIEW

Reflection-based DI engine. Scans Jandex `.idx` files at startup, discovers beans, resolves constructor dependencies, instantiates reflectively, applies JDK dynamic AOP proxies, and returns an immutable `BeanContainer`. Default engine for development and tests (`Engine.RUNTIME`). Pure DI engine with zero direct dependencies on the HTTP web layer.

## KEY ENTRY POINTS

- **`RuntimeBootstrap.build()`** — production static entry point, called reflectively by `DiEngine`.
- **`RuntimeContainer`** — `ContainerEngine` SPI implementation; orchestrates `BuildPipeline` for bean resolution.
- **`JandexIndexLoader`** — loads and merges `META-INF/jandex.idx` across classpath archives.

## CONVENTIONS

- **Flat package only** — all classes live directly in `com.github.dropguard.summer.runtime`. No sub-packages in main.
- **Jandex-driven discovery** — no classpath directory scanning. Modules must generate `META-INF/jandex.idx`.
- **Shared Discovery Pipeline** — shares `BuildPipeline`, `Discovery`, `BeanEnrichment`, `SharedConditionEvaluator` with `summer-aot-engine` via `summer-engine`.
- **Pure DI boundary** — web routing and HTTP parameter resolution belong in `summer-runtime-web`, not here.
- **ArchUnit boundary** — `java.lang.reflect` and `java.lang.invoke` imports are confined to this module (and `RuntimeDiMarker` in `summer-core`).

## ANTI-PATTERNS

- ~~Loading classes outside the Jandex index~~ — breaks the dual-engine contract.
- ~~Holding `IndexView` after container build~~ — not needed at steady-state runtime.
- ~~Mutable `BeanContainer` after build~~ — container is immutable by design.
- ~~Web dependencies~~ — `summer-runtime` must never depend on `summer-web` or `summer-web-http`.
- ~~Direct `ProxyFactory.create()`~~ — always go through `RuntimeAopProcessor`.
