package com.senzing.calculator.scoring.risk;

import org.json.JSONException;
import org.json.JSONObject;

import com.senzing.listener.senzing.communication.ConsumerType;
import com.senzing.listener.senzing.communication.MessageConsumer;
import com.senzing.listener.senzing.communication.MessageConsumerFactory;
import com.senzing.listener.senzing.data.ConsumerCommandOptions;
import com.senzing.listener.senzing.service.ListenerService;
import com.senzing.calculator.scoring.risk.service.RiskScoringService;

public class RiskScoringCalculator {

  public void run(String config) throws Exception {

    String consumerType = getConfigValue(config, ConsumerCommandOptions.CONSUMER_TYPE);
    if (consumerType == null || consumerType.isEmpty()) {
      consumerType = "rabbitmq";
    }

    ListenerService service = new RiskScoringService();
    service.init(config);

    MessageConsumer consumer = MessageConsumerFactory.generateMessageConsumer(ConsumerType.valueOf(consumerType), config);
    consumer.consume(service);
  }

  private String getConfigValue(String config, String key) throws JSONException {
    JSONObject jsonConfig = new JSONObject(config);
    return jsonConfig.optString(key);
  }
}
