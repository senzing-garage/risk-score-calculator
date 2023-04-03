ARG BASE_IMAGE=senzing/senzingapi-runtime:3.5.0
ARG BASE_BUILDER_IMAGE=debian:11.6-slim@sha256:7acda01e55b086181a6fa596941503648e423091ca563258e2c1657d140355b1

# -----------------------------------------------------------------------------
# Stage: builder
# -----------------------------------------------------------------------------

FROM ${BASE_BUILDER_IMAGE} as builder

ENV REFRESHED_AT=2023-04-03

LABEL Name="senzing/risk-scoring-calculator-builder" \
      Maintainer="support@senzing.com" \
      Version="1.0.6"

# Set environment variables.

ENV SENZING_ROOT=/opt/senzing
ENV SENZING_G2_DIR=${SENZING_ROOT}/g2
ENV PYTHONPATH=${SENZING_ROOT}/g2/python
ENV LD_LIBRARY_PATH=${SENZING_ROOT}/g2/lib:${SENZING_ROOT}/g2/lib/debian

# Build "risk-scoring-calculator.jar"

COPY . /risk-scoring-calculator
WORKDIR /risk-scoring-calculator

RUN export RISK_SCORING_CALCULATOR_VERSION=$(mvn "help:evaluate" -Dexpression=project.version -q -DforceStdout) \
 && make package \
 && cp /risk-scoring-calculator/target/risk-scoring-calculator-${RISK_SCORING_CALCULATOR_VERSION}.jar "/risk-scoring-calculator.jar" \
 && cp -r /risk-scoring-calculator/target/libs "/libs"

# -----------------------------------------------------------------------------
# Stage: Final
# -----------------------------------------------------------------------------

FROM ${BASE_IMAGE}

ENV REFRESHED_AT=2023-04-03

LABEL Name="senzing/risk-scoring-calculator" \
      Maintainer="support@senzing.com" \
      Version="1.0.6"

HEALTHCHECK CMD ["/app/healthcheck.sh"]

# Run as "root" for system installation.

USER root

# Install packages via apt.

RUN apt update \
 && apt -y install \
      software-properties-common \
 && rm -rf /var/lib/apt/lists/*

# Install Java-11.

RUN wget -qO - https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public | apt-key add - \
 && add-apt-repository --yes https://adoptopenjdk.jfrog.io/adoptopenjdk/deb/ \
 && apt update \
 && apt install -y adoptopenjdk-11-hotspot \
 && rm -rf /var/lib/apt/lists/*

# Copy files from repository.

COPY ./rootfs /

# Service exposed on port 8080.

EXPOSE 8080

# Copy files from builder step.

COPY --from=builder "/risk-scoring-calculator.jar" "/app/risk-scoring-calculator.jar"
COPY --from=builder "/libs" "/app/libs"

# Make non-root container.

USER 1001

# Runtime execution.

WORKDIR /app
ENTRYPOINT ["java", "-jar", "risk-scoring-calculator.jar"]
