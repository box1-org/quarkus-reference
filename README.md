# Box1 Quarkus reference

Reference JVM application for the Box1 golden path. Generated with the official
Quarkus code service: Quarkus **3.40.1.1**, Java **21**, Maven Wrapper **3.9.16**.
Application code and GitHub-driven Tekton workflows live here; desired OpenShift state
lives in [`box1-gitops`](https://github.com/box1-org/box1-gitops).

## Run and test

With JDK 21 installed:

```sh
./mvnw quarkus:dev
./mvnw -B verify -DskipITs=false
```

Unit tests exercise input boundaries and configurable greetings. Quarkus HTTP
tests cover REST JSON responses, bad input, Java 21, health, OpenAPI and the
Micrometer counter. Failsafe repeats the HTTP tests against the packaged JVM
application using `@QuarkusIntegrationTest`.

| Endpoint | Purpose |
| --- | --- |
| `/api/greetings?name=Ada` | JSON greeting; defaults to `developer` |
| `/api/info` | Application version, Java major version, delivery mode, and full source commit SHA (`gitRef`) |
| `/q/health/live` | Liveness |
| `/q/health/ready` | Readiness |
| `/q/health/started` | Startup |
| `/q/openapi` | OpenAPI schema; request JSON with `Accept: application/json` |
| `/q/metrics` | Prometheus metrics, including `box1_greetings_total` |

`gitRef` is embedded into `META-INF/box1-build.properties` by Maven resource
filtering. Automated builds use the pipeline's exact `REVISION`; the manual
reference build uses `SOURCE_REVISION`. It identifies the code packaged into the
image and is independent of runtime configuration. Local builds default to
`unknown`; supply `-Dgit.ref="$(git rev-parse HEAD)"` to Maven to embed a local ref.
Only the build-metadata resource is filtered, preserving Quarkus runtime config
expressions in `application.properties`.

`GREETING_PREFIX` defaults to `Hello` locally. OpenShift's ConfigMap changes it to
`Hello from box1`. Blank names and names longer than 80 characters return HTTP 400.

## Automated CI/CD

Application workflows live in `.tekton/`; shared Tasks are maintained in
`box1-gitops/platform/pipelines/tasks` and synchronized into `box1-ci` by Argo CD.
Pipelines as Code resolves the named cluster Tasks for each GitHub event and
reports the final result to GitHub.

- Pull requests targeting `main`: fetch exact source SHA, compile, unit/HTTP and
  packaged JVM tests, Checkstyle, and SpotBugs with FindSecBugs. No image is pushed.
- Pushes/merges to `main`: repeat verification, build with Jib, push a SHA-tagged
  image, and capture the immutable image digest.
- Promotion opens a GitOps PR changing only the reference image digest and source
  annotations. Development merges automatically after the required GitOps checks
  succeed on the exact PR head SHA. An `approval` policy leaves the PR open.
- Argo CD deploys the merged desired state. A read-only observer waits for the
  expected source revision and running digest, then smoke-tests the HTTPS Route.
  No deployment command is executed by Tekton or a human after the application
  merge. A failed test, security/lint check, GitOps check, rollout, or smoke test
  fails the corresponding GitHub check. A stale source build is not promoted.

`ci-test` has no automatically mounted API token, registry push access, or mounted
GitOps credential in the Maven container. PAC uses default-branch provenance, so
PR edits cannot replace the pipeline definition used to execute that PR. Source
fetch is platform-owned code with the temporary GitHub token only in that step.
Trusted main builds use `ci-build` for registry push, `ci-gitops` for the promotion
API calls, and `ci-observe` for read-only rollout observation. Only platform
maintainers should have direct Pod/PipelineRun creation rights in `box1-ci`.

PAC issues short-lived App tokens scoped explicitly to this repository plus
`box1-org/box1-gitops`. No static source Secret or personal access token is needed.
The build uses the existing UBI Java 21 runtime and Maven checksum pins. Jib needs
no Docker daemon, socket, or privileged container. Static analysis checks source
bugs/security patterns; it is not a dependency-vulnerability database scan.

The required development GitOps checks are:

- `Pipelines as Code CI / box1-phase1-smoke-`
- `Pipelines as Code CI / box1-gitops-validate-`

The promotion Task enforces these checks, the expected App identity, immutable
head SHA, and exact allowed file contents before calling GitHub's merge API.
It does not bypass configured GitHub branch rules; additional approval/rules can
prevent the merge. Other environments should select `approval` and retain their
own reviewer/branch rules. GitOps and application repository changes remain
independently visible for review.

## Manual reference build

`ci/reference-build.yaml` is retained as the Phase 2 reproduction path. It needs
a separately provisioned short-lived `quarkus-reference-source` Secret. Normal
GitHub CI/CD uses `.tekton/` and does not require that manual Secret or submission.

## Live HTTP smoke test

```sh
python3 scripts/smoke.py https://quarkus-reference.apps.ocp.box1.org
```

The smoke script checks the ConfigMap greeting, Java 21, all health probes,
OpenAPI, Prometheus metrics and HTTP 400 validation, with TLS verification enabled.
Health and metrics are publicly reachable through the reference lab Route; review
exposure and authentication before adapting this application for sensitive data.

The self-service Developer Hub template remains a subsequent phase. Native
images are deferred.
