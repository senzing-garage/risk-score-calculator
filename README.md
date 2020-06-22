# risk-scoring-calculator

## Overview

This application examines G2 entities and scores them based the quality of its data and the sharing and interaction of the data with other entities.

These are the rules used for scoring:

1. Red Collision (entity has any of the following):
    1. Entity has a Red status for Data Quality
    1. Manual flag for Red
    1. Forced Un-merge (This is not implemented yet)

1. Red Data Quality (entity has any of the following):
    1. Is Ambiguous or has Ambiguous relationship
    1. Those having multiple F1E or F1ES of the same type
    1. Those with a F1E or F1ES that is shared with other entities
    1. Same entity with multiple DOBs

1. Green Collision (entity has all of the following):
    1. Entity has a Green status for Data Quality
    1. Entity has no shared F1
    1. Entity has no possible matches

1. Green Data Quality (entity has all of the following):
    1. Entity has at least 1 trusted data source record (configurable which source)
    1. Entity has none or one SSN
    1. Entity has one and only one DOB
    1. Entity has address

It receives data via RabbitMQ, which in turn can be fed by a streaming service.

## Setup and building

### Dependencies

To build the Risk Score Calculator you will need Apache Maven (recommend version 3.6.1 or later)
as well as OpenJDK version 11.0.x (recommend version 11.0.6+10 or later).

You will also need the Senzing `g2.jar` file installed in your Maven repository.
The Senzing REST API Server requires version 1.13.x or later of the Senzing API and Senzing App.
In order to install `g2.jar` you must:

1. Locate your
   [SENZING_G2_DIR](https://github.com/Senzing/knowledge-base/blob/master/lists/environment-variables.md#senzing_g2_dir)
   directory.
   The default locations are:
    1. [Linux](https://github.com/Senzing/knowledge-base/blob/master/HOWTO/install-senzing-api.md#centos): `/opt/senzing/g2`
    1. Windows MSI Installer: `C:\Program Files\Senzing\`

1. Determine your `SENZING_G2_JAR_VERSION` version number:
    1. Locate your `g2BuildVersion.json` file:
        1. Linux: `${SENZING_G2_DIR}/g2BuildVersion.json`
        1. Windows: `${SENZING_G2_DIR}\data\g2BuildVersion.json`
    1. Find the value for the `"VERSION"` property in the JSON contents.
       Example:

        ```console
        {
            "PLATFORM": "Linux",
            "VERSION": "1.14.20060",
            "API_VERSION": "1.14.3",
            "BUILD_NUMBER": "2020_02_29__02_00"
        }
        ```

1. Install the `g2.jar` file in your local Maven repository, replacing the
   `${SENZING_G2_DIR}` and `${SENZING_G2_JAR_VERSION}` variables as determined above:

    1. Linux:

        ```console
        export SENZING_G2_DIR=/opt/senzing/g2
        export SENZING_G2_JAR_VERSION=1.14.3

        mvn install:install-file \
            -Dfile=${SENZING_G2_DIR}/lib/g2.jar \
            -DgroupId=com.senzing \
            -DartifactId=g2 \
            -Dversion=${SENZING_G2_JAR_VERSION} \
            -Dpackaging=jar
        ```

    1. Windows:

        ```console
        set SENZING_G2_DIR="C:\Program Files\Senzing\g2"
        set SENZING_G2_JAR_VERSION=1.14.3

        mvn install:install-file \
            -Dfile="%SENZING_G2_DIR%\lib\g2.jar" \
            -DgroupId=com.senzing \
            -DartifactId=g2 \
            -Dversion="%SENZING_G2_JAR_VERSION%" \
            -Dpackaging=jar
        ```

The Risk Scoring Calculator is built on the [Senzing Listener](https://github.com/Senzing/senzing-listener).  You will need to build it and install into local Maven repository before building this application.  Follow the directions for building the [Senzing Listener](https://github.com/Senzing/senzing-listener).

### Building

To build simply execute:

```console
mvn install
```

## Running

Before running the Risk Scoring Calculator you need to set up the environment for G2

### Setup

1. Linux

    ```console
    export SENZING_G2_DIR=/opt/senzing/g2
    export LD_LIBRARY_PATH=${SENZING_G2_DIR}/lib:${SENZING_G2_DIR}/lib/debian:$LD_LIBRARY_PATH
    ```

1. Windows

    ```console
    set SENZING_G2_DIR="C:\Program Files\Senzing\g2"
    set Path=%SENZING_G2_DIR%\lib;%Path%
    ```

### Parameters

A few pieces of information are needed for running the application.  They will become parameters on the command line.

1. The host name for the RabbitMQ server.  The command line paramter for this is -mqHost.

1. The name of the RabbitMQ used for receiving messages.  The paramter is -mqQueue.

1. The user name for RabbitMQ.  This might be ignored, based on RabbitMQ security settings.  The parameter is -mqUser.

1. The password for RabbitMQ.  This might be ignored, based on RabbitMQ security settings.  The parameter is -mqPassword.

1. Path of the G2 ini file.  The parameter is -iniFile.

### Command

The command for running the application is

```console
java -jar target/risk-scoring-calculator-0.0.1-SNAPSHOT.jar -iniFile <ini file path> -mqQueue <queue name> -mqHost <RabbitMQ host> -mqUser <RabbitMQ user name> -mqPassword <RabbitMQ password>
```
