# Taskfile Reference

Build, push, and manage Nuxeo container images using Maven and Docker/Podman.

## Prerequisites

- [Task](https://taskfile.dev/installation/) — `brew install go-task`
- Docker or Podman
- Maven 3.x
- Java (JDK) — `JAVA_HOME` is auto-detected from `javac` on `$PATH`
- GNU coreutils (`readlink -f`) — on macOS: `brew install coreutils`

## Configuration

Copy `.env.example` to `.env` and adjust values for your environment:

```sh
cp .env.example .env
```

`.env` is loaded automatically via `dotenv`. Any variable can also be overridden on the CLI:

```sh
task build-base DOCKER_IMAGE_TAG=2025.9
```

### Key variables

| Variable | Default | Description |
|---|---|---|
| `DOCKER_REGISTRY` | `docker.platform.dev.nuxeo.com` | Container registry |
| `DOCKER_BASE_IMAGE_NAME` | `$DOCKER_REGISTRY/nuxeo` | Base image name (without tag) |
| `DOCKER_BENCHMARK_IMAGE_NAME` | `$DOCKER_REGISTRY/nuxeo-benchmark` | Benchmark image name |
| `DOCKER_IMAGE_TAG` | `2025.x` | Image tag |
| `REVISION` | `2025.9-SNAPSHOT` | Maven revision |
| `TARGETPLATFORM` | `linux/amd64` | Platform for `push-*-platform` tasks |
| `BUILD_TAG` | `local` | Build identifier |
| `CONTAINER_ENGINE` | auto-detected | Path to `podman` or `docker` |

### Auto-detected variables

| Variable | Source |
|---|---|
| `CONTAINER_ENGINE` | `podman` if available, else `docker` |
| `DOCKER_HOST` | Podman socket or `unix:///var/run/docker.sock` for Docker |
| `ARCH_PROFILE` | `arm64` on Apple Silicon / aarch64, else `amd64` |
| `DOCKER_PLATFORM` | `linux/arm64` or `linux/amd64` based on `uname -m` |
| `SCM_REF` | `git rev-parse HEAD` |
| `JAVA_HOME` | Derived from `which javac` |

## Tasks

```sh
task --list     # show all tasks with descriptions
task info       # print current configuration and container engine status
```

### Build

| Task | Description |
|---|---|
| `task` | Build both base and benchmark images (default) |
| `task build-distrib` | Build Nuxeo Maven distribution (required before first image build) |
| `task build-parent` | Build `nuxeo-parent` POM only |
| `task build-base` | Build `nuxeo` base image (uses existing distribution) |
| `task build-base-full` | Build distribution then base image |
| `task build-benchmark` | Build `nuxeo-benchmark` image (uses existing distribution) |
| `task build-benchmark-full` | Build distribution then benchmark image |
| `task deploy` | Deploy distribution to Maven registry |

### Push

| Task | Description |
|---|---|
| `task push` | Push both images to registry (in parallel) |
| `task push-base` | Push base image |
| `task push-benchmark` | Push benchmark image |
| `task push-platform` | Push both images for `$TARGETPLATFORM` (in parallel) |
| `task push-base-platform` | Push base image for `$TARGETPLATFORM` |
| `task push-benchmark-platform` | Push benchmark image for `$TARGETPLATFORM` |
| `task build-and-push` | Build and push both images (sequential) |
| `task build-and-push-base` | Build and push base image |
| `task build-and-push-benchmark` | Build and push benchmark image |

### Container management

| Task | Description |
|---|---|
| `task nuxeo-run` | Run a `nuxeo` container from the benchmark image on port 8080 |
| `task nuxeo-stop` | Stop the `nuxeo` container |
| `task nuxeo-start` | Start a stopped `nuxeo` container |
| `task nuxeo-rm` | Remove the `nuxeo` container |

### Utilities

| Task | Description |
|---|---|
| `task clean-orphans` | Remove dangling images |
| `task info` | Print current configuration and container engine status |

## Common workflows

**First build on a new machine:**
```sh
task build-base-full        # builds distribution + base image
task build-benchmark-full   # builds distribution + benchmark image
```

**Iterative development (distribution already built):**
```sh
task build-base             # rebuild base image only
task build-benchmark        # rebuild benchmark image only
```

**Run Nuxeo locally:**
```sh
task nuxeo-run              # starts on http://localhost:8080
task nuxeo-stop
task nuxeo-rm
```

**Build and publish a release:**
```sh
task build-and-push DOCKER_IMAGE_TAG=2025.9 REVISION=2025.9
```

**Diagnose configuration:**
```sh
task info
```

## Notes

- `.env` values take precedence over Taskfile defaults. CLI vars (`task foo VAR=value`) take precedence over both.
- `push` and `push-platform` run their two subtasks in **parallel**. `build-and-push` runs build then push **sequentially** (benchmark depends on the base image being built first).
- Container engine format strings (`{{.Names}}`, `{{.Server.Version}}`, etc.) are passed to Docker/Podman using Taskfile's `{{"{{" }}` escape syntax so they are not interpolated by Taskfile's own template engine.