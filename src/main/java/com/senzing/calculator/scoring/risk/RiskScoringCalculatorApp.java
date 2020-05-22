package com.senzing.calculator.scoring.risk;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import com.senzing.calculator.scoring.risk.data.CommandOptions;
import com.senzing.listener.senzing.data.ConsumerCommandOptions;

public class RiskScoringCalculatorApp {
  public static void main(String[] args) {
    try {
      String config = processArguments(args);
      RiskScoringCalculator riskScoringCalculator = new RiskScoringCalculator();
      riskScoringCalculator.run(config);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static String processArguments(String[] args) throws ParseException {
    Options options = new Options();

    // Add options.
    // Options for risk scoring servive.
    options.addOption(CommandOptions.INI_FILE, true, "Path to the G2 ini file");
    options.addOption(CommandOptions.JDBC_CONNECTION, true, "Connection string for the G2 database");
    // Options for consumer.
    options.addOption(ConsumerCommandOptions.MQ_HOST, true, "Host for RabbitMQ");
    options.addOption(ConsumerCommandOptions.MQ_USER, true, "User name for RabbitMQ");
    options.addOption(ConsumerCommandOptions.MQ_PASSWORD, true, "Password for RabbitMQ");
    options.addOption(ConsumerCommandOptions.MQ_QUEUE, true, "Queue name for the receiving queue");
    options.addOption(ConsumerCommandOptions.CONSUMER_TYPE, true, "Type of the consumer used e.g. rabbitmq");

    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine = parser.parse(options, args);

    JsonObjectBuilder jsonRoot = Json.createObjectBuilder();
    addCommandArgumentValue(jsonRoot, commandLine, CommandOptions.INI_FILE);
    addCommandArgumentValue(jsonRoot, commandLine, CommandOptions.JDBC_CONNECTION);
    addCommandArgumentValue(jsonRoot, commandLine, ConsumerCommandOptions.MQ_HOST);
    addCommandArgumentValue(jsonRoot, commandLine, ConsumerCommandOptions.MQ_USER);
    addCommandArgumentValue(jsonRoot, commandLine, ConsumerCommandOptions.MQ_PASSWORD);
    addCommandArgumentValue(jsonRoot, commandLine, ConsumerCommandOptions.MQ_QUEUE);
    addCommandArgumentValue(jsonRoot, commandLine, ConsumerCommandOptions.CONSUMER_TYPE);

    return jsonRoot.build().toString();
  }

  private static void addCommandArgumentValue(JsonObjectBuilder jsonRoot, CommandLine commandLine, String key) {
    if (commandLine.hasOption(key)) {
      jsonRoot.add(key, commandLine.getOptionValue(key));
    }
  }
}
