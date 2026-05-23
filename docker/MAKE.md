# Makefile Reference

Build, push, and manage Nuxeo container images using Maven and Docker/Podman.

## Prerequisites

- Docker or Podman
- Maven 3.x
- Java (JDK) — `JAVA_HOME` is auto-detected from `javac` on `$PATH`
- GNU coreutils (`readlink -f`) — on macOS: `brew install coreutils`

## Configuration

Copy `.env.example` to `.env` and adjust values for your environment:

```sh
cp .env.example .env
```

Any variable with `?=` in the Makefile can be overridden via `.env` or the CLI:

```sh
make build-base DOCKER_IMAGE_TAG=2025.9
```

### Key variables

| Variable | Default | Description |
|---|---|---|
| `DOCKER_REGISTRY` | `docker.platform.dev.nuxeo.com` | Container registry |
| `DOCKER_BASE_IMAGE_NAME` | `$(DOCKER_REGISTRY)/nuxeo` | Base image name (without tag) |
| `DOCKER_BENCHMARK_IMAGE_NAME` | `$(DOCKER_REGISTRY)/nuxeo-benchmark` | Benchmark image name |
| `DOCKER_IMAGE_TAG` | `2025.x` | Image tag |
| `REVISION` | `2025.9-SNAPSHOT` | Maven revision |
| `TARGETPLATFORM` | `linux/amd64` | Platform for `push-*-platform` targets |
| `BUILD_TAG` | `local` | Build identifier |
| `CONTAINER_ENGINE` | auto-detected | Path to `podman` or `docker` |

### Auto-detected variables

| Variable | Source |
|---|---|
| `CONTAINER_ENGINE` | `podman` if available, else `docker` |
| `DOCKER_HOST` | Podman socket path or `unix:///var/run/docker.sock` |
| `ARCH_PROFILE` | `arm64` on Apple Silicon / aarch64, else `amd64` |
| `DOCKER_PLATFORM` | `linux/arm64` or `linux/amd64` based on `uname -m` |
| `SCM_REF` | `git rev-parse HEAD` |
| `JAVA_HOME` | Derived from `which javac` |

## Targets

### Build

| Target | Description |
|---|---|
| `make all` | Build both base and benchmark images (default) |
| `make build-distrib` | Build Nuxeo Maven distribution (required before first image build) |
| `make build-parent` | Build `nuxeo-parent` POM only |
| `make build-base` | Build `nuxeo` base image (uses existing distribution) |
| `make build-base-full` | Build distribution then base image |
| `make build-benchmark` | Build `nuxeo-benchmark` image (uses existing distribution) |
| `make build-benchmark-full` | Build distribution then benchmark image |
| `make deploy` | Deploy distribution to Maven registry |

### Push

| Target | Description |
|---|---|
| `make push` | Push both images to registry |
| `make push-base` | Push base image |
| `make push-benchmark` | Push benchmark image |
| `make push-platform` | Push both images for `$(TARGETPLATFORM)` |
| `make push-base-platform` | Push base image for `$(TARGETPLATFORM)` |
| `make push-benchmark-platform` | Push benchmark image for `$(TARGETPLATFORM)` |
| `make build-and-push` | Build and push both images |
| `make build-and-push-base` | Build and push base image |
| `make build-and-push-benchmark` | Build and push benchmark image |

### Container management

| Target | Description |
|---|---|
| `make nuxeo-run` | Run a `nuxeo` container from the benchmark image on port 8080 |
| `make nuxeo-stop` | Stop the `nuxeo` container |
| `make nuxeo-start` | Start a stopped `nuxeo` container |
| `make nuxeo-rm` | Remove the `nuxeo` container |

### Utilities

| Target | Description |
|---|---|
| `make clean-orphans` | Remove dangling images |
| `make test` | Print current configuration and container engine status |
| `make help` | List all targets with descriptions |

## Common workflows

**First build on a new machine:**
```sh
make build-base-full        # builds distribution + base image
make build-benchmark-full   # builds distribution + benchmark image
```

**Iterative development (distribution already built):**
```sh
make build-base             # rebuild base image only
make build-benchmark        # rebuild benchmark image only
```

**Run Nuxeo locally:**
```sh
make nuxeo-run              # starts on http://localhost:8080
make nuxeo-stop
make nuxeo-rm
```

**Build and publish a release:**
```sh
make build-and-push DOCKER_IMAGE_TAG=2025.9 REVISION=2025.9
```

**Diagnose configuration:**
```sh
make test
```