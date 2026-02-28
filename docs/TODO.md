# 🗺️ Summer Framework TODOs & Roadmap

## Future Enhancements
- [ ] **Protobuf Integration**: Investigate high-performance binary protocol support for fast, internal service-to-service APIs.
- [ ] **Config to Java Record Mapping**: Build an environment/properties configuration loader that maps directly to pure Java Records.

## Deferred / Out of Scope
- [ ] **AOT Compilation & APT Processing**: Explore Ahead-of-Time compilation or Annotation Processors to completely eliminate the minimal runtime reflection inside DI/Router. *(Note: The primary goal of Summer right now is removing historically opinionated baggage. Competing on AOT compilation with GraalVM/Quarkus is a distant "maybe").*
