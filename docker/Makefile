# ===== Load .env if present =====
ifneq (,$(wildcard .env))
include .env
export
endif

# ===== Configuration (defaults) =====
DOCKER_REGISTRY              ?= docker.platform.dev.nuxeo.com
DOCKER_BASE_IMAGE_NAME       ?= $(DOCKER_REGISTRY)/nuxeo
DOCKER_BENCHMARK_IMAGE_NAME  ?= $(DOCKER_REGISTRY)/nuxeo-benchmark
DOCKER_IMAGE_TAG             ?= 2025.x
TARGETPLATFORM               ?= linux/amd64
REVISION                     ?= 2025.9-SNAPSHOT
BUILD_TAG                    ?= local
SCM_REF                      ?= $(shell git rev-parse HEAD 2>/dev/null || echo "local")
VERSION                      ?= $(DOCKER_IMAGE_TAG)
DOCKER_BASE_IMAGE            := $(DOCKER_BASE_IMAGE_NAME):$(DOCKER_IMAGE_TAG)
DOCKER_BENCHMARK_IMAGE       := $(DOCKER_BENCHMARK_IMAGE_NAME):$(DOCKER_IMAGE_TAG)

# ===== Maven Options =====
MAVEN_OPTS    := -Xmx4g -Xms2g -XX:+TieredCompilation -XX:TieredStopAtLevel=1
MAVEN_COMMON  := -B -DskipTests -Dnuxeo.skip.enforcer=true

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
.PHONY: all build-base build-benchmark push push-base push-benchmark \
        build-and-push build-and-push-base build-and-push-benchmark \
        clean-orphans nuxeo-run nuxeo-stop nuxeo-start nuxeo-rm \
        test help

# Default target
all: build-base build-benchmark

help:
	@echo "Nuxeo Docker Build Targets:"
	@echo ""
	@echo "  make all                      - Build both base and benchmark images"
	@echo "  make build-base               - Build nuxeo base image"
	@echo "  make build-benchmark          - Build nuxeo-benchmark image"
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
	@echo "  make clean-orphans            - Remove dangling Docker images"
	@echo ""
	@echo "Configuration:"
	@echo "  make test                     - Display current configuration"
	@echo ""
	@echo "Variables:"
	@echo "  DOCKER_REGISTRY=$(DOCKER_REGISTRY)"
	@echo "  DOCKER_IMAGE_TAG=$(DOCKER_IMAGE_TAG)"
	@echo "  TARGETPLATFORM=$(TARGETPLATFORM)"
	@echo "  REVISION=$(REVISION)"

# === Build Base Image ===
build-base: _prepare-distrib
	@echo "🐳 Building Docker base image: $(DOCKER_BASE_IMAGE)"
	@echo "   Platform: $(DOCKER_PLATFORM)"
	@echo "   Architecture Profile: $(ARCH_PROFILE)"
	@export MAVEN_OPTS='$(MAVEN_OPTS)' && \
	cd nuxeo && mvn install $(MAVEN_COMMON) \
	    -P$(ARCH_PROFILE) \
	    -Drevision=$(REVISION) \
	    -Ddocker.platforms=$(DOCKER_PLATFORM) \
	    -Ddocker.image.name=$(DOCKER_BASE_IMAGE)
	@echo "✅ Base image build complete"
	@-$(MAKE) clean-orphans

# === Build Benchmark Image ===
build-benchmark: _prepare-distrib
	@echo "🐳 Building Docker benchmark image: $(DOCKER_BENCHMARK_IMAGE)"
	@echo "   Platform: $(DOCKER_PLATFORM)"
	@echo "   Base image: $(DOCKER_BASE_IMAGE)"
	@export MAVEN_OPTS='$(MAVEN_OPTS)' && \
	cd nuxeo-benchmark && mvn install $(MAVEN_COMMON) \
	    -P$(ARCH_PROFILE) \
	    -Drevision=$(REVISION) \
	    -Ddocker.base.image=$(DOCKER_BASE_IMAGE) \
	    -Ddocker.platforms=$(DOCKER_PLATFORM) \
	    -Ddocker.image.name=$(DOCKER_BENCHMARK_IMAGE)
	@echo "✅ Benchmark image build complete"
	@-$(MAKE) clean-orphans

# === Prepare Distribution ===
_prepare-distrib:
	@echo "📦 Preparing Nuxeo distribution..."
	@export MAVEN_OPTS='$(MAVEN_OPTS)' && \
	cd .. && mvn install -Pdistrib -DskipTests -Drevision=$(REVISION)

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
	docker push $(DOCKER_BASE_IMAGE)
	@echo "✅ Base image pushed successfully"

push-benchmark:
	@echo "📤 Pushing benchmark image $(DOCKER_BENCHMARK_IMAGE)..."
	docker push $(DOCKER_BENCHMARK_IMAGE)
	@echo "✅ Benchmark image pushed successfully"

# === Container Management ===
nuxeo-run:
	@if docker ps -a --format '{{.Names}}' | grep -wq nuxeo; then \
		echo "⚠️  Container 'nuxeo' already exists. Use 'make nuxeo-rm' to remove it first."; \
	else \
		echo "🚀 Starting nuxeo container from $(DOCKER_BASE_IMAGE)..."; \
		docker run -d --name nuxeo -p 8080:8080 $(DOCKER_BASE_IMAGE); \
		echo "✅ Nuxeo is running at http://localhost:8080"; \
	fi

nuxeo-stop:
	@echo "⏸️  Stopping nuxeo container..."
	@docker stop nuxeo || echo "ℹ️  No running container named 'nuxeo'"

nuxeo-start:
	@echo "▶️  Starting nuxeo container..."
	@docker start nuxeo || echo "ℹ️  No container named 'nuxeo' found"

nuxeo-rm:
	@echo "🗑️  Removing nuxeo container..."
	@docker rm nuxeo -f || echo "ℹ️  No container named 'nuxeo' to remove"

# === Docker Cleanup ===
clean-orphans:
	@echo "🧽 Removing dangling Docker images..."
	@docker image prune --force --filter "dangling=true" || echo "✅ No orphan images to clean"

# === Diagnostic ===
test:
	@echo "🔧 Docker Build Configuration:"
	@echo ""
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
	@echo "Docker Status:"
	@docker version --format '  Docker version: {{.Server.Version}}' 2>/dev/null || echo "  ❌ Docker not available"
	@docker info --format '  Registry: {{.IndexServerAddress}}' 2>/dev/null || true
	@docker info --format '  Username: {{.Username}}' 2>/dev/null | grep -q '.' && echo "  ✅ Logged in" || echo "  ⚠️  Not logged in"
