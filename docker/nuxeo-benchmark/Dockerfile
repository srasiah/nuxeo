# Nuxeo Benchmark image
#
# It includes a Nuxeo server with the needed packages for the Benchmark tests

ARG BASE_IMAGE

FROM $BASE_IMAGE

ARG BUILD_TAG
ARG SCM_REF
ARG VERSION

LABEL org.nuxeo.benchmark.build-tag=$BUILD_TAG
LABEL org.nuxeo.benchmark.scm-ref=$SCM_REF
LABEL org.nuxeo.benchmark.version=$VERSION
# Override parent ones
LABEL org.label-schema.name="Nuxeo Benchmark Server"
LABEL org.opencontainers.image.title="Nuxeo Benchmark Server"


# Prepare local package directory
RUN mkdir -p /tmp/local-packages/

# Install offline packages from both sources via bind mounts, so the zips never
# end up baked into an image layer. ./marketplace holds optional local overrides
# (may be empty); ./target/packages is populated by `mvn dependency:copy-dependencies`.
RUN --mount=type=bind,source=./marketplace,target=/tmp/marketplace \
    --mount=type=bind,source=./target/packages,target=/tmp/target-packages \
    cp /tmp/marketplace/*.zip /tmp/local-packages/ 2>/dev/null; \
    cp /tmp/target-packages/*.zip /tmp/local-packages/ \
 && /install-packages.sh --offline /tmp/local-packages/*.zip \
 && rm -rf /tmp/local-packages
