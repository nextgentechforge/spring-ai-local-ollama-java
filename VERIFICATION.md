# Verification record

Verified on 2026-09-19 using Amazon Corretto Java 21.0.12.1, Gradle wrapper 8.14.3, Spring Boot 4.1.1 and Spring AI 2.0.1.

## Build results

- `gradlew.bat build --no-daemon --console=plain`: BUILD SUCCESSFUL.
- `gradlew.bat clean build --no-daemon --console=plain`: BUILD SUCCESSFUL; all 8 tasks executed, 21 tests passed, zero failures or errors.
- Executable artifact: `build/libs/nextgentechforge-spring-ai-1.0.0.jar`.

## Automated checks

- `gradlew.bat clean test --no-daemon --console=plain`: PASS, 21 tests, zero failures.
- Controller: 8 cases covering HTTP success, health, validation, malformed input and sanitized provider errors.
- Service: 3 cases covering real ChatClient orchestration with a mocked ChatModel, UTC timestamps, failure and empty response handling.
- Tools: 4 cases covering known and unknown names, degraded health, annotation discovery and callback invocation.
- OpenAI integration: 5 cases booting the real application and exercising health, chat, both Markdown documents through RAG, and an SDK tool request → Java execution → SDK response round trip.
- Ollama integration: 1 case booting the separate profile without an OpenAI key and exercising embedding and chat adapters.
- Credential-pattern scan of tracked text: no OpenAI-style keys, AWS access-key identifiers or private-key blocks found. Environment files and build/cache outputs are ignored. Test credentials are explicit non-secret placeholders.

## Scope and limitations

Both integration suites use loopback HTTP provider stubs. The application, embedded web server, provider adapters, ChatClient, document loading, chunking, SimpleVectorStore, advisor and tool callbacks are real. Stub embeddings are fixed vectors and stub answers are scripted: these checks validate wiring and context propagation, not semantic ranking, real inference quality or autonomous live-model tool selection.

## Live Docker/Ollama checks after access was enabled

- Docker CLI and engine connectivity: PASS (Docker Desktop 4.91.0; Engine 29.8.0).
- `docker compose up -d`: PASS using the repository's pinned Ollama 0.11.10 image.
- `/api/version`: PASS, reported Ollama 0.11.10.
- Downloaded and registered `llama3.1:8b`, `nomic-embed-text`, and the smaller trial model `qwen2.5:3b`.
- Packaged Java 21 application startup with the Ollama profile: PASS.
- Real Markdown chunking and embedding through Ollama: PASS, four chunks indexed.
- `GET /api/health`: PASS, returned status UP.
- Live generated answers: NOT VERIFIED. The first llama3.1:8b request was cancelled after 421 seconds without a completed answer. A second trial with qwen2.5:3b also took several minutes without a completed answer and was stopped. Docker had approximately 5.7 GiB available, inference was CPU-only, and the Windows host had approximately 1 GB free RAM during the trial. These observations indicate resource pressure; they do not establish answer quality or a model correctness failure. Later questions in the scripted run could not be tested because the temporary application was deliberately stopped.
- Temporary Java test process stopped and inference models unloaded after the checks. The Ollama Compose container remains running; downloaded models remain cached in its volume.
- No OPENAI_API_KEY was available, so live OpenAI inference remains unverified.

The previous Docker access restriction was resolved by the user's permission change. The Docker CLI and credential helper required adding the per-user Docker bin directory to the command's PATH; this step is now documented in README.

The sandbox Java compiler could not resolve compiled project classes during test compilation. Running the same Gradle tasks through approved execution outside the sandbox resolved that environment issue. No workaround was added to the repository build configuration.

## Follow-up API testing and timeout correction

A fresh full Gradle build passed with 22 tests and zero failures after correcting Spring Boot 4's HTTP timeout property prefix from `spring.http.client` to `spring.http.clients`. A new integration test delays the Ollama-compatible provider and verifies HTTP 503 after the configured read timeout. `AI_READ_TIMEOUT` and `AI_CONNECT_TIMEOUT` now configure these limits.

Live health and Actuator health returned UP; blank, missing and malformed messages returned HTTP 400. Before the timeout correction, the real Qwen tool request exceeded the caller's 300-second limit. Live generated answers still have not passed acceptance. The updated application is restarted with Qwen 2.5 3B, four inference threads, and a 160-token generation limit. A Postman collection is included under `postman/`.

## Internet-search feature verification

- Full Gradle build: PASS; 32 tests, zero failures or errors. The eight new search-service cases cover encoding, result filtering, bounded snippets, failure handling, and tool callback invocation.
- Ollama integration now has four cases, including a deterministic provider tool request -> Java searchWeb -> search service -> tool response -> cited answer round trip, plus direct endpoint validation. Provider and search responses in this suite are stubs; this verifies wiring, not live inference quality.
- Pinned SearXNG container: RUNNING alongside Ollama. Live search returned source links. DuckDuckGo reported a CAPTCHA while other engines supplied results.
- Rebuilt Java application started with the Ollama profile and qwen2.5:3b. Live GET /api/health: HTTP 200, UP.
- Live GET /api/web/search?q=Spring%20Boot%20official%20releases: HTTP 200, status OK, three source URLs including https://github.com/spring-projects/spring-boot/releases. Empty query: HTTP 400.
- Postman collection parses successfully and includes nine requests, including direct search and web-grounded chat.
- Fresh live web-grounded chat attempt: NOT VERIFIED. The client timed out after 110.1 seconds without a completed response. HTTP provider timeouts are not an end-to-end conversation deadline. The real search endpoint is verified independently; a completed live local-model answer with citations remains unverified on this CPU-only setup.
- The updated application, Ollama, and SearXNG are left running for local use.
