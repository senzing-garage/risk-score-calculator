ARG BASE_IMAGE=senzing/senzing-base:1.6.3
ARG BASE_BUILDER_IMAGE=senzing/base-image-debian:1.0.6

# -----------------------------------------------------------------------------
# Stage: builder
# -----------------------------------------------------------------------------

FROM ${BASE_BUILDER_IMAGE} as builder

# Set Shell to use for RUN commands in builder step.

ENV REFRESHED_AT=2021-12-07

LABEL Name="senzing/risk-scoring-calculator-builder" \
      Maintainer="support@senzing.com" \
      Version="1.0.0"

# Build arguments.

ARG SENZING_G2_JAR_RELATIVE_PATHNAME=unknown
ARG SENZING_G2_JAR_VERSION=unknown

# Set environment variables.

ENV SENZING_ROOT=/opt/senzing
ENV SENZING_G2_DIR=${SENZING_ROOT}/g2
ENV PYTHONPATH=${SENZING_ROOT}/g2/python
ENV LD_LIBRARY_PATH=${SENZING_ROOT}/g2/lib:${SENZING_ROOT}/g2/lib/debian

# Copy Repo files to Builder step.

COPY . /risk-scoring-calculator

# Run the "make" command to install dependencies
WORKDIR /risk-scoring-calculator/senzing-listener
RUN make \
     SENZING_G2_JAR_PATHNAME=/risk-scoring-calculator/${SENZING_G2_JAR_RELATIVE_PATHNAME} \
     SENZING_G2_JAR_VERSION=${SENZING_G2_JAR_VERSION} \
     install-dependency

# Run the "make" command to create the artifacts.

WORKDIR /risk-scoring-calculator

RUN export RISK_SCORING_CALCULATOR_JAR_VERSION=$(mvn "help:evaluate" -Dexpression=project.version -q -DforceStdout) \
 && make \
     SENZING_G2_JAR_PATHNAME=/risk-scoring-calculator/${SENZING_G2_JAR_RELATIVE_PATHNAME} \
     SENZING_G2_JAR_VERSION=${SENZING_G2_JAR_VERSION} \
     package \
 && cp /risk-scoring-calculator/target/risk-scoring-calculator-${RISK_SCORING_CALCULATOR_JAR_VERSION}.jar "/risk-scoring-calculator.jar" \
 && cp -r /risk-scoring-calculator/target/libs "/libs"

# -----------------------------------------------------------------------------
# Stage: Final
# -----------------------------------------------------------------------------

FROM ${BASE_IMAGE}

ENV REFRESHED_AT=2021-12-07

LABEL Name="senzing/risk-scoring-calculator" \
      Maintainer="support@senzing.com" \
      Version="1.0.2"

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
