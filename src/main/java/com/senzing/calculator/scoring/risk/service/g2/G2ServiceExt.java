package com.senzing.calculator.scoring.risk.service.g2;

import java.io.IOException;
import java.util.List;

import javax.json.Json;
import javax.json.JsonObjectBuilder;

import com.senzing.g2.engine.G2Diagnostic;
import com.senzing.g2.engine.G2DiagnosticJNI;
import com.senzing.listener.service.exception.ServiceExecutionException;
import com.senzing.listener.service.exception.ServiceSetupException;
import com.senzing.listener.service.g2.G2Service;
import com.senzing.listener.service.g2.G2ServiceDefinitions;

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
    } catch (IOException | RuntimeException e) {
      throw new ServiceSetupException(e);
    }
    g2Diagnostic = new G2DiagnosticJNI();
    int result = g2Diagnostic.init(diagnosticModuleName, configData, verboseLogging);
    if (result != G2ServiceDefinitions.G2_VALID_RESULT) {
      StringBuilder errorMessage = new StringBuilder("G2 diagnostic failed to initalize with error: ");
      errorMessage.append(g2DiagnosticErrorMessage(g2Diagnostic));
      throw new ServiceSetupException(errorMessage.toString());
    }
  }

  public void destroy() {
    if (g2Diagnostic != null) {
      g2Diagnostic.destroy();
    }
    super.destroy();
  }

  public String findEntitiesByFeatureIDs(List<Long> ids, long entityID) throws ServiceExecutionException {
    
    JsonObjectBuilder rootObject = Json.createObjectBuilder();
    rootObject.add("ENTITY_ID", entityID);
    rootObject.add("LIB_FEAT_IDS", Json.createArrayBuilder(ids).build());
    StringBuffer response = new StringBuffer();
    String outStr = rootObject.build().toString();
    int result = g2Diagnostic.findEntitiesByFeatureIDs(outStr, response);
    if (result != G2ServiceDefinitions.G2_VALID_RESULT) {
      StringBuilder errorMessage = new StringBuilder("G2 engine failed to find entities for features: ");
      errorMessage.append(g2DiagnosticErrorMessage(g2Diagnostic));
      throw new ServiceExecutionException(errorMessage.toString());
    }
    return response.toString();
  }

  static protected String g2DiagnosticErrorMessage(G2Diagnostic g2Diagnostic) {
    return g2Diagnostic.getLastExceptionCode() + ", " + g2Diagnostic.getLastException();
  }

}
