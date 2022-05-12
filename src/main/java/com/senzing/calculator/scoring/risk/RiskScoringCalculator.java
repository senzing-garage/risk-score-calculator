package com.senzing.calculator.scoring.risk;

import java.io.StringReader;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import com.senzing.listener.communication.ConsumerType;
import com.senzing.listener.communication.MessageConsumer;
import com.senzing.listener.communication.MessageConsumerFactory;
import com.senzing.calculator.scoring.risk.data.CommandOptions;
import com.senzing.calculator.scoring.risk.service.RiskScoringService;

public class RiskScoringCalculator {

  public void run(String config) throws Exception {

    String consumerType = getConfigValue(config, CommandOptions.CONSUMER_TYPE);
    if (consumerType == null || consumerType.isEmpty()) {
      consumerType = "RABBIT_MQ";
    }

    RiskScoringService service = new RiskScoringService();
    service.init(config);

    MessageConsumer consumer = MessageConsumerFactory.generateMessageConsumer(ConsumerType.valueOf(consumerType), config);
    consumer.consume(service);

    while (service.isServiceUp()) {
      Thread.sleep(30000);
    }
    service.destroy();
  }

  private String getConfigValue(String config, String key) {
    JsonReader reader = Json.createReader(new StringReader(config));
    JsonObject jsonConfig = reader.readObject();
    return jsonConfig.getString(key, null);
  }
}
