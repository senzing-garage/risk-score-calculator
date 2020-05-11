package com.senzing.calculator.scoring.risk.service.g2;

import java.io.IOException;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.senzing.g2.engine.G2Diagnostic;
import com.senzing.g2.engine.G2DiagnosticJNI;
import com.senzing.listener.senzing.service.exception.ServiceExecutionException;
import com.senzing.listener.senzing.service.exception.ServiceSetupException;
import com.senzing.listener.senzing.service.g2.G2Service;
import com.senzing.listener.senzing.service.g2.G2ServiceDefinitions;

public class G2ServiceExt extends G2Service {

  protected G2Diagnostic g2Diagnostic;
  static final String diagnosticModuleName = "G2DiagnosticJNI";
  
  public G2ServiceExt() {
    super();
  }

  @Override
  public void init(String iniFile) throws ServiceSetupException {
    super.init(iniFile);
    boolean verboseLogging = false;

    String configData = null;
    try {
      configData = getG2IniDataAsJson(iniFile);
    } catch (IOException | JSONException e) {
      throw new ServiceSetupException(e);
    }
    g2Diagnostic = new G2DiagnosticJNI();
    int result = g2Diagnostic.initV2(diagnosticModuleName, configData, verboseLogging);
    if (result != G2ServiceDefinitions.G2_VALID_RESULT) {
      StringBuilder errorMessage = new StringBuilder("G2 diagnostic failed to initalize with error: ");
      errorMessage.append(g2DiagnosticErrorMessage(g2Diagnostic));
      throw new ServiceSetupException(errorMessage.toString());
    }
  }

  public String findEntitiesByFeatureIDs(List<Long> ids, long entityID) throws JSONException, ServiceExecutionException {
    
    JSONObject root = new JSONObject();
    root.put("ENTITY_ID", entityID);
    JSONArray idList = new JSONArray();
    ids.stream().forEach(id -> idList.put(id));
    root.put("LIB_FEAT_IDS", idList);
    StringBuffer response = new StringBuffer();
    int result = g2Diagnostic.findEntitiesByFeatureIDs(root.toString(), response);
    if (result != G2ServiceDefinitions.G2_VALID_RESULT) {
      StringBuilder errorMessage = new StringBuilder("G2 engine failed to find entities for features: ");
      errorMessage.append(g2DiagnosticErrorMessage(g2Diagnostic));
      throw new ServiceExecutionException(errorMessage.toString());
    }
    return response.toString();
  }


  public void postRiskScore(String riskScoreDoc) throws ServiceExecutionException {
    int result = g2Diagnostic.postRiskScore(riskScoreDoc);
    if (result != G2ServiceDefinitions.G2_VALID_RESULT) {
      StringBuilder errorMessage = new StringBuilder("G2 engine failed to post risk scores: ");
      errorMessage.append(g2DiagnosticErrorMessage(g2Diagnostic));
      throw new ServiceExecutionException(errorMessage.toString());
    }
  }

  static protected String g2DiagnosticErrorMessage(G2Diagnostic g2Diagnostic) {
    return g2Diagnostic.getLastExceptionCode() + ", " + g2Diagnostic.getLastException();
  }

}
