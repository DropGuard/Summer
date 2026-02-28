# 🗺️ Summer Framework TODOs & Roadmap

## Future Enhancements
- [ ] **Protobuf Integration**: Investigate high-performance binary protocol support for fast, internal service-to-service APIs.
- [ ] **Config to Java Record Mapping**: Build an environment/properties configuration loader that maps directly to pure Java Records.
- [ ] **Static Analysis (Error Prone)**: Integrate Google Error Prone into the Maven compiler plugin to catch runtime bugs at compile-time.
- [ ] **CI/CD Quality Gate (Qodana)**: Setup JetBrains Qodana in local/CI environment to track code smells and deep architectural vulnerabilities (CI Pipeline created, waiting for Cloud Token).

## Deferred / Out of Scope
- [ ] **AOT Compilation & APT Processing**: Explore Ahead-of-Time compilation or Annotation Processors to completely eliminate the minimal runtime reflection inside DI/Router. *(Note: The primary goal of Summer right now is removing historically opinionated baggage. Competing on AOT compilation with GraalVM/Quarkus is a distant "maybe").*
