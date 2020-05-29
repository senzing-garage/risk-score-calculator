package com.senzing.calculator.scoring.risk.service;

import java.io.StringReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import com.senzing.listener.senzing.data.Definitions;
import com.senzing.listener.senzing.service.ListenerService;
import com.senzing.listener.senzing.service.exception.ServiceExecutionException;
import com.senzing.listener.senzing.service.exception.ServiceSetupException;
import com.senzing.calculator.scoring.risk.data.CommandOptions;
import com.senzing.calculator.scoring.risk.service.db.DatabaseService;
import com.senzing.calculator.scoring.risk.service.g2.G2ServiceExt;

public class RiskScoringService implements ListenerService {

  // Class for storing ftype overrides.
  private class Fbovr {
    private String fType;
    private String uType;

    public String getFType() {
      return fType;
    }

    public void setFType(String fType) {
      this.fType = fType;
    }

    public String getUType() {
      return uType;
    }

    public void setUType(String uType) {
      this.uType = uType;
    }
  }

  // Tags for incoming message.
  private static final String AFFECTED_ENTITIES_TAG = "AFFECTED_ENTITIES";
  // Configuration tags.
  private static final String G2_CONFIG_SECTION = "G2_CONFIG";
  private static final String CFG_FBOVR_SECTION = "CFG_FBOVR";
  private static final String CFG_FTYPE_SECTION = "CFG_FTYPE";
  private static final String FTYPE_CODE_TAG = "FTYPE_CODE";
  private static final String FTYPE_FREQ_TAG = "FTYPE_FREQ";
  private static final String FTYPE_EXCL_TAG = "FTYPE_EXCL";
  // Configuration values.
  private static final String F1_TAG = "F1";
  private static final String F1E_TAG = "F1E";
  private static final String F1ES_TAG = "F1ES";
  // Main sections for entity message.
  private static final String RESOLVED_ENTITY_SECTION = "RESOLVED_ENTITY";
  private static final String RELATED_ENTITIES_SECTION = "RELATED_ENTITIES";
  // Sub sections for entity message.
  private static final String FEATURES_SECTION = "FEATURES";
  private static final String RECORDS_SECTION = "RECORDS";
  // Features
  private static final String SSN_TAG = "SSN";
  private static final String DOB_TAG = "DOB";
  private static final String TRUSTED_ID_TAG = "TRUSTED_ID";
  private static final String RISK_SCORE_OVERRIDE_TAG = "RISK_SCORE_OVERRIDE";
  // Features' sub tags
  private static final String UTYPE_CODE_TAG = "UTYPE_CODE";
  private static final String FEAT_DESC_TAG = "FEAT_DESC";
  // Miscellaneous tags
  private static final String IS_AMBIGUOUS_TAG = "IS_AMBIGUOUS";
  private static final String MATCH_LEVEL_CODE_TAG = "MATCH_LEVEL_CODE";
  private static final String DATA_SOURCE_TAG = "DATA_SOURCE";
  private static final String LIB_FEAT_ID_TAG = "LIB_FEAT_ID";
  // Values.
  private static final String POSSIBLY_SAME_VALUE = "POSSIBLY_SAME";
  private static final String IMDM_VALUE = "IMDM";
  private static final String YES_VALUE = "YES";

  private static final int defaultLensID = 1;

  G2ServiceExt g2Service;
  List<String> f1Exclusive;
  List<String> f1Features;
  List<Fbovr> f1OverRideFType;
  DatabaseService dbService;

  boolean serviceUp;

  private static long processCount = 0;

  @Override
  public void init(String config) throws ServiceSetupException {
    serviceUp = false;
    // Get configuration
    String g2IniFile = null;
    String connectionString = null;
    try { 
      JsonReader reader = Json.createReader(new StringReader(config));
      JsonObject configObject = reader.readObject();
      g2IniFile = configObject.getString(CommandOptions.INI_FILE, "");
      connectionString = configObject.getString(CommandOptions.JDBC_CONNECTION, "");
    } catch (RuntimeException e) {
      throw new ServiceSetupException(e);
    }

    if (g2IniFile == null || g2IniFile.isEmpty()) {
      throw new ServiceSetupException(CommandOptions.INI_FILE + " missing from configuration");
    }
    if (connectionString == null || connectionString.isEmpty()) {
      throw new ServiceSetupException(CommandOptions.JDBC_CONNECTION + " missing from configuration");
    }

    g2Service = new G2ServiceExt();
    g2Service.init(g2IniFile);

    // Get the configuration and collect information from it.
    try {
      String g2Config = g2Service.exportConfig();
      JsonReader g2ConfigReader = Json.createReader(new StringReader(g2Config));
      JsonObject g2JsonConfig = g2ConfigReader.readObject();
      JsonObject g2ConfigRoot = g2JsonConfig.getJsonObject(G2_CONFIG_SECTION);
      // Get the exclusive features.
      f1Exclusive = extractF1ExclusiveFeatures(g2ConfigRoot);
      // Get the override features.
      f1OverRideFType = extractF1FeatureTypeOverride(g2ConfigRoot);
      // Get the F1 features.
      List<String> frequencies = Arrays.asList(F1_TAG);
      f1Features = extractFeatureTypesBasedOnFrequency(g2ConfigRoot, frequencies);

      dbService = new DatabaseService();
      dbService.init(connectionString);
    } catch (ServiceExecutionException | RuntimeException | SQLException e) {
      throw new ServiceSetupException(e);
    }

    serviceUp = true;
    Date current = new Date();
    System.out.println(current.toInstant() + " - Initalization complete");
  }

  @Override
  public void cleanUp() {
    g2Service.cleanUp();
  }

  @Override
  public void process(String message) throws ServiceExecutionException {
    // The message should be of format:
    // {
    //   "DATA_SOURCE":"TEST",
    //   "RECORD_ID":"RECORD3",
    //   "AFFECTED_ENTITIES":[
    //     {"ENTITY_ID":1,"LENS_CODE":"DEFAULT"}
    //   ]
    // }
    try {
      JsonReader reader = Json.createReader(new StringReader(message));
      JsonObject json = reader.readObject();
      // We are only interested in the entity ids from the AFFECTED_ENTITIES section.
      JsonArray entities = json.getJsonArray(AFFECTED_ENTITIES_TAG);
      if (entities != null) {
        for (int i = 0; i < entities.size(); i++) {
          JsonObject entity = entities.getJsonObject(i);
          if (entity != null) {
            Long entityID = entity.getJsonNumber(Definitions.ENTITY_ID_FIELD).longValue();
            processEntity(entityID);
          }
        }
      }
    } catch (RuntimeException e) {
      throw new ServiceExecutionException(e);
    }
  }

  private void processEntity(long entityID) throws ServiceExecutionException {
    // Get the information about the entity from G2.
    String entityData = null;
    try {
      entityData = g2Service.getEntity(entityID, false, false);
    } catch (ServiceExecutionException e) {
      if (e.getMessage().contains("Unknown resolved entity value")) {
        System.err.println(e.getMessage());
        System.err.println("Failed to get entity " + entityID);
      } else {
        // Bail out if any other error
        e.printStackTrace();
        System.exit(-2);
      }
    }
    if (entityData == null || entityData.isEmpty()) {
      dbService.postRiskScore(entityID, defaultLensID, null, null, null);
      return;
    }

    // The F1, F1E, F1ES and their overrides are collected for later processing.
    List<Long> f1ExLibFeatIDs = new ArrayList<Long>();
    List<Long> f1OvrExLibFeatIDs = new ArrayList<Long>();
    List<Long> f1LibFeatIDs = new ArrayList<Long>();

    try {
      JsonReader reader = Json.createReader(new StringReader(entityData));
      JsonObject rootObject = reader.readObject();

      // For collecting up scoring info
      RiskScorer riskScorer = new RiskScorer();

      // Data Quality check.
      JsonObject resolvedEntity = optJsonObject(rootObject, RESOLVED_ENTITY_SECTION);
      if (resolvedEntity != null) {

        // Good part of the needed data is contained in the features.
        // Check quality of SSN.
        JsonObject features = optJsonObject(resolvedEntity, FEATURES_SECTION);
        if (getFeatureCount(features, SSN_TAG) == 1) {
          riskScorer.setOneAndOnlyOneSSN(true);
        }

        // Check quality of DOB.
        int dobCount = getFeatureCount(features, DOB_TAG);
        if (dobCount == 1) {
          riskScorer.setOneAndOnlyOneDOB(true);
        } else if (dobCount > 1) {
          riskScorer.setMutltipleDOBs(true);
        }

        // Check if any exclusive types have multiple values.
        for (String fType : f1Exclusive) {
          JsonArray fTypeValues = optJsonArray(features, fType);
          if (fTypeValues != null) {
            if (fTypeValues.size() > 1) {
              riskScorer.setMultipleExclusives(true);
            }
            // Collect up the feature ids for later.
            collectLibFeatureIDs(fTypeValues, f1ExLibFeatIDs);
          }
        }

        // Any feature overrides need to be checked. They can also be F1 exclusive.
        for (Fbovr fbOvr : f1OverRideFType) {
          JsonArray fTypeValues = optJsonArray(features, fbOvr.getFType());
          if (fTypeValues != null && fTypeValues.size() > 1) {
            int cnt = 0;
            for (int i = 0; i < fTypeValues.size(); i++) {
              String uType = fTypeValues.getJsonObject(i).getString(UTYPE_CODE_TAG, null);
              if (uType != null && uType.contentEquals(fbOvr.getUType())) {
                cnt++;
              }
            }
            if (cnt > 1) {
              riskScorer.setMultipleExclusives(true);
              break;
            }
            // Collect up the feature ids for later.
            collectLibFeatureIDs(fTypeValues, f1OvrExLibFeatIDs);
          }
        }

        // Collect up F1 feature values. They are used later.
        for (String fType : f1Features) {
          JsonArray fTypeValues = optJsonArray(features, fType);
          if (fTypeValues != null) {
            collectLibFeatureIDs(fTypeValues, f1LibFeatIDs);
          }
        }
        // OT-TODO: This section is under discussion and its fate will be decided later.
        // Existence of TRUSTED_IDs indicates it could have been forced apart (forced
        // unmerge).
//        JSONArray trustedIDs = features.optJSONArray(TRUSTED_ID_TAG);
//        if (trustedIDs != null) {
//        // Non-shared trusted id means it was used to split entity apart.
//        // OT-TODO: verify the above is really true.
//          if (checkNotSharedFeature(trustedIDs)) {
//            riskScorer.setForcedUnMerge(true);
//          }
//        }

        // Handle any override of risk scores.
        JsonArray scoreOverride = optJsonArray(features, RISK_SCORE_OVERRIDE_TAG);
        if (scoreOverride != null) {
          for (int i = 0; i < scoreOverride.size(); i++) {
            String featDesc = scoreOverride.getJsonObject(i).getString(FEAT_DESC_TAG, "");
            riskScorer.setScoreOverride(featDesc);
          }
        }
      }

      // Now check if any of the F1 features are shared with other entities.
      boolean noSharedF1s = true;
      if (f1ExLibFeatIDs.size() > 0) {
        String results = getFeaturesForEntity(f1ExLibFeatIDs, entityID);
        if (checkForSharedFeatures(results)) {
          riskScorer.setSharedF1Exclusives(true);
          noSharedF1s = false;
        }
      }
      if (riskScorer.hasSharedF1Exclusives() == false && f1OvrExLibFeatIDs.size() > 0) {
        String results = getFeaturesForEntity(f1OvrExLibFeatIDs, entityID);
        if (checkForSharedFeatures(results)) {
          riskScorer.setSharedF1Exclusives(true);
          noSharedF1s = false;
        }
      }
      if (f1LibFeatIDs.size() > 0) {
        String results = getFeaturesForEntity(f1LibFeatIDs, entityID);
        if (checkForSharedFeatures(results)) {
          noSharedF1s = false;
        }
      }
      riskScorer.setNoSharedF1(noSharedF1s);

      // Check if the related entities section reveals any ambiguous relationships (count as red).
      JsonArray relatedEntities = optJsonArray(rootObject, RELATED_ENTITIES_SECTION);
      if (relatedEntities != null) {
        boolean noPossibleMatch = true;
        for (int i = 0; i < relatedEntities.size(); i++) {
          JsonObject entity = relatedEntities.getJsonObject(i);

          if (entity.getInt(IS_AMBIGUOUS_TAG) > 0) {
            riskScorer.setAmbiguous(true);
          }
          String matchLevelCode = entity.getString(MATCH_LEVEL_CODE_TAG, null);
          if (matchLevelCode != null && matchLevelCode.equals(POSSIBLY_SAME_VALUE)) {
            noPossibleMatch = false;
          }
        }
        riskScorer.setNoPossibleMatch(noPossibleMatch);
      }

      // Do we have iMDM data source in this entity (counts as green).
      JsonArray records = optJsonArray(resolvedEntity, RECORDS_SECTION);
      if (records != null) {
        for (int i = 0; i < records.size(); i++) {
          JsonObject record = records.getJsonObject(i);
          String dataSource = record.getString(DATA_SOURCE_TAG, null);
          if (dataSource != null && dataSource.toUpperCase().equals(IMDM_VALUE)) {
            riskScorer.setSourceIMDM(true);
            break;
          }
        }
      }

      // Data collection is done. Lets report the findings.
      reportScoring(entityID, defaultLensID, riskScorer);
    } catch (RuntimeException e) {
      throw new ServiceExecutionException(e);
    }
  }

  private List<String> extractFeatureTypesBasedOnFrequency(JsonObject configRoot, List<String> fqs) {
    List<String> features = new ArrayList<>();
    JsonArray fTypes = configRoot.getJsonArray(CFG_FTYPE_SECTION);
    for (int i = 0; i < fTypes.size(); i++) {
      JsonObject fType = fTypes.getJsonObject(i);
      String frequency = fType.getString(FTYPE_FREQ_TAG, "");
      if (fqs.contains(frequency)) {
        features.add(fType.getString(FTYPE_CODE_TAG));
      }
    }
    return features;
  }

  private List<String> extractF1ExclusiveFeatures(JsonObject configRoot) {
    List<String> features = new ArrayList<>();
    List<String> fqs = Arrays.asList(F1_TAG, F1E_TAG, F1ES_TAG);
    JsonArray fTypes = configRoot.getJsonArray(CFG_FTYPE_SECTION);
    for (int i = 0; i < fTypes.size(); i++) {
      JsonObject fType = fTypes.getJsonObject(i);
      String frequency = fType.getString(FTYPE_FREQ_TAG, "");
      String Exclusive = fType.getString(FTYPE_EXCL_TAG, "");
      if (fqs.contains(frequency) && Exclusive.toUpperCase().equals(YES_VALUE)) {
        features.add(fType.getString(FTYPE_CODE_TAG));
      }
    }
    return features;
  }

  private List<Fbovr> extractF1FeatureTypeOverride(JsonObject configRoot) {
    List<Fbovr> overRideFeats = new ArrayList<>();
    List<String> fqs = Arrays.asList(F1_TAG, F1E_TAG, F1ES_TAG);
    JsonArray fTypes = configRoot.getJsonArray(CFG_FBOVR_SECTION);
    for (int i = 0; i < fTypes.size(); i++) {
      JsonObject fType = fTypes.getJsonObject(i);
      String frequency = fType.getString(FTYPE_FREQ_TAG, "");
      String Exclusive = fType.getString(FTYPE_EXCL_TAG, "");
      if (fqs.contains(frequency) && Exclusive.toUpperCase().equals(YES_VALUE)) {
        Fbovr fbovr = new Fbovr();
        fbovr.setFType(fType.getString(FTYPE_CODE_TAG));
        fbovr.setUType(fType.getString(UTYPE_CODE_TAG));
        overRideFeats.add(fbovr);
      }
    }
    return overRideFeats;
  }

  private void collectLibFeatureIDs(JsonArray fTypeValues, List<Long> libFeatIDs) {
    for (int i = 0; i < fTypeValues.size(); i++) {
      JsonObject fTypeObject = fTypeValues.getJsonObject(i);
      Long featID = fTypeObject.getJsonNumber(LIB_FEAT_ID_TAG).longValue();
      if (featID != null) {
        libFeatIDs.add(featID);
      }
    }
  }

  private String getFeaturesForEntity(List<Long> feats, long entityID) throws ServiceExecutionException {
    try {
      return g2Service.findEntitiesByFeatureIDs(feats, entityID);
    } catch (RuntimeException e) {
      throw new ServiceExecutionException(e);
    }
  }

  private boolean checkForSharedFeatures(String jsonDoc) throws ServiceExecutionException {
    try {
      JsonReader reader = Json.createReader(new StringReader(jsonDoc));
      JsonArray featJson = reader.readArray();
      return featJson.size() > 0;
    } catch (RuntimeException e) {
      throw new ServiceExecutionException(e);
    }
  }

  public void reportScoring(long entityID, int lensID, RiskScorer riskScorer) throws ServiceExecutionException {
    try {
      dbService.postRiskScore(entityID, lensID, riskScorer.getDataQualityScore().toString(), riskScorer.getCollisionScore().toString(), riskScorer.getReason());
    } catch (RuntimeException e) {
      throw new ServiceExecutionException(e);
    }
    processCount++;
    if (processCount % 1000 == 0) {
      Date current = new Date();
      System.out.println(current.toInstant() + " - Processed " + processCount + " records.");
    }
  }

  private int getFeatureCount(JsonObject features, String key) {
    JsonArray value = optJsonArray(features, key);
    int retVal = 0;
    if (value != null) {
      retVal = value.size();
    }
    return retVal;
  }

  private JsonObject optJsonObject(JsonObject jsonObject, String key) {
    JsonObject retVal = null;
    try {
      retVal = jsonObject.getJsonObject(key);
    } catch (Exception e) {
    }
    return retVal;
  }

  private JsonArray optJsonArray(JsonObject jsonObject, String key) {
    JsonArray retVal = null;
    try {
      retVal = jsonObject.getJsonArray(key);
    } catch (Exception e) {
    }
    return retVal;
  }

  public boolean isServiceUp() {
    return serviceUp;
  }

}
