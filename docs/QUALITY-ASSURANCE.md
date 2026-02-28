# Summer Framework: Code Quality & Static Analysis

This document outlines the standard tools and practices used to maintain the codebase quality of the Summer Framework. We prioritize modern, fast, and highly accurate tooling over legacy linters (such as Checkstyle or PMD) to ensure an excellent Developer Experience (DX).

## 1. Code Formatting: Spotless

**Tool**: `spotless-maven-plugin`
**Phase**: Pre-commit / Development
**Role**: 
Spotless acts as the uncompromising dictator of code style. It ensures that every line of code merging into the repository looks exactly the same, regardless of the developer's IDE or personal preferences. We currently use it to automatically format code and aggressively prune unused imports.

*   To apply formatting: `mvn spotless:apply`
*   To check formatting (in CI): `mvn spotless:check`

## 2. Compile-Time Bug Detection: Error Prone

**Tool**: Google Error Prone
**Phase**: Compilation (`mvn compile`)
**Role**: 
Error Prone hooks directly into the `javac` compiler. It acts as a safety net that catches common, yet silent bugs (like accidental reference equality `==` on Strings, swallowed Exceptions, or invalid `String.format` arguments) the moment the code is compiled. 

*   **Zero False Positives**: We prefer Error Prone because it explicitly focuses on real compiler-level bugs rather than opinionated stylistic warnings.
*   **Relationship to Others**: It runs *before* any runtime tests and does NOT conflict with external static analysis tools.

## 3. Deep Architectural Analysis: Qodana / SonarLint

**Tool**: JetBrains Qodana (or SonarQube/SonarLint)
**Phase**: CI/CD Pipeline / IDE (Post-compilation)
**Role**: 
While Error Prone catches immediate syntactical and logical errors during compilation, Sonar and Qodana exist to catch macroscopic architectural issues.

*   **Deep Scans**: They perform deep inter-procedural flow analysis to find complex `NullPointerException` paths, identify security vulnerabilities (OWASP top 10), and calculate technical debt / code smells (e.g., duplicated code blocks, overly complex methods).
*   **No Conflicts**: **Qodana and Error Prone DO NOT conflict.** They are highly synergistic. Error Prone is the aggressive bouncer at the compiler door, while Qodana is the auditor that reviews the entire architected building for structural integrity after it is built.

---

### Integration Roadmap

- [x] **Spotless**: Fully integrated via Maven.
- [ ] **Error Prone**: Pending integration into the root `pom.xml` build profiles.
- [ ] **Qodana / SonarQube**: Pending integration into GitHub Actions / GitLab CI pipelines for automated pull request checks.
