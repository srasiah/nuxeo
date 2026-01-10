# ===== Load .env if present =====
ifneq (,$(wildcard .env))
include .env
export
endif

# ===== Configuration (defaults) =====
DOCKER_REGISTRY              ?= docker.platform.dev.nuxeo.com
DOCKER_BASE_IMAGE_NAME       ?= $(DOCKER_REGISTRY)/nuxeo
DOCKER_BENCHMARK_IMAGE_NAME  ?= $(DOCKER_REGISTRY)/nuxeo-benchmark
DOCKER_PLATFORMS             ?= linux/amd64,linux/arm64
DOCKER_IMAGE_TAG             ?= 2025.x
TARGETPLATFORM               ?= linux/amd64
REVISION                     ?= 2025.9-SNAPSHOT
BUILD_TAG                    ?= local
SCM_REF                      ?= $(shell git rev-parse HEAD 2>/dev/null || echo "local")
VERSION                      ?= $(DOCKER_IMAGE_TAG)
DOCKER_BASE_IMAGE            := $(DOCKER_BASE_IMAGE_NAME):$(DOCKER_IMAGE_TAG)
DOCKER_BENCHMARK_IMAGE       := $(DOCKER_BENCHMARK_IMAGE_NAME):$(DOCKER_IMAGE_TAG)
MAVEN_OPTS                   :='-Xmx4g -Xms2g -XX:+TieredCompilation -XX:TieredStopAtLevel=1'

# ===== Container Engine Detection =====
# Auto-detect podman or docker
CONTAINER_ENGINE ?= $(shell command -v podman 2>/dev/null || command -v docker 2>/dev/null || echo "docker")
CONTAINER_ENGINE_NAME := $(shell basename $(CONTAINER_ENGINE))

# Set DOCKER_HOST for container engine compatibility with Maven Docker plugin
ifeq ($(CONTAINER_ENGINE_NAME),podman)
    PODMAN_SOCKET := $(shell podman info --format '{{.Host.RemoteSocket.Path}}' 2>/dev/null)
    ifneq ($(PODMAN_SOCKET),)
        export DOCKER_HOST := unix://$(PODMAN_SOCKET)
    endif
else ifeq ($(CONTAINER_ENGINE_NAME),docker)
    # For Docker, set DOCKER_HOST to default socket if not already set
    ifndef DOCKER_HOST
        export DOCKER_HOST := unix:///var/run/docker.sock
    endif
    # Enable BuildKit for Docker
    export DOCKER_BUILDKIT := 1
endif

# ===== Maven Options =====
MAVEN_OPTS    := -Xmx4g -Xms2g -XX:+TieredCompilation -XX:TieredStopAtLevel=1
MAVEN_COMMON  := -B -DskipTests -Dnuxeo.skip.enforcer=true

# ===== Java Configuration =====
# Ensure JAVA_HOME is set for Maven builds
JAVA_HOME ?= $(shell dirname $(shell dirname $(shell readlink -f $(shell which javac 2>/dev/null) 2>/dev/null) 2>/dev/null) 2>/dev/null)
export JAVA_HOME

# Determine architecture profile
UNAME_M := $(shell uname -m)
ifeq ($(UNAME_M),arm64)
    ARCH_PROFILE := arm64
    DOCKER_PLATFORM := linux/arm64
else ifeq ($(UNAME_M),aarch64)
    ARCH_PROFILE := arm64
    DOCKER_PLATFORM := linux/arm64
else
    ARCH_PROFILE := amd64
    DOCKER_PLATFORM := linux/amd64
endif

# ===== Targets =====
.PHONY: all build-distrib build-parent build-base build-base-full build-benchmark build-benchmark-full push push-base push-benchmark \
        build-and-push build-and-push-base build-and-push-benchmark \
        clean-orphans nuxeo-run nuxeo-stop nuxeo-start nuxeo-rm \
        test help

# Default target
all: build-base build-benchmark

help:
	@echo "Nuxeo Container Build Targets:"
	@echo ""
	@echo "  make all                      - Build both base and benchmark images"
	@echo "  make build-distrib            - Build Nuxeo distribution (prerequisite for images)"
	@echo "  make build-parent             - Build parent distribution only (no Docker images)"
	@echo "  make build-base               - Build nuxeo base image (skips distrib rebuild)"
	@echo "  make build-base-full          - Build nuxeo base image with full distrib rebuild"
	@echo "  make build-benchmark          - Build nuxeo-benchmark image (skips distrib rebuild)"
	@echo "  make build-benchmark-full     - Build nuxeo-benchmark image with full distrib rebuild"
	@echo "  make build-and-push           - Build and push both images"
	@echo "  make build-and-push-base      - Build and push base image"
	@echo "  make build-and-push-benchmark - Build and push benchmark image"
	@echo "  make push                     - Push both images to registry"
	@echo "  make push-base                - Push base image to registry"
	@echo "  make push-benchmark           - Push benchmark image to registry"
	@echo ""
	@echo "Container Management:"
	@echo "  make nuxeo-run                - Run a nuxeo container"
	@echo "  make nuxeo-stop               - Stop the nuxeo container"
	@echo "  make nuxeo-start              - Start the nuxeo container"
	@echo "  make nuxeo-rm                 - Remove the nuxeo container"
	@echo ""
	@echo "Cleanup:"
	@echo "  make clean-orphans            - Remove dangling images"
	@echo ""
	@echo "Configuration:"
	@echo "  make test                     - Display current configuration"
	@echo ""
	@echo "Variables:"
	@echo "  CONTAINER_ENGINE=$(CONTAINER_ENGINE) ($(CONTAINER_ENGINE_NAME))"
	@echo "  DOCKER_REGISTRY=$(DOCKER_REGISTRY)"
	@echo "  DOCKER_IMAGE_TAG=$(DOCKER_IMAGE_TAG)"
	@echo "  TARGETPLATFORM=$(TARGETPLATFORM)"
	@echo "  REVISION=$(REVISION)"

# === Build Distribution ===
build-distrib:
	@echo "📦 Building Nuxeo distribution..."
	@export MAVEN_OPTS='$(MAVEN_OPTS)' && \
	cd .. && mvn install -Pdistrib -DskipTests -Drevision=$(REVISION)
	@echo "✅ Distribution build complete"

# === Build Parent Distribution ===
build-parent:
	@echo "📦 Building nuxeo-parent..."
	@export MAVEN_OPTS='$(MAVEN_OPTS)' && \
	cd ../parent && mvn clean install -DskipTests -Drevision=$(REVISION)
	@echo "✅ nuxeo-parent build complete"

# === Build Base Image ===
build-base:
	@echo "🐳 Building base image with $(CONTAINER_ENGINE_NAME): $(DOCKER_BASE_IMAGE)"
	@echo "   Platform: $(DOCKER_PLATFORM)"
	@echo "   Architecture Profile: $(ARCH_PROFILE)"
	@echo "   Docker Host: $(DOCKER_HOST)"
	@export MAVEN_OPTS='$(MAVEN_OPTS)' && \
	export DOCKER_HOST='$(DOCKER_HOST)' && \
	cd nuxeo && mvn install $(MAVEN_COMMON) \
	    -P$(ARCH_PROFILE) \
	    -Drevision=$(REVISION) \
	    -Ddocker.platforms=$(DOCKER_PLATFORM) \
	    -Ddocker.image.name=$(DOCKER_BASE_IMAGE)
	@echo "✅ Base image build complete"
	@# Tag for local benchmark builds
	@$(CONTAINER_ENGINE) tag $(DOCKER_BASE_IMAGE) $(DOCKER_BASE_IMAGE_NAME):$(REVISION)
	@-$(MAKE) clean-orphans

# === Build Base Image with full rebuild ===
build-base-full: build-distrib
	@$(MAKE) build-base

# === Build Benchmark Image ===
build-benchmark:
	@echo "🐳 Building benchmark image with $(CONTAINER_ENGINE_NAME): $(DOCKER_BENCHMARK_IMAGE)"
	@echo "   Platform: $(DOCKER_PLATFORM)"
	@echo "   Base image: $(DOCKER_BASE_IMAGE)"
	@echo "   Docker Host: $(DOCKER_HOST)"
	@export MAVEN_OPTS='$(MAVEN_OPTS)' && \
	export DOCKER_HOST='$(DOCKER_HOST)' && \
	cd nuxeo-benchmark && mvn install $(MAVEN_COMMON) \
	    -P$(ARCH_PROFILE) \
	    -Drevision=$(REVISION) \
	    -Ddocker.base.image=$(DOCKER_BASE_IMAGE) \
	    -Ddocker.platforms=$(DOCKER_PLATFORM) \
	    -Ddocker.buildx.enabled=true \
	    -Ddocker.image.name=$(DOCKER_BENCHMARK_IMAGE)
	@echo "✅ Benchmark image build complete"
	@-$(MAKE) clean-orphans

# === Build Benchmark Image with full rebuild ===
build-benchmark-full: build-distrib
	@$(MAKE) build-benchmark

# === Prepare Distribution (deprecated - use build-distrib) ===
_prepare-distrib: build-distrib

# === Build and Push ===
build-and-push: build-and-push-base build-and-push-benchmark

build-and-push-base: build-base
	@$(MAKE) push-base

build-and-push-benchmark: build-benchmark
	@$(MAKE) push-benchmark

# === Docker Push Targets ===
push: push-base push-benchmark

push-base:
	@echo "📤 Pushing base image $(DOCKER_BASE_IMAGE)..."
	$(CONTAINER_ENGINE) push $(DOCKER_BASE_IMAGE) --platform $(TARGETPLATFORM)
	@echo "✅ Base image pushed successfully"

push-benchmark:
	@echo "📤 Pushing benchmark image $(DOCKER_BENCHMARK_IMAGE)..."
	$(CONTAINER_ENGINE) push $(DOCKER_BENCHMARK_IMAGE) --platform $(TARGETPLATFORM)
	@echo "✅ Benchmark image pushed successfully"

# === Container Management ===
nuxeo-run:
	@if $(CONTAINER_ENGINE) ps -a --format '{{.Names}}' | grep -wq nuxeo; then \
		echo "⚠️  Container 'nuxeo' already exists. Use 'make nuxeo-rm' to remove it first."; \
	else \
		echo "🚀 Starting nuxeo container from $(DOCKER_BENCHMARK_IMAGE)..."; \
		$(CONTAINER_ENGINE) run -d --name nuxeo -p 8080:8080 $(DOCKER_BENCHMARK_IMAGE); \
		echo "✅ Nuxeo is running at http://localhost:8080"; \
	fi

nuxeo-stop:
	@echo "⏸️  Stopping nuxeo container..."
	@$(CONTAINER_ENGINE) stop nuxeo || echo "ℹ️  No running container named 'nuxeo'"

nuxeo-start:
	@echo "▶️  Starting nuxeo container..."
	@$(CONTAINER_ENGINE) start nuxeo || echo "ℹ️  No container named 'nuxeo' found"

nuxeo-rm:
	@echo "🗑️  Removing nuxeo container..."
	@$(CONTAINER_ENGINE) rm nuxeo -f || echo "ℹ️  No container named 'nuxeo' to remove"

# === Cleanup ===
clean-orphans:
	@echo "🧽 Removing dangling images..."
	@$(CONTAINER_ENGINE) image prune --force --filter "dangling=true" || echo "✅ No orphan images to clean"

# === Diagnostic ===
test:
	@echo "🔧 Container Build Configuration:"
	@echo ""
	@printf '%-30s %s\n' "Container Engine:" "$(CONTAINER_ENGINE) ($(CONTAINER_ENGINE_NAME))"
	@printf '%-30s %s\n' "DOCKER_HOST:" "$(DOCKER_HOST)"
	@printf '%-30s %s\n' "DOCKER_REGISTRY:" "$(DOCKER_REGISTRY)"
	@printf '%-30s %s\n' "DOCKER_BASE_IMAGE:" "$(DOCKER_BASE_IMAGE)"
	@printf '%-30s %s\n' "DOCKER_BENCHMARK_IMAGE:" "$(DOCKER_BENCHMARK_IMAGE)"
	@printf '%-30s %s\n' "DOCKER_IMAGE_TAG:" "$(DOCKER_IMAGE_TAG)"
	@printf '%-30s %s\n' "TARGETPLATFORM:" "$(TARGETPLATFORM)"
	@printf '%-30s %s\n' "Detected Platform:" "$(DOCKER_PLATFORM)"
	@printf '%-30s %s\n' "Architecture Profile:" "$(ARCH_PROFILE)"
	@printf '%-30s %s\n' "REVISION:" "$(REVISION)"
	@printf '%-30s %s\n' "BUILD_TAG:" "$(BUILD_TAG)"
	@printf '%-30s %s\n' "SCM_REF:" "$(SCM_REF)"
	@printf '%-30s %s\n' "VERSION:" "$(VERSION)"
	@printf '%-30s %s\n' "MAVEN_OPTS:" "$(MAVEN_OPTS)"
	@printf '%-30s %s\n' "MAVEN_COMMON:" "$(MAVEN_COMMON)"
	@echo ""
	@echo "$(CONTAINER_ENGINE_NAME) Status:"
	@$(CONTAINER_ENGINE) version --format '  Version: {{.Server.Version}}' 2>/dev/null || $(CONTAINER_ENGINE) version --format '  Version: {{.Version}}' 2>/dev/null || echo "  ❌ $(CONTAINER_ENGINE_NAME) not available"
	@$(CONTAINER_ENGINE) info --format '  Registry: {{.IndexServerAddress}}' 2>/dev/null || true
	@$(CONTAINER_ENGINE) info --format '  Username: {{.Username}}' 2>/dev/null | grep -q '.' && echo "  ✅ Logged in" || echo "  ⚠️  Not logged in"
