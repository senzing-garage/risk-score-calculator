package com.senzing.calculator.scoring.risk.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.senzing.listener.senzing.data.Definitions;
import com.senzing.listener.senzing.service.ListenerService;
import com.senzing.listener.senzing.service.exception.ServiceExecutionException;
import com.senzing.listener.senzing.service.exception.ServiceSetupException;
import com.senzing.calculator.scoring.risk.data.CommandOptions;
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

  G2ServiceExt g2Service;
  List<String> f1Exclusive;
  List<String> f1Features;
  List<Fbovr> f1OverRideFType;

  private static long processCount = 0;

  @Override
  public void init(String config) throws ServiceSetupException {
    // Get configuration
    String g2IniFile = null;
    try { 
      JSONObject configObject = new JSONObject(config);
      g2IniFile = configObject.optString(CommandOptions.INI_FILE);
    } catch (JSONException e) {
      throw new ServiceSetupException(e);
    }
    g2Service = new G2ServiceExt();
    g2Service.init(g2IniFile);

    // Get the configuration and collect information from it.
    try {
      String g2Config = g2Service.exportConfig();
      JSONObject jsonConfig = new JSONObject(g2Config);
      JSONObject configRoot = jsonConfig.getJSONObject(G2_CONFIG_SECTION);
      // Get the exclusive features.
      List<String> frequencies = Arrays.asList(F1E_TAG, F1ES_TAG);
      f1Exclusive = extractFeatureTypesBasedOnFrequency(configRoot, frequencies);
      // Get the override features.
      f1OverRideFType = extractF1FeatureTypeOverride(configRoot, frequencies);
      // Get the F1 features.
      frequencies = Arrays.asList(F1_TAG);
      f1Features = extractFeatureTypesBasedOnFrequency(configRoot, frequencies);
    } catch (ServiceExecutionException | JSONException e) {
      throw new ServiceSetupException(e);
    }
    System.out.println("Initalization complete");
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
      JSONObject json = new JSONObject(message);
      // We are only interested in the entity ids from the AFFECTED_ENTITIES section.
      JSONArray entities = json.getJSONArray(AFFECTED_ENTITIES_TAG);
      if (entities != null) {
        for (int i = 0; i < entities.length(); i++) {
          JSONObject entity = entities.getJSONObject(i);
          if (entity != null) {
            Long entityID = entity.getLong(Definitions.ENTITY_ID_FIELD);
            processEntity(entityID);
          }
        }
      }
    } catch (JSONException e) {
      throw new ServiceExecutionException(e);
    }
  }

  private void processEntity(long entityID) throws ServiceExecutionException, JSONException {
    // Get the information about the entity from G2.
    String entityData = g2Service.getEntity(entityID, true, false);

    // The F1, F1E, F1ES and their overrides are collected for later processing.
    List<Long> f1ExLibFeatIDs = new ArrayList<Long>();
    List<Long> f1OvrExLibFeatIDs = new ArrayList<Long>();
    List<Long> f1LibFeatIDs = new ArrayList<Long>();

    try {
      JSONObject rootObject = new JSONObject(entityData);

      // For collecting up scoring info
      RiskScorer riskScorer = new RiskScorer();

      // Data Quality check.
      JSONObject resolvedEntity = rootObject.optJSONObject(RESOLVED_ENTITY_SECTION);
      if (resolvedEntity != null) {

        // Good part of the needed data is contained in the features.
        // Check quality of SSN.
        JSONObject features = resolvedEntity.optJSONObject(FEATURES_SECTION);
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
          JSONArray fTypeValues = features.optJSONArray(fType);
          if (fTypeValues != null) {
            if (fTypeValues.length() > 1) {
              riskScorer.setMultipleExclusives(true);
            }
            // Collect up the feature ids for later.
            collectLibFeatureIDs(fTypeValues, f1ExLibFeatIDs);
          }
        }

        // Any feature overrides need to be checked. They can also be F1 exclusive.
        for (Fbovr fbOvr : f1OverRideFType) {
          JSONArray fTypeValues = features.optJSONArray(fbOvr.getFType());
          if (fTypeValues != null && fTypeValues.length() > 1) {
            int cnt = 0;
            for (int i = 0; i < fTypeValues.length(); i++) {
              String uType = fTypeValues.getJSONObject(i).optString(UTYPE_CODE_TAG);
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
          JSONArray fTypeValues = features.optJSONArray(fType);
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
        JSONArray scoreOverride = features.optJSONArray(RISK_SCORE_OVERRIDE_TAG);
        if (scoreOverride != null) {
          for (int i = 0; i < scoreOverride.length(); i++) {
            String featDesc = scoreOverride.getJSONObject(i).optString(FEAT_DESC_TAG);
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
      JSONArray relatedEntities = rootObject.optJSONArray(RELATED_ENTITIES_SECTION);
      if (relatedEntities != null) {
        boolean noPossibleMatch = true;
        for (int i = 0; i < relatedEntities.length(); i++) {
          JSONObject entity = relatedEntities.getJSONObject(i);

          if (entity.getInt(IS_AMBIGUOUS_TAG) > 0) {
            riskScorer.setAmbiguous(true);
          }
          String matchLevelCode = entity.optString(MATCH_LEVEL_CODE_TAG);
          if (matchLevelCode != null && matchLevelCode.equals(POSSIBLY_SAME_VALUE)) {
            noPossibleMatch = false;
          }
        }
        riskScorer.setNoPossibleMatch(noPossibleMatch);
      }

      // Do we have iMDM data source in this entity (counts as green).
      JSONArray records = resolvedEntity.optJSONArray(RECORDS_SECTION);
      if (records != null) {
        for (int i = 0; i < records.length(); i++) {
          JSONObject record = records.getJSONObject(i);
          String dataSource = record.optString(DATA_SOURCE_TAG);
          if (dataSource != null && dataSource.toUpperCase().equals(IMDM_VALUE)) {
            riskScorer.setSourceIMDM(true);
            break;
          }
        }
      }

      // Data collection is done. Lets report the findings.
      reportScoring(entityID, riskScorer);
    } catch (JSONException e) {
      throw new ServiceExecutionException(e);
    }
  }

  private List<String> extractFeatureTypesBasedOnFrequency(JSONObject configRoot, List<String> fqs) throws JSONException {
    List<String> features = new ArrayList<>();
    JSONArray fTypes = configRoot.getJSONArray(CFG_FTYPE_SECTION);
    for (int i = 0; i < fTypes.length(); i++) {
      JSONObject fType = fTypes.getJSONObject(i);
      String frequency = fType.optString(FTYPE_FREQ_TAG);
      if (fqs.contains(frequency)) {
        features.add(fType.getString(FTYPE_CODE_TAG));
      }
    }
    return features;
  }

  private List<Fbovr> extractF1FeatureTypeOverride(JSONObject configRoot, List<String> fqs) throws JSONException {
    List<Fbovr> overRideFeats = new ArrayList<>();
    JSONArray fTypes = configRoot.getJSONArray(CFG_FBOVR_SECTION);
    for (int i = 0; i < fTypes.length(); i++) {
      JSONObject fType = fTypes.getJSONObject(i);
      String frequency = fType.optString(FTYPE_FREQ_TAG);
      if (fqs.contains(frequency)) {
        Fbovr fbovr = new Fbovr();
        fbovr.setFType(fType.getString(FTYPE_CODE_TAG));
        fbovr.setUType(fType.getString(UTYPE_CODE_TAG));
        overRideFeats.add(fbovr);
      }
    }
    return overRideFeats;
  }

  private void collectLibFeatureIDs(JSONArray fTypeValues, List<Long> libFeatIDs) throws JSONException {
    for (int i = 0; i < fTypeValues.length(); i++) {
      JSONObject fTypeObject = fTypeValues.getJSONObject(i);
      Long featID = fTypeObject.getLong(LIB_FEAT_ID_TAG);
      if (featID != null) {
        libFeatIDs.add(featID);
      }
    }
  }

  private String getFeaturesForEntity(List<Long> feats, long entityID) throws JSONException, ServiceExecutionException {
    return g2Service.findEntitiesByFeatureIDs(feats, entityID);
  }

  private boolean checkForSharedFeatures(String jsonDoc) throws JSONException {
    JSONArray featJson = new JSONArray(jsonDoc);
    return featJson.length() > 0;
  }

  public void reportScoring(long entityID, RiskScorer riskScorer) throws JSONException, ServiceExecutionException {
    JSONObject riskScoreDoc = new JSONObject();
    riskScoreDoc.put("ENTITY_ID", entityID);
    riskScoreDoc.put("QUALITY_SCORE", riskScorer.getDataQualityScore().toString());
    riskScoreDoc.put("COLLISION_SCORE", riskScorer.getCollisionScore().toString());
    riskScoreDoc.put("REASON", riskScorer.getReason());
    g2Service.postRiskScore(riskScoreDoc.toString());
    processCount++;
    if (processCount % 1000 == 0) {
      System.out.println("Processed " + processCount + " records.");
    }
  }

  private int getFeatureCount(JSONObject features, String key) {
    JSONArray value = features.optJSONArray(key);
    int retVal = 0;
    if (value != null) {
      retVal = value.length();
    }
    return retVal;
  }
}
