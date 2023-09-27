package com.senzing.calculator.scoring.risk.service;

import java.io.StringReader;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import com.senzing.util.JsonUtilities;
import com.senzing.listener.service.ListenerService;
import com.senzing.listener.service.exception.ServiceExecutionException;
import com.senzing.listener.service.exception.ServiceSetupException;
import com.senzing.listener.service.g2.G2Service;
import com.senzing.calculator.scoring.risk.data.CommandOptions;
import com.senzing.calculator.scoring.risk.data.Definitions;
import com.senzing.calculator.scoring.risk.service.db.DatabaseService;
import com.senzing.calculator.scoring.risk.service.g2.G2ServiceExt;

import static com.senzing.listener.service.ListenerService.*;
import static com.senzing.listener.service.ListenerService.State.*;

public class RiskScoringService implements ListenerService {

  // Class for storing ftype overrides.
  private class FeatureTypeOverride {
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
  private static final String ADDRESS_TAG = "ADDRESS";
  private static final String TRUSTED_ID_TAG = "TRUSTED_ID";
  private static final String RISK_SCORE_OVERRIDE_TAG = "RISK_SCORE_OVERRIDE";
  // Features' sub tags
  private static final String UTYPE_CODE_TAG = "UTYPE_CODE";
  private static final String FEAT_DESC_TAG = "FEAT_DESC";
  private static final String FEAT_DESC_VALUES_TAG = "FEAT_DESC_VALUES";
  // Relationship sub tags
  private static final String IS_AMBIGUOUS_TAG = "IS_AMBIGUOUS";
  private static final String MATCH_LEVEL_CODE_TAG = "MATCH_LEVEL_CODE";
  private static final String MATCH_KEY_TAG = "MATCH_KEY";
  // Miscellaneous tags
  private static final String DATA_SOURCE_TAG = "DATA_SOURCE";
  private static final String LIB_FEAT_ID_TAG = "LIB_FEAT_ID";
  private static final String CANDIDATE_CAP_REACHED_TAG = "CANDIDATE_CAP_REACHED";
  private static final String SCORING_CAP_REACHED_TAG = "SCORING_CAP_REACHED";
  // Values.
  private static final String POSSIBLY_SAME_VALUE = "POSSIBLY_SAME";
  private static final String YES_VALUE = "YES";
  private static final String Y_VALUE = "Y";
  // Other strings
  private static final String COMMA = ",";

  private static final int defaultLensID = 1;

  G2ServiceExt g2Service;
  DatabaseService dbService;

  List<String> f1Exclusive;
  List<String> f1Features;
  List<FeatureTypeOverride> f1OverRideFType;
  List<String> trustedSources;
  List<QueryRiskData> queryRiskCriteria;

  State state = UNINITIALIZED;
  boolean serviceUp;

  private static long processCount = 0;
  private static long missingEntityCount = 0;

  /**
   * Implemented to return the statistics associated with this instance.
   * 
   * {@inheritDoc}
   */
  @Override
  public synchronized Map<Statistic, Number> getStatistics() {
      return Collections.emptyMap();
  }

  @Override
  public synchronized State getState() {
    return this.state;
  }

  /**
   * Sets the state for this instance.
   * @param state The state for this instance.
   */
  protected synchronized void setState(State state) {
    this.state = state;
  }

  @Override
  public void init(JsonObject config) throws ServiceSetupException {
    this.setState(INITIALIZING);
    serviceUp = false;
    // Get configuration
    String g2IniFile = null;
    String connectionString = null;
    String trustedSourcesString = null;
    String queryRiskString = null;
    try {
      g2IniFile = JsonUtilities.getString(config, CommandOptions.INI_FILE, "");
      connectionString = JsonUtilities.getString(config, CommandOptions.JDBC_CONNECTION, "");
      trustedSourcesString = JsonUtilities.getString(config, CommandOptions.TRUSTED_SOURCES, "");
      queryRiskString = JsonUtilities.getString(config, CommandOptions.QUERY_RISK_CRITERIA, "");
    } catch (RuntimeException e) {
      throw new ServiceSetupException(e);
    }

    if (g2IniFile == null || g2IniFile.isEmpty()) {
      throw new ServiceSetupException(CommandOptions.INI_FILE + " missing from configuration");
    }
    if (connectionString == null || connectionString.isEmpty()) {
      throw new ServiceSetupException(CommandOptions.JDBC_CONNECTION + " missing from configuration");
    }
    if (trustedSourcesString == null || trustedSourcesString.isEmpty()) {
      System.err.println("WARNING: trusted sources is not configured!");
    }
    if (queryRiskString == null || queryRiskString.isEmpty()) {
      System.err.println("WARNING: query risk criteria is not configured!");
    }

    trustedSources = parseAndFormatCommaSeparatedString(trustedSourcesString);
    queryRiskCriteria = parseAndProcessQueryRiskCriteria(queryRiskString);

    JsonObjectBuilder job = Json.createObjectBuilder();
    job.add(G2Service.G2_INIT_CONFIG_KEY, g2IniFile);
    job.add(G2Service.G2_MODULE_NAME_KEY, "RiskScoringService");
    JsonObject g2InitConfig = job.build();
    g2Service = new G2ServiceExt();
    g2Service.init(g2InitConfig);

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
    this.setState(AVAILABLE);
  }


  @Override
  public void destroy() {
    this.setState(DESTROYING);
    g2Service.destroy();
    this.setState(DESTROYED);
  }


  @Override
  public void process(JsonObject message) throws ServiceExecutionException {
    // The message should be of format:
    // {
    //   "DATA_SOURCE":"TEST",
    //   "RECORD_ID":"RECORD3",
    //   "AFFECTED_ENTITIES":[
    //     {"ENTITY_ID":1,"LENS_CODE":"DEFAULT"}
    //   ]
    // }
    try {
      // We are only interested in the entity ids from the AFFECTED_ENTITIES section.
      JsonArray entities = JsonUtilities.getJsonArray(message, AFFECTED_ENTITIES_TAG);
      if (entities != null) {
        for (int i = 0; i < entities.size(); i++) {
          JsonObject entity = entities.getJsonObject(i);
          if (entity != null) {
            Long entityID = JsonUtilities.getLong(entity, Definitions.ENTITY_ID_FIELD);
            processEntity(entityID);
          }
        }
      }
    } catch (RuntimeException e) {
      throw new ServiceExecutionException(e);
    }
  }

  /**
   * Calculates risk scores (data quality and collision scores) for entity of ID entityID
   * 
   * @param entityID ID for the entity being scored
   * @throws ServiceExecutionException
   */
  private void processEntity(long entityID) throws ServiceExecutionException {
    // Get the information about the entity from G2.
    String entityData = null;
    try {
      entityData = g2Service.getEntity(entityID, true, true);
    } catch (ServiceExecutionException e) {
      if (e.getMessage().contains("Unknown resolved entity value")) {
        missingEntityCount++;
      } else {
        // Bail out if any other error
        e.printStackTrace();
        System.exit(-2);
      }
    }
    if (entityData == null || entityData.isEmpty()) {
      dbService.postRiskScore(entityID, defaultLensID, null, null, null, null, null);
      return;
    }
    // The F1, F1E, F1ES and their overrides are collected for later processing.
    Map<Long, FeatData> f1ExLibFeats = new HashMap<>();
    Map<Long, FeatData> f1OvrExLibFeats = new HashMap<>();
    Map<Long, FeatData> f1LibFeats = new HashMap<>();

    try {
      JsonReader reader = Json.createReader(new StringReader(entityData));
      JsonObject rootObject = reader.readObject();

      // For collecting up scoring info
      RiskScorer riskScorer = new RiskScorer();

      // Find section where the actual entity data exists.
      JsonObject resolvedEntity = optJsonObject(rootObject, RESOLVED_ENTITY_SECTION);

      //=========================================
      // Data quality check
      //=========================================

      // Good part of the needed data for data quality is contained in the features.
      // Check quality of SSN.
      JsonObject features = optJsonObject(resolvedEntity, FEATURES_SECTION);
      if (getFeatureCount(features, SSN_TAG) <= 1) {
        riskScorer.setOneOrLessSSN(true);
      }

      // Check quality of DOB.
      int dobCount = getFeatureCount(features, DOB_TAG);
      if (dobCount == 1) {
        riskScorer.setOneAndOnlyOneDOB(true);
      } else if (dobCount > 1) {
        riskScorer.setMutltipleDOBs(true);
      }

      // Check for quality of addresses
      int addressCount = getFeatureCount(features, ADDRESS_TAG);
      if (addressCount > 0) {
        riskScorer.setOneOrMoreAddress(true);
      }

      // Check if any exclusive types have multiple values (counts as red if any found).
      checkForMultipleExclusives(features, f1ExLibFeats, riskScorer);

      // Any feature overrides need to be checked. They can also be F1 exclusive.
      checkForMultipleExclusivesForOverrides(features, f1OvrExLibFeats, riskScorer);

      // Collect up F1 feature values. They are used later.
      for (String fType : f1Features) {
        JsonArray fTypeValues = optJsonArray(features, fType);
        if (fTypeValues != null) {
          collectLibFeatures(fType, fTypeValues, f1LibFeats, false);
        }
      }

      // OT-TODO: This section is under discussion and its fate will be decided later.
      // Existence of TRUSTED_IDs indicates it could have been forced apart (forced
      // unmerge).
      //checkForUnmerge(features, riskScorer);

      //=========================================
      // Collision quality check
      //=========================================

      // Handle any override of risk scores.
      JsonArray scoreOverride = optJsonArray(features, RISK_SCORE_OVERRIDE_TAG);
      if (scoreOverride != null) {
        for (int i = 0; i < scoreOverride.size(); i++) {
          String featDesc = scoreOverride.getJsonObject(i).getString(FEAT_DESC_TAG, "");
          riskScorer.setScoreOverride(featDesc);
        }
      }

      // Check if any of the F1 exclusive features are shared with other entities (count as red if any found).
      checkF1ExcusivesShared(f1ExLibFeats, entityID, riskScorer);

      // Check if any of the override F1 exclusive features are shared with other entities (count as red if any found).
      checkF1ExcusivesShared(f1OvrExLibFeats, entityID, riskScorer);

      // Check if any of the F1 exclusive features are shared with other entities (count as green if none found).
      checkF1Shared(f1LibFeats, entityID, riskScorer);

      // Check if the related entities section reveals any ambiguous relationships or possible matches (count as red if found).
      checkRelationships(rootObject, riskScorer);

      // Do we have a trusted data source in this entity (counts as green if found).
      checkTrustedDatasource(resolvedEntity, riskScorer);

      //=========================================
      // Match quality check
      //=========================================
      checkQueryRisk(rootObject, riskScorer);

      //=========================================
      // Reporting
      //=========================================

      // Data collection is done. Lets report the findings.
      reportScoring(entityID, defaultLensID, riskScorer);

      // Manage record count and report on records processed.
      reportProgress();
    } catch (RuntimeException e) {
      throw new ServiceExecutionException(e);
    }
  }



  //===================================================================
  // Methods for data quality rules
  //===================================================================

  /*
   * Checks if any of the F1 exclusive (F1E and f1ES) have multiple values and adds the finding to the risk scorer.
   * It also collects all F1 exclusive it finds to "exclusiveFeats" which can be used for other processing later.
   */
  private void checkForMultipleExclusives(JsonObject features, Map<Long, FeatData> exclusiveFeats, RiskScorer riskScorer) {
    for (String fType : f1Exclusive) {
      JsonArray fTypeValues = optJsonArray(features, fType);
      if (fTypeValues != null) {
        if (fTypeValues.size() > 1) {
          List<String> featList = getMultiValueFeatures(fTypeValues);
          riskScorer.addMultipleExclusives(fType, featList);
        }
        // Collect up the feature values for later.
        collectLibFeatures(fType, fTypeValues, exclusiveFeats, true);
      }
    }
  }

  /*
   * Checks if any of the F1 exclusive overrides have multiple values and adds the finding to the risk scorer.
   * It also collects all found to "exclusiveFeats" which can be used for other processing later.
   */
  private void checkForMultipleExclusivesForOverrides(JsonObject features, Map<Long, FeatData> exclusiveFeats, RiskScorer riskScorer) {
    for (FeatureTypeOverride fTypeOverride : f1OverRideFType) {
      JsonArray fTypeValues = optJsonArray(features, fTypeOverride.getFType());
      if (fTypeValues != null && fTypeValues.size() > 1) {
        for (int i = 0; i < fTypeValues.size(); i++) {
          String uType = fTypeValues.getJsonObject(i).getString(UTYPE_CODE_TAG, null);
          if (uType != null && uType.contentEquals(fTypeOverride.getUType())) {
            List<String> featList = getMultiValueFeatures(fTypeValues);
            riskScorer.addMultipleExclusives(fTypeOverride.getFType(), featList);
          }
        }
        // Collect up the feature values for later.
        collectLibFeatures(fTypeOverride.getFType(), fTypeValues, exclusiveFeats, true);
      }
    }
  }

  /*
   * Checks with G2 if any of the F1 exclusive features are shared with other entities.
   * If any are found, they are added to the risk scorer for evaluation.
   */
  private void checkF1ExcusivesShared(Map<Long, FeatData> f1Exclusives, long entityID, RiskScorer riskScorer) throws ServiceExecutionException {
    if (f1Exclusives.size() > 0) {
      List<Long> ids = new ArrayList<>();
      ids.addAll(f1Exclusives.keySet());
      // Query G2. When checking, the entityID is excluded so returned values all belong to other entities.
      String results = getFeaturesForEntity(ids, entityID);
      List<FeatData> featData = checkForSharedFeatures(results, f1Exclusives);
      if (!featData.isEmpty()) {
        riskScorer.addSharedExclusives(featData);
        riskScorer.addSharedF1s(featData);
      }
    }
  }

  // OT-TODO: Not settled how unmerge will be handled.
  private void checkForUnmerge(JsonObject features, RiskScorer riskScorer) {
    JsonArray trustedIDs = optJsonArray(features, TRUSTED_ID_TAG);
    if (trustedIDs != null) {
    // Non-shared trusted id means it was used to split entity apart.
    // OT-TODO: verify the above is really true.
//      if (checkNotSharedFeature(trustedIDs)) {
//        riskScorer.setForcedUnMerge(true);
//      }
    }
  }


  //===================================================================
  // Methods for collision rules
  //===================================================================

  /*
   * Checks with G2 if any of the F1 features are shared with other entities.
   * If any are found, they are added to the risk scorer for evaluation.
   */
  private void checkF1Shared(Map<Long, FeatData> f1LibFeats, long entityID, RiskScorer riskScorer) throws ServiceExecutionException {
    if (f1LibFeats.size() > 0) {
      List<Long> ids = new ArrayList<>();
      ids.addAll(f1LibFeats.keySet());
      String results = getFeaturesForEntity(ids, entityID);
      List<FeatData> featData = checkForSharedFeatures(results, f1LibFeats);
      if (!featData.isEmpty()) {
        riskScorer.addSharedF1s(featData);
      }
    }
  }

  /*
   * Scans the entity for any ambiguous relationships and any possible matches.
   * The risk scorer is updated with the results found.
   */
  private void checkRelationships(JsonObject entityRoot, RiskScorer riskScorer) {
    JsonArray relatedEntities = optJsonArray(entityRoot, RELATED_ENTITIES_SECTION);
    if (relatedEntities != null) {
      boolean noPossibleMatch = true;
      for (int i = 0; i < relatedEntities.size(); i++) {
        JsonObject entity = relatedEntities.getJsonObject(i);

        // Check ambiguous
        if (entity.getInt(IS_AMBIGUOUS_TAG) > 0) {
          riskScorer.setAmbiguous(true);
        }
        // Check possible match.
        String matchLevelCode = entity.getString(MATCH_LEVEL_CODE_TAG, null);
        if (POSSIBLY_SAME_VALUE.equals(matchLevelCode) ) {
          noPossibleMatch = false;
        }
      }
      riskScorer.setNoPossibleMatch(noPossibleMatch);
    }
  }

  /*
   * Checks if any of the data sources are in the trusted sources list.
   * The risk scorer is updated with the results found.
   */
  private void checkTrustedDatasource(JsonObject resolvedEntity, RiskScorer riskScorer) {
    JsonArray records = optJsonArray(resolvedEntity, RECORDS_SECTION);
    if (records != null) {
      for (int i = 0; i < records.size(); i++) {
        JsonObject record = records.getJsonObject(i);
        String dataSource = record.getString(DATA_SOURCE_TAG, null).strip().toUpperCase();
        if (trustedSources.contains(dataSource)) {
          riskScorer.addTrustedSource(dataSource);
        }
      }
    }
  }


  //===================================================================
  // Methods for match quality
  //===================================================================

  /*
   * Checks the relationships for the quality of the matches.
   * The risk scorer is updated with the results found.
   */
  private void checkQueryRisk(JsonObject entityRoot, RiskScorer riskScorer) throws ServiceExecutionException {
    JsonArray relatedEntities = optJsonArray(entityRoot, RELATED_ENTITIES_SECTION);
    if (relatedEntities != null) {
      for (int i = 0; i < relatedEntities.size(); i++) {
        JsonObject entity = relatedEntities.getJsonObject(i);

        // Analyze the match key
        String matchKey = entity.getString(MATCH_KEY_TAG);
        List<String> parsedMatchKey = null;
        try {
          parsedMatchKey = parseMatchKey(matchKey);
        } catch (ParseException e) {
          throw new ServiceExecutionException("Badly formed match key: " + matchKey);
        }
        if (parsedMatchKey.size() > 0) {
          for (QueryRiskData queryRiskData : queryRiskCriteria) {
            boolean criteriaMatch = true;
            for (String matchKeyItem : queryRiskData.getCriteriaList()) {
              if (!parsedMatchKey.contains(matchKeyItem)) {
                criteriaMatch = false;
                break;
              }
            }
            if (criteriaMatch) {
              riskScorer.addQueryRisk(queryRiskData.getCriteriaString(), queryRiskData.getScore());
            }
          }
        }
      }
    }
  }

  //===================================================================
  // Other assisting methods
  //===================================================================

  /*
   * Collects a list of features from g2config JSON document, with frequency (F1, FM etc.) in fqs.
   */
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

  /*
   * Collects information about F1 exclusive features from g2config JSON document.
   */
  private List<String> extractF1ExclusiveFeatures(JsonObject configRoot) {
    List<String> features = new ArrayList<>();
    List<String> fqs = Arrays.asList(F1_TAG, F1E_TAG, F1ES_TAG);
    JsonArray fTypes = configRoot.getJsonArray(CFG_FTYPE_SECTION);
    for (int i = 0; i < fTypes.size(); i++) {
      JsonObject fType = fTypes.getJsonObject(i);
      String frequency = fType.getString(FTYPE_FREQ_TAG, "");
      String exclusive = fType.getString(FTYPE_EXCL_TAG, "");
      if (fqs.contains(frequency) && exclusive.equalsIgnoreCase(YES_VALUE)) {
        features.add(fType.getString(FTYPE_CODE_TAG));
      }
    }
    return features;
  }

  /*
   * Collects information about F1 exclusive features override from g2config JSON document.
   */
  private List<FeatureTypeOverride> extractF1FeatureTypeOverride(JsonObject configRoot) {
    List<FeatureTypeOverride> overRideFeats = new ArrayList<>();
    List<String> fqs = Arrays.asList(F1_TAG, F1E_TAG, F1ES_TAG);
    JsonArray fTypes = configRoot.getJsonArray(CFG_FBOVR_SECTION);
    for (int i = 0; i < fTypes.size(); i++) {
      JsonObject fType = fTypes.getJsonObject(i);
      String frequency = fType.getString(FTYPE_FREQ_TAG, "");
      String Exclusive = fType.getString(FTYPE_EXCL_TAG, "");
      if (fqs.contains(frequency) && Exclusive.toUpperCase().equals(YES_VALUE)) {
        FeatureTypeOverride fTypeOverride = new FeatureTypeOverride();
        fTypeOverride.setFType(fType.getString(FTYPE_CODE_TAG));
        fTypeOverride.setUType(fType.getString(UTYPE_CODE_TAG));
        overRideFeats.add(fTypeOverride);
      }
    }
    return overRideFeats;
  }

  private List<String> getMultiValueFeatures(JsonArray fTypeValues) {
    List<String> featList = new ArrayList<>();
    for (int i = 0; i < fTypeValues.size(); i++) {
      JsonObject fTypeObject = fTypeValues.getJsonObject(i);
      String featDesc = fTypeObject.getString(FEAT_DESC_TAG);
      featList.add(featDesc);
    }
    return featList;
  }

  private void collectLibFeatures(String fType, JsonArray fTypeValues, Map<Long, FeatData> libFeaturess, boolean includeGeneric) {
    for (int i = 0; i < fTypeValues.size(); i++) {
      JsonObject fTypeObject = fTypeValues.getJsonObject(i);
      Long featID = fTypeObject.getJsonNumber(LIB_FEAT_ID_TAG).longValue();
      if (featID != null) {
        if (includeGeneric || !isGeneric(fTypeObject))
        {
          String featDesc = fTypeObject.getString(FEAT_DESC_TAG);
          FeatData featData = new FeatData();
          featData.setFeature(fType);
          featData.setDescription(featDesc);
          libFeaturess.put(featID, featData);
        }
      }
    }
  }

  private boolean isGeneric(JsonObject featureObject) {
    JsonArray featDescs = featureObject.getJsonArray(FEAT_DESC_VALUES_TAG);
    for (int i = 0; i < featDescs.size(); i++) {
      JsonObject descriptionObject = featDescs.getJsonObject(i);
      if (descriptionObject.getString(CANDIDATE_CAP_REACHED_TAG).equals(Y_VALUE) || 
          descriptionObject.getString(SCORING_CAP_REACHED_TAG).equals(Y_VALUE)) {
        return true;
      }
    }
    return false;
  }

  private String getFeaturesForEntity(List<Long> feats, long entityID) throws ServiceExecutionException {
    try {
      return dbService.findEntitiesByFeatureIDs(feats, entityID, defaultLensID);
    } catch (RuntimeException e) {
      throw new ServiceExecutionException(e);
    }
  }

  private List<FeatData> checkForSharedFeatures(String jsonDoc, Map<Long, FeatData> features) throws ServiceExecutionException {
    List<FeatData> sharedFeat = new ArrayList<>();
    try {
      JsonReader reader = Json.createReader(new StringReader(jsonDoc));
      JsonArray featJson = reader.readArray();
      for (int i = 0; i < featJson.size(); i++) {
        Long id = featJson.getJsonObject(i).getJsonNumber(LIB_FEAT_ID_TAG).longValue();
        FeatData fd = features.get(id);
        if (fd != null) {
          sharedFeat.add(fd);
        }
      }
      return sharedFeat;
    } catch (RuntimeException e) {
      throw new ServiceExecutionException(e);
    }
  }

  private void reportScoring(long entityID, int lensID, RiskScorer riskScorer) throws ServiceExecutionException {
    try {
      dbService.postRiskScore(entityID, lensID, riskScorer.getDataQualityScore().toString(),
          riskScorer.getCollisionScore().toString(), riskScorer.getQueryRiskScore().toString(), riskScorer.getReason(),
          riskScorer.getQueryRiskReason());
    } catch (RuntimeException e) {
      throw new ServiceExecutionException(e);
    }
  }

  private void reportProgress() {
    processCount++;
    if (processCount % 1000 == 0) {
      Date current = new Date();
      String timeStamp = current.toInstant().toString();
      String filler = " ".repeat(timeStamp.length());
      System.out.println(timeStamp + " - Processed " + processCount + " records.");
      System.out.println(filler + " - Missing entity count for last 1000:    " + missingEntityCount);
      System.out.println(filler + " - Missing entity percent for last 1000:  " + missingEntityCount/10 + "%");
      missingEntityCount = 0; 
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

  private List<String> parseAndFormatCommaSeparatedString(String source) {
    List<String> parsedList = new ArrayList<>();
    if (source != null) {
      String parsedString[] = source.split(COMMA);
      for (int i = 0; i < parsedString.length; i++) {
        parsedList.add(parsedString[i].strip().toUpperCase());
      }
    }
    return parsedList;
  }

  /*
   * Parses query risk criteria string and populates a list of pojo objects containing the parsed information.
   */
  private List<QueryRiskData> parseAndProcessQueryRiskCriteria(String queryRiskCriteriaString) throws ServiceSetupException {
    List<QueryRiskData> retVal = new ArrayList<>();
    if (queryRiskCriteriaString != null && !queryRiskCriteriaString.isEmpty()) {
      // expecting string of format: +NAME+DOB:R;+NAME+ADDRESS:Y;+NAME+PHONE:Y;+NAME+SSN:R
      String criteria[] = queryRiskCriteriaString.split(";");
      for (int i = 0; i < criteria.length; i++) {
        String matchKeyCriterion[] = criteria[i].split(":");
        if (matchKeyCriterion.length != 2) {
          throw new ServiceSetupException("Badly formed query risk criteria: " + queryRiskCriteriaString);
        }
        String score = matchKeyCriterion[1].toUpperCase();
        // The score takes values R (for red) or Y (for yellow).
        if (!(score.equals(RiskScorer.R_VALUE) || score.equals(RiskScorer.Y_VALUE))) {
          throw new ServiceSetupException("Badly formed query risk criteria: " + queryRiskCriteriaString);
        }
        QueryRiskData queryRiskData = new QueryRiskData();
        queryRiskData.setScore(score);
        queryRiskData.setCriteriaString(matchKeyCriterion[0]);
        try {
          queryRiskData.setCriteriaList(parseMatchKey(matchKeyCriterion[0]));
        } catch (ParseException e) {
          throw new ServiceSetupException("Badly formed query risk criteria: " + queryRiskCriteriaString);
        }
        retVal.add(queryRiskData);
      }
    }
    return retVal;
  }

  /*
   * Parses match key of format +NAME+PHONE_NUMBER+ADDRESS-DOB.
   */
  private static List<String> parseMatchKey(String matchKey) throws ParseException {
    List<String> result = new ArrayList<>();

    int tokenBegin = matchKey.length();
    while (tokenBegin > 0) {
      int tokenEnd = tokenBegin;
      int lastPlus = matchKey.lastIndexOf('+', tokenEnd - 1);
      int lastMinus = matchKey.lastIndexOf('-', tokenEnd - 1);
      // This is for special cases of keys like this: +NAME+ADDRESS (Ambiguous)
      int lastParen = matchKey.lastIndexOf('(', tokenEnd - 1);
      tokenBegin = Math.max(lastParen, Math.max(lastPlus, lastMinus));
      if (tokenBegin < 0) {
        throw new ParseException("Badly formed match key: " + matchKey, tokenBegin);
      }
      result.add( matchKey.substring(tokenBegin, tokenEnd).trim());
    }
    return result;
  }

  public boolean isServiceUp() {
    return serviceUp;
  }

}
