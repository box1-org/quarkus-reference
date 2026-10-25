# Box1 Quarkus reference

Reference JVM application for the Box1 golden path. Generated with the official
Quarkus code service: Quarkus **3.40.1.1**, Java **21**, Maven Wrapper **3.9.16**.
Application code and its manual Tekton build live here; desired OpenShift state
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
| `/api/info` | Application version and Java major version |
| `/q/health/live` | Liveness |
| `/q/health/ready` | Readiness |
| `/q/health/started` | Startup |
| `/q/openapi` | OpenAPI schema; request JSON with `Accept: application/json` |
| `/q/metrics` | Prometheus metrics, including `box1_greetings_total` |

`GREETING_PREFIX` defaults to `Hello` locally. OpenShift's ConfigMap changes it to
`Hello from box1`. Blank names and names longer than 80 characters return HTTP 400.

## Build and delivery

`ci/reference-build.yaml` is a **manual** Tekton PipelineRun for Phase 2; it is
outside `.tekton/` and is not yet a self-service/PAC application template.
The operator submits it with a full source commit SHA. It downloads that commit
using a short-lived, Contents-read GitHub installation token from the transient
`box1-ci/quarkus-reference-source` Secret. The token is available only to the
source-fetch step; it is not mounted in Maven build steps or the runtime.
Revoke the token and remove that transient Secret after the build completes.

The fetch step bootstraps the exact Maven ZIP pinned by the wrapper properties,
verifies its SHA-256 and extracts it with Python. This handles the minimal UBI
Java image without an `unzip` binary and preserves checksum verification.
Tekton runs `verify -DskipITs=false` before building/pushing with the Quarkus Jib
extension. It uses the existing `ci-build` identity, writes only to the `box1-ci`
registry project, and has no application deployment permissions. This trusted
reference build is for platform-maintainer code; it is not an untrusted PR runner.
Jib requires no Docker socket, daemon, privileged container, or native compilation.
The build image and UBI 9 OpenJDK 21 runtime are pinned to verified image digests.
Registry TLS verification stays enabled; push uses the public TLS Route and a
short-lived Kubernetes service-account token. The PipelineRun emits `image-digest`.

To submit from a checkout after provisioning the short-lived source Secret:

```sh
python3 - <<'PYCODE' > /tmp/quarkus-reference-build.yaml
from pathlib import Path
import subprocess
sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
print(Path('ci/reference-build.yaml').read_text().replace('REPLACE_WITH_COMMIT_SHA', sha))
PYCODE
kubectl create -f /tmp/quarkus-reference-build.yaml
```

After a successful build, the platform operator records the emitted digest in
`box1-gitops/workloads/quarkus-reference/kustomization.yaml`. The Argo CD
`quarkus-reference` Application reconciles that Git state. No application
PipelineRun runs `oc apply`, `kubectl apply`, or an Argo sync command.

The runtime pulls by digest from the internal registry service. Its dedicated
ServiceAccount has pull access to the `box1-ci` image project, no push access,
and no mounted Kubernetes API token. Deployment, Service, HTTPS Route, ConfigMap,
health probes, resource bounds, and rollout policy are all tracked in GitOps.

## Live HTTP smoke test

```sh
python3 scripts/smoke.py https://quarkus-reference.apps.ocp.box1.org
```

The smoke script checks the ConfigMap greeting, Java 21, all health probes,
OpenAPI, Prometheus metrics and HTTP 400 validation, with TLS verification enabled.
Health and metrics are publicly reachable through the reference lab Route; review
exposure and authentication before adapting this application for sensitive data.

The self-service Developer Hub template, automatic cross-repo GitOps updates,
and untrusted PR isolation are subsequent phases. Native images are deferred.
