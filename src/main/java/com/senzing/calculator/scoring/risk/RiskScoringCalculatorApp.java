package com.senzing.calculator.scoring.risk;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.json.JSONException;
import org.json.JSONObject;

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

  private static String processArguments(String[] args) throws ParseException, JSONException {
    Options options = new Options();

    // Add options.
    // Options for g2.
    options.addOption(CommandOptions.INI_FILE, true, "Path to the G2 ini file");
    // Options for consumer.
    options.addOption(ConsumerCommandOptions.MQ_HOST, true, "Host for RabbitMQ");
    options.addOption(ConsumerCommandOptions.MQ_USER, true, "User name for RabbitMQ");
    options.addOption(ConsumerCommandOptions.MQ_PASSWORD, true, "Password for RabbitMQ");
    options.addOption(ConsumerCommandOptions.MQ_QUEUE, true, "Queue name for the receiving queue");
    options.addOption(ConsumerCommandOptions.CONSUMER_TYPE, true, "Type of the consumer used e.g. rabbitmq");

    CommandLineParser parser = new DefaultParser();
    CommandLine commandLine = parser.parse(options, args);

    JSONObject jsonRoot = new JSONObject();
    addCommandArgumentValue(jsonRoot, commandLine, CommandOptions.INI_FILE);
    addCommandArgumentValue(jsonRoot, commandLine, ConsumerCommandOptions.MQ_HOST);
    addCommandArgumentValue(jsonRoot, commandLine, ConsumerCommandOptions.MQ_USER);
    addCommandArgumentValue(jsonRoot, commandLine, ConsumerCommandOptions.MQ_PASSWORD);
    addCommandArgumentValue(jsonRoot, commandLine, ConsumerCommandOptions.MQ_QUEUE);
    addCommandArgumentValue(jsonRoot, commandLine, ConsumerCommandOptions.CONSUMER_TYPE);

    return jsonRoot.toString();
  }

  private static void addCommandArgumentValue(JSONObject jsonRoot, CommandLine commandLine, String key) throws JSONException {
    if (commandLine.hasOption(key)) {
      jsonRoot.put(key, commandLine.getOptionValue(key));
    }
  }
}
