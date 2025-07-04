# ===== Configuration (defaults) =====
DOCKER_REGISTRY              ?= docker.platform.dev.nuxeo.com
DOCKER_BASE_IMAGE_NAME       ?= $(DOCKER_REGISTRY)/nuxeo/nuxeo
DOCKER_BENCHMARK_IMAGE_NAME  ?= $(DOCKER_REGISTRY)/nuxeo-benchmark
DOCKER_IMAGE_TAG             ?= 2023.x
TARGETPLATFORM               ?= linux/amd64
MAVEN_PROFILE                ?= "-Pdistrib,docker,amd64"
REVISION                     ?= 2023.29-SNAPSHOT
RELEASE_VERSION              ?= 2023.29
NUXEO_PLATFORM_VERSION       ?= 2023.29-SNAPSHOT
ORG_NAME                     ?= your-org-name
MAVEN_GROUP_ID               ?= org.nuxeo
PACKAGE_NAME                 ?= org.nuxeo.web.ui.nuxeo-web-ui-parent
DOCKER_BASE_IMAGE            := $(DOCKER_BASE_IMAGE_NAME):$(DOCKER_IMAGE_TAG)
DOCKER_BENCHMARK_IMAGE       := $(DOCKER_BENCHMARK_IMAGE_NAME):$(DOCKER_IMAGE_TAG)
MAVEN_DEPLOY                 := -DaltDeploymentRepository=github::default::https://maven.pkg.github.com/OWNER/REPO

# ===== Load .env if present =====
ifneq (,$(wildcard .env))
include .env
export
endif

# ===== Maven Options =====
MAVEN_OPTS    := -Xmx4g -Xms2g -XX:+TieredCompilation -XX:TieredStopAtLevel=1
MAVEN_COMMON  := -B -DskipTests -Dnuxeo.skip.enforcer=true -T6

# ===== Targets =====
.PHONY: all build-base-image build-benchmark-image push push-base push-benchmark \
        clean-all-unused preview-orphans clean-orphans \
        nuxeo-run nuxeo-stop nuxeo-start nuxeo-rm \
        test deploy-packages \
        set_version reset_version check-env

# === Build Images ===
all: build-base-image build-benchmark-image

build-packages: _build-packages
	@echo "✅ Packages build successful..."

build-base-image: _build-base-image
	@echo "✅ Base build successful, cleaning orphan images..."
	@-$(MAKE) clean-orphans
	@-$(MAKE) push-base

build-benchmark-image: _build-benchmark-image
	@echo "✅ Benchmark build successful, cleaning orphan images..."
	@-$(MAKE) clean-orphans
	@-$(MAKE) push-benchmark

_build-packages:
	@echo "🐳 Building packages..."
	@export MAVEN_OPTS='$(MAVEN_OPTS)' && \
	mvn install -Pdistrib $(MAVEN_COMMON) -Drevision=$(REVISION)

_build-base-image:
	@echo "🐳 Building Docker base image: $(DOCKER_BASE_IMAGE)"
	@export MAVEN_OPTS='$(MAVEN_OPTS)' && \
	mvn install ${MAVEN_PROFILE} -pl docker/nuxeo -am $(MAVEN_COMMON) \
	    -Drevision=$(REVISION) \
		-Ddocker.base.image=$(DOCKER_BASE_IMAGE) \
		-Ddocker.platforms=$(TARGETPLATFORM)

_build-benchmark-image:
	@echo "🐳 Building Docker benchmark image: $(DOCKER_BENCHMARK_IMAGE)"
	@export MAVEN_OPTS='$(MAVEN_OPTS)' && \
	mvn clean install ${MAVEN_PROFILE} $(MAVEN_COMMON) \
	 	-Drevision=$(REVISION) \
		-Ddocker.base.image=$(DOCKER_BASE_IMAGE) \
		-Ddocker.benchmark.image=$(DOCKER_BENCHMARK_IMAGE) \
		-Ddocker.platforms=$(TARGETPLATFORM)

# === Deploy ===
deploy-packages:
	@echo "📦 Deploying Maven artifact to GitHub Packages..."
	@export MAVEN_OPTS='$(MAVEN_OPTS)' && \
	mvn deploy -Pdistrib \
	$(MAVEN_COMMON) \
	$(MAVEN_DEPLOY)

# === Version Management ===
set_version:
	@echo "📝 Setting new version: $(RELEASE_VERSION)"
	mvn versions:set -DnewVersion=$(RELEASE_VERSION)

reset_version:
	@echo "🔁 Reverting to previous version..."
	mvn versions:revert

# === Docker Push Targets ===
push: push-base push-benchmark

push-base:
	@echo "📤 Pushing base image $(DOCKER_BASE_IMAGE)..."
	docker info | grep -q 'Username' || (echo "❌ Not logged in!" && exit 1)
	docker push $(DOCKER_BASE_IMAGE)

push-benchmark:
	@echo "📤 Pushing benchmark image $(DOCKER_BENCHMARK_IMAGE)..."
	docker info | grep -q 'Username' || (echo "❌ Not logged in!" && exit 1)
	docker push $(DOCKER_BENCHMARK_IMAGE)

# === Container Management ===
nuxeo-run:
	@if docker ps -a --format '{{.Names}}' | grep -wq nuxeo; then \
		echo "⚠️  Container 'nuxeo' already exists. Use 'make nuxeo-rm'."; \
	else \
		docker run -d --name nuxeo -p 8080:8080 $(DOCKER_BENCHMARK_IMAGE); \
	fi

nuxeo-stop:
	@docker stop nuxeo || echo "ℹ️  No running container named 'nuxeo'"

nuxeo-start:
	@docker start nuxeo || echo "ℹ️  No container named 'nuxeo' found"

nuxeo-rm:
	@docker rm nuxeo -f || echo "ℹ️  No container named 'nuxeo' to remove"

# === Docker Cleanup ===
clean-all-unused:
	@echo "🧹 Removing all unused Docker images..."
	@docker image prune --all --force

preview-orphans:
	@echo "🔍 Listing orphan images..."
	@docker images --filter "dangling=true" --quiet || echo "No orphan images found"

clean-orphans: preview-orphans
	@echo "🧽 Removing orphan Docker images..."
	@docker image prune --force --filter "dangling=true" || echo "✅ Cleanup completed"

# === Diagnostic ===
check-env:
	@[[ -n "$(GITHUB_TOKEN)" ]] || (echo "❌ GITHUB_TOKEN is not set" && exit 1)

test:
	@echo "🔧 Test Environment:"
	@printf '%-25s %s\n' "REVISION:" "$(REVISION)"
	@printf '%-25s %s\n' "RELEASE_VERSION:" "$(RELEASE_VERSION)"
	@printf '%-25s %s\n' "MAVEN_PROFILE:" "$(MAVEN_PROFILE)"
	@printf '%-25s %s\n' "MAVEN_DEPLOY:" "$(MAVEN_DEPLOY)"
	@printf '%-25s %s\n' "MAVEN_OPTS:" "$(MAVEN_OPTS)"
	@printf '%-25s %s\n' "MAVEN_COMMON:" "$(MAVEN_COMMON)"
	@printf '%-25s %s\n' "GITHUB_USER:" "$${GITHUB_USER:-<not set>}"
	@printf '%-25s %s\n' "GITHUB_TOKEN:" "$$( [ -n "$$GITHUB_TOKEN" ] && echo '***' || echo '<not set>' )"