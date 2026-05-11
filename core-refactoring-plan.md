# Core Architecture Refactoring Plan

## Objective
Address critical architectural flaws in the Summer framework:
1. Replace the mock HTTP parser with a functional parser that reads real HTTP streams.
2. Refine the AOP mechanism to apply proxies selectively rather than universally.
3. Decouple the `Request` object from the DI container by shifting validation responsibilities to `WebContext`.

## Key Files & Context
- `summer-web/src/main/java/summer/web/server/HttpRequestParser.java`
- `summer-aop/src/main/java/summer/aop/MethodInterceptor.java`
- `summer-tx/src/main/java/summer/tx/TransactionInterceptor.java`
- `summer-core/src/main/java/summer/core/ApplicationContext.java`
- `summer-web/src/main/java/summer/web/Request.java`
- `summer-web/src/main/java/summer/web/WebContext.java`
- `summer-web/src/main/java/summer/web/server/HttpConnectionHandler.java`
- `summer-web/src/main/java/summer/web/server/HttpServer.java`
- `summer-web/src/main/java/summer/web/SummerApplication.java`

## Implementation Steps

### Phase 1: Real HTTP Parsing
1. **Update `HttpRequestParser.java`**:
   - Implement reading from `InputStream` (e.g., wrapping in a `BufferedReader` or raw byte reading).
   - Parse the Request-Line (Method, URI, HTTP Version).
   - Parse HTTP Headers line by line until an empty line `\r\n` is encountered.
   - Extract the `Content-Length` header if present.
   - Read the exact number of bytes specified by `Content-Length` for the request body.
   - Separate the URI into path and query string.
   - Return a properly populated `Request` object.

### Phase 2: Selective AOP Proxying
1. **Update `MethodInterceptor.java`**:
   - Add a default method: `default boolean supports(Class<?> targetClass) { return true; }`.
2. **Update `TransactionInterceptor.java`**:
   - Override the `supports` method to return `true` only if the `targetClass` contains any methods annotated with `@Transactional`.
3. **Update `ApplicationContext.java`**:
   - In the `instantiateBean` method, when retrieving `interceptors`, filter them using `interceptor.supports(instance.getClass())`.
   - Only wrap the instance in a proxy if the filtered list of interceptors is not empty.

### Phase 3: Decouple Request and DI
1. **Update `Request.java`**:
   - Remove the dependency on `ApplicationContext.getInstance()`.
   - Remove the validation logic from the `body(Class<T> type)` method. It should only handle JSON deserialization.
2. **Update `WebContext.java`**:
   - Add a private field for `BodyValidator validator`.
   - Add a constructor parameter or setter to inject the validator.
   - Implement `public <T> T body(Class<T> type)`:
     - Call `request.body(type)` to deserialize.
     - If `validator` is present and supports the type, perform validation. Throw an exception if validation fails.
     - Return the object.
3. **Update `HttpConnectionHandler.java` & `HttpServer.java` / `SummerApplication.java`**:
   - Retrieve the `BodyValidator` (if available) during DI startup in `SummerApplication` or pass it down through `HttpServer` to `HttpConnectionHandler`.
   - When constructing `WebContext` for each request in `HttpConnectionHandler`, inject the `BodyValidator`.

## Verification & Testing
1. **Parsing**: Send an actual HTTP POST request with a body using `curl` or Postman to verify it is correctly parsed.
2. **AOP**: Inspect logs or debug to ensure Beans without `@Transactional` are no longer wrapped in proxy classes.
3. **Validation**: Test a POST endpoint with an invalid body to ensure `WebContext.body(Class)` correctly triggers validation errors without `Request` depending on the DI context.
