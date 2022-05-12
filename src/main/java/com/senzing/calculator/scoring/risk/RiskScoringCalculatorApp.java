package com.senzing.calculator.scoring.risk;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import com.senzing.calculator.scoring.risk.config.AppConfiguration;
import com.senzing.calculator.scoring.risk.config.ConfigKeys;
import com.senzing.calculator.scoring.risk.data.CommandOptions;
import com.senzing.listener.communication.ConsumerType;
import com.senzing.listener.communication.rabbitmq.RabbitMQConsumer;

public class RiskScoringCalculatorApp {

  private static final String RABBITMQ_CONSUMER_TYPE = "RABBIT_MQ";

  private static Map<String, Object> configValues;

  public static void main(String[] args) {
    configValues = new HashMap<>();
    configValues.put(CommandOptions.CONSUMER_TYPE, RABBITMQ_CONSUMER_TYPE);
    try {
      processConfiguration();
      processArguments(args);

      validateCommandLineParams();

      String config = buildConfigJson();
      RiskScoringCalculator riskScoringCalculator = new RiskScoringCalculator();
      riskScoringCalculator.run(config);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void processConfiguration() {
    try {
      AppConfiguration config = new AppConfiguration();
      configValues.put(CommandOptions.CONSUMER_TYPE, RABBITMQ_CONSUMER_TYPE);
      configValues.put(CommandOptions.INI_FILE, config.getConfigValue(ConfigKeys.G2_INI_FILE));
      configValues.put(CommandOptions.JDBC_CONNECTION, config.getConfigValue(ConfigKeys.JDBC_CONNECTION));
      configValues.put(CommandOptions.TRUSTED_SOURCES, config.getConfigValue(ConfigKeys.TRUSTED_SOURCES));
      configValues.put(CommandOptions.QUERY_RISK_CRITERIA, config.getConfigValue(ConfigKeys.QUERY_RISK_CRITERIA));
      configValues.put(RabbitMQConsumer.MQ_HOST, config.getConfigValue(ConfigKeys.RABBITMQ_HOST));
      configValues.put(RabbitMQConsumer.MQ_QUEUE, config.getConfigValue(ConfigKeys.RABBITMQ_NAME));
      configValues.put(RabbitMQConsumer.MQ_USER, config.getConfigValue(ConfigKeys.RABBITMQ_USER_NAME));
      configValues.put(RabbitMQConsumer.MQ_PASSWORD, config.getConfigValue(ConfigKeys.RABBITMQ_PASSWORD));
      // This is a future enhancement and enabled when other consumers have been added.
      //configValues.put(ConsumerCommandOptions.CONSUMER_TYPE, config.getConfigValue(ConfigKeys.CONSUMER_TYPE));
    } catch (IOException e) {
      System.out.println("Configuration file not found. Expecting command line arguments.");
    }
  }

  private static void processArguments(String[] args) throws ParseException {
    Options options = new Options();

    // Add options.
    // Options for risk scoring servive.
    options.addOption(CommandOptions.INI_FILE, true, "Path to the G2 ini file");
    options.addOption(CommandOptions.JDBC_CONNECTION, true, "Connection string for the G2 database");
    // Scoring options
    options.addOption(CommandOptions.TRUSTED_SOURCES, true, "List of trusted sources used for scoring");
    options.addOption(CommandOptions.QUERY_RISK_CRITERIA, true, "List of query risk scoring criteria");
    // Options for consumer.
    options.addOption(RabbitMQConsumer.MQ_HOST, true, "Host for RabbitMQ");
    options.addOption(RabbitMQConsumer.MQ_USER, true, "User name for RabbitMQ");
    options.addOption(RabbitMQConsumer.MQ_PASSWORD, true, "Password for RabbitMQ");
    options.addOption(RabbitMQConsumer.MQ_QUEUE, true, "Queue name for the receiving queue");
    options.addOption(CommandOptions.CONSUMER_TYPE, true, "Type of the consumer used e.g. rabbitmq");

    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine = parser.parse(options, args);

    addCommandLineValue(commandLine, CommandOptions.INI_FILE);
    addCommandLineValue(commandLine, CommandOptions.JDBC_CONNECTION);
    addCommandLineValue(commandLine, CommandOptions.TRUSTED_SOURCES);
    addCommandLineValue(commandLine, CommandOptions.QUERY_RISK_CRITERIA);
    addCommandLineValue(commandLine, RabbitMQConsumer.MQ_HOST);
    addCommandLineValue(commandLine, RabbitMQConsumer.MQ_USER);
    addCommandLineValue(commandLine, RabbitMQConsumer.MQ_PASSWORD);
    addCommandLineValue(commandLine, RabbitMQConsumer.MQ_QUEUE);
    addCommandLineValue(commandLine, CommandOptions.CONSUMER_TYPE);
  }

  private static void addCommandLineValue(CommandLine commandLine, String key) {
    String cmdLineValue = commandLine.getOptionValue(key);
    if (cmdLineValue != null && !cmdLineValue.isEmpty()) {configValues.put(key, cmdLineValue);}
  }

  private static void validateCommandLineParams() {
    List<String> unsetParameters = new ArrayList<>();
    checkParameter(unsetParameters, CommandOptions.INI_FILE);
    checkParameter(unsetParameters, CommandOptions.JDBC_CONNECTION);
    checkParameter(unsetParameters, RabbitMQConsumer.MQ_HOST);
    checkParameter(unsetParameters, RabbitMQConsumer.MQ_QUEUE);

    if (!unsetParameters.isEmpty()) {
      System.out.println("No configuration found for parameters: " + String.join(", ", unsetParameters));
      helpMessage();
      System.out.println("Failed to start!!!");
      System.exit(-1);
    }
  }

  private static void checkParameter(List<String> parameters, String key) {
    Object value = configValues.get(key);
    if (value == null || value.toString().isEmpty()) {
      parameters.add(key);
    }
  }

  private static String buildConfigJson() {
    JsonObjectBuilder jsonRoot = Json.createObjectBuilder();
    for (String key : configValues.keySet()) {
      Object value = configValues.get(key);
      if (value != null) {
        jsonRoot.add(key, value.toString());
      }
    }
    return jsonRoot.build().toString();
  }

  private static void helpMessage() {
    System.out.println("Set the configuration in the risk-scoring-calculator.properties or add command line parameters.");
    System.out.println("Command line usage: java -jar risk-scoring-calculator.jar -jdbcConnection jdbc:<connection string to db> \\");
    System.out.println("                                                          -iniFile <path to ini file> \\");
    System.out.println("                                                          -mqQueue <name of the queue read from> \\");
    System.out.println("                                                          -mqHost <host name for queue server> \\");
    System.out.println("                                                          [-mqUser <queue server user name>] \\");
    System.out.println("                                                          [-mqPassword <queue server password>]");
    System.out.println("                                                          [-trustedSources <comma separated list of trusted sources used for scoring>]");
    System.out.println("                                                          [-queryRiskCriteria <list of query risk criteria. See below at *>]");
    System.out.println("");
    System.out.println("* Example of queryRiskCriteria: +NAME+DOB:R;+NAME+ADDRESS:Y;+NAME+PHONE:Y;+NAME+SSN:R");
    System.out.println("");
  }
}
