package com.senzing.calculator.scoring.risk;

import java.io.StringReader;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import com.senzing.listener.senzing.communication.ConsumerType;
import com.senzing.listener.senzing.communication.MessageConsumer;
import com.senzing.listener.senzing.communication.MessageConsumerFactory;
import com.senzing.listener.senzing.data.ConsumerCommandOptions;
import com.senzing.calculator.scoring.risk.service.RiskScoringService;

public class RiskScoringCalculator {

  public void run(String config) throws Exception {

    String consumerType = getConfigValue(config, ConsumerCommandOptions.CONSUMER_TYPE);
    if (consumerType == null || consumerType.isEmpty()) {
      consumerType = "rabbitmq";
    }

    RiskScoringService service = new RiskScoringService();
    service.init(config);

    MessageConsumer consumer = MessageConsumerFactory.generateMessageConsumer(ConsumerType.valueOf(consumerType), config);
    consumer.consume(service);

    while (service.isServiceUp()) {
      Thread.sleep(30000);
    }
    service.cleanUp();
  }

  private String getConfigValue(String config, String key) {
    JsonReader reader = Json.createReader(new StringReader(config));
    JsonObject jsonConfig = reader.readObject();
    return jsonConfig.getString(key, null);
  }
}
