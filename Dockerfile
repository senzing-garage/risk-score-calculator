ARG BASE_IMAGE=senzing/senzing-base:1.6.3
ARG BASE_BUILDER_IMAGE=senzing/base-image-debian:1.0.6

# -----------------------------------------------------------------------------
# Stage: builder
# -----------------------------------------------------------------------------

FROM ${BASE_BUILDER_IMAGE} as builder

ENV REFRESHED_AT=2021-12-07

LABEL Name="senzing/risk-scoring-calculator-builder" \
      Maintainer="support@senzing.com" \
      Version="1.0.0"

# Set environment variables.

ENV SENZING_ROOT=/opt/senzing
ENV SENZING_G2_DIR=${SENZING_ROOT}/g2
ENV PYTHONPATH=${SENZING_ROOT}/g2/python
ENV LD_LIBRARY_PATH=${SENZING_ROOT}/g2/lib:${SENZING_ROOT}/g2/lib/debian

# Build "senzing-listener.jar"

COPY senzing-listener /senzing-listener
WORKDIR /senzing-listener

RUN export SENZING_LISTENER_VERSION=$(mvn "help:evaluate" -Dexpression=project.version -q -DforceStdout) \
 && make package \
 && cp /senzing-api-server/target/senzing-api-server-${SENZING_LISTENER_VERSION}.jar "/senzing-listener.jar" \
 && mvn install:install-file \
      -Dfile=/senzing-listener.jar  \
      -DgroupId=com.senzing \
      -DartifactId=senzing-listener \
      -Dversion=${SENZING_LISTENER_VERSION} \
      -Dpackaging=jar

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

ENV REFRESHED_AT=2021-12-07

LABEL Name="senzing/risk-scoring-calculator" \
      Maintainer="support@senzing.com" \
      Version="1.1.0"

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
