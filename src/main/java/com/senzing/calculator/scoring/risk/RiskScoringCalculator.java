package com.senzing.calculator.scoring.risk;

import java.io.StringReader;

import javax.json.Json;
import javax.json.JsonObject;

import com.senzing.util.JsonUtilities;
import com.senzing.listener.communication.ConsumerType;
import com.senzing.listener.communication.MessageConsumer;
import com.senzing.listener.communication.MessageConsumerFactory;
import com.senzing.calculator.scoring.risk.data.CommandOptions;
import com.senzing.calculator.scoring.risk.service.RiskScoringService;

public class RiskScoringCalculator {

  public void run(String configText) throws Exception {

    JsonObject config = JsonUtilities.parseJsonObject(configText);
    String consumerType = JsonUtilities.getString(config, CommandOptions.CONSUMER_TYPE);
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
}
