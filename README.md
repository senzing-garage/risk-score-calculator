# risk-scoring-calculator

## Overview

This application examines G2 entities and scores them based the quality of its data and the sharing and interaction of the data with other entities.

These are the rules used for scoring (Data quality refers to data belonging to the entity, Collision refers to data related to other entities):

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

Data Quality that is neither Red nor Green is flagged as Yellow.  Same goes for Collisions.

It receives data via RabbitMQ, which in turn can be fed by a streaming service.

Update July 1, 2020:
I added query risk scoring. It uses criteria for match keys to give the score.
The score is configured with a string of format "+NAME+DOB:R;+NAME+ADDRESS:Y;+NAME+PHONE:Y;+NAME+SSN:R".
The relationships are scored according to this config, e.g. +NAME+DOB on a relationship would give Red score.

## Setup and building

### Dependencies

To build the Risk Score Calculator you will need Apache Maven (recommend version 3.6.1 or later)
as well as OpenJDK version 11.0.x (recommend version 11.0.6+10 or later).

To run this application you will need a RabbitMQ installation or an access to a RabbitMQ server.  More information can be found here https://www.rabbitmq.com/download.html

You will also need an installation of Senzing and a Senzing project.  Instructions can be found here https://docs.senzing.com/quickstart/

### Building

To build:

```console
git clone git@github.com:Senzing/risk-score-calculator.git
cd risk-score-calculator
mvn install
```

## Running

Before running the Risk Scoring Calculator you need to set up the environment for G2

### Setup

Set up the environment:
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

The risk scorer requires a database table that doesn't exist in standard installations of G2.  SQL scripts for creating the table can be found in `src/main/resources` folder.
There are 2 scripts: create-res_risk_score-mysql.v2.sql and create-res_risk_score-sqlite.v2.sql.  As the names indicates, one is intended for MySQL and the other SQLite.  The scripts have not been tested on other database types so if any other is needed the scripts might need modification.
The table could be created in the G2 database but another database could be used.

### Parameters

A few pieces of information are needed for running the application.  They will become parameters on the command line.

1. The host name for the RabbitMQ server.  The command line paramter for this is -mqHost.

1. The name of the RabbitMQ used for receiving messages.  The paramter is -mqQueue.

1. The user name for RabbitMQ.  This might be ignored, based on RabbitMQ security settings.  The parameter is -mqUser.

1. The password for RabbitMQ.  This might be ignored, based on RabbitMQ security settings.  The parameter is -mqPassword.

1. Path of the G2 ini file.  The parameter is -iniFile.

1. Connection string for database holding scoring data.  The parameter is -jdbcConnection.

### Command

The command for running the application is

```console
java -jar target/risk-scoring-calculator-0.0.1-SNAPSHOT.jar -iniFile <ini file path> -mqQueue <queue name> -mqHost <RabbitMQ host> -mqUser <RabbitMQ user name> -mqPassword <RabbitMQ password>
```
