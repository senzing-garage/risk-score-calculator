ARG BASE_IMAGE=senzing/senzingapi-runtime:3.6.0
ARG BASE_BUILDER_IMAGE=senzing/base-image-debian:1.0.19

# -----------------------------------------------------------------------------
# Stage: builder
# -----------------------------------------------------------------------------

FROM ${BASE_BUILDER_IMAGE} as builder

ENV REFRESHED_AT=2023-06-29

LABEL Name="senzing/risk-scoring-calculator-builder" \
      Maintainer="support@senzing.com" \
      Version="2.0.0"

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

ENV REFRESHED_AT=2023-06-29

LABEL Name="senzing/risk-scoring-calculator" \
      Maintainer="support@senzing.com" \
      Version="2.0.0"

HEALTHCHECK CMD ["/app/healthcheck.sh"]

# Run as "root" for system installation.

USER root

# Install packages via apt.

RUN apt update \
 && apt -y install \
      software-properties-common \
 && rm -rf /var/lib/apt/lists/*

# Install Java-11.

RUN mkdir -p /etc/apt/keyrings \
 && wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public > /etc/apt/keyrings/adoptium.asc

RUN echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" >> /etc/apt/sources.list

RUN apt update \
 && apt install -y temurin-11-jdk \
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
