package com.senzing.calculator.scoring.risk.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObjectBuilder;

/**
 * This class collects and scores risk based on following criteria:
 * Rules: 
 * Red Collision (entity has any of the following):
 *                Entity has a Red status for Data Quality
 *                Manual flag for Red
 *                Forced Un-merge (This is not currently scored)
 * 
 * Red Data Quality (entity has any of the following):
 *                Is Ambiguous or has Ambiguous relationship
 *                Those having multiple F1E or F1ES of the same type – requires RXSSN change
 *                Those with a F1E or F1ES that is shared with other entities 
 *                Same entity with multiple DOBs
 *
 * Green Collision (entity has all of the following):
 *                Entity has a Green status for Data Quality
 *                Entity has no shared F1
 *                Entity has no possible matches
 * 
 * Green Data Quality (entity has all of the following):
 *                Entity has at least 1 IMDM records
 *                Entity has one and only one SSN
 *                Entity has one and only one DOB
 * 
 * Modification 06/03/2020
 * Green Data Quality (entity has all of the following):
 *                Entity has at least 1 IMDM records
 *                Entity has none or one SSN
 *                Entity has one and only one DOB
 *                Entity has address
 */
public class RiskScorer {
  // Red quality.
  private boolean ambiguous;
  private boolean mutltipleDOBs;
  private Map<String, List<String>> multipleExclusives;
  private List<FeatData> sharedExclusives;
  // Green collision.
  private boolean noPossibleMatch;
  private List<FeatData> sharedF1s;
  // Green quality.
  private boolean oneAndOnlyOneDOB;
  private boolean oneOrLessSSN;
  private boolean sourceIMDM;
  private boolean oneOrMoreAddress;

  // Override for data quality scores
  private RiskScore scoreOverride;

  // Red quality reasons.
  private static final String IS_AMBIGUOUS = "Ambiguous relationships";
  private static final String MULTIPLE_DOBS = "More than one DOB";
  private static final String MULTIPLE_EXCLUSIVES = "More than one F1E or F1ES of the same type";
  private static final String SHARED_EXCLUSIVES = "F1E or F1ES shared with other entities";

  // Red collision reasons.
  private static final String RED_DATA_QUALITY = "Red data quality";
  private static final String MANUAL_RED = "Manually flagged red";

  // Green quality reasons.
  private static final String IMDM_EXISTS = "At least 1 iMDM record";
  private static final String ONE_SSN = "One or less SSN";
  private static final String ONE_DOB = "One and only one DOB";
  private static final String ONE_OR_MORE_ADDRESS = "One or more addresses";

  // Green collision reasons.
  private static final String GREEN_DATA_QUALITY = "Green data quality";
  private static final String NO_SHARED_F1 = "No shared F1 types with other entities";
  private static final String NO_POSIBLE_MATCH = "No possible match";

  // Yellow quality reasons.
  private static final String NO_IMDM = "No iMDM record";
  private static final String NOT_ONE_SSN = "More than one SSN";
  private static final String NOT_ONE_DOB = "Not one and only one DOB";
  private static final String NO_ADDRESS = "No address";
  
  // Yellow collision reasons.
  private static final String NOT_GREEN_DATA_QUALITY = "Data quality not green";
  private static final String SHARES_F1 = "Shares F1 types with other entities";
  private static final String POSIBLE_MATCH = "Possible match exists";
  private static final String MANUAL_YELLOW = "Manually flagged yellow";

  private static final int MAX_SUBSTRING_SIZE = 250;

  public RiskScorer() {
    ambiguous = false;
    mutltipleDOBs = false;
    multipleExclusives = new HashMap<>();
    noPossibleMatch = false;
    sharedF1s = new ArrayList<>();
    oneAndOnlyOneDOB = false;
    oneOrLessSSN = false;
    sourceIMDM = false;
    oneOrMoreAddress = false;
    sharedExclusives = new ArrayList<>();
  }

  public boolean isAmbiguous() {
    return ambiguous;
  }

  public void setAmbiguous(boolean ambiguous) {
    this.ambiguous = ambiguous;
  }

  public boolean hasMutltipleDOBs() {
    return mutltipleDOBs;
  }

  public void setMutltipleDOBs(boolean mutltipleDOBs) {
    this.mutltipleDOBs = mutltipleDOBs;
  }

  public Map<String, List<String>> getMultipleExclusives() {
    return multipleExclusives;
  }

  public void addMultipleExclusives(String type, List<String> values) {
    this.multipleExclusives.put(type, values);
  }

  public boolean hasOneAndOnlyOneDOB() {
    return oneAndOnlyOneDOB;
  }

  public void setOneAndOnlyOneDOB(boolean oneAndOnlyOneDOB) {
    this.oneAndOnlyOneDOB = oneAndOnlyOneDOB;
  }

  public boolean hasOneOrLessSSN() {
    return oneOrLessSSN;
  }

  public void setOneOrLessSSN(boolean oneOrLessSSN) {
    this.oneOrLessSSN = oneOrLessSSN;
  }

  public boolean hasSourceIMDM() {
    return sourceIMDM;
  }

  public void setSourceIMDM(boolean hasIMDMdsrc) {
    this.sourceIMDM = hasIMDMdsrc;
  }

  public boolean hasNoPossibleMatch() {
    return noPossibleMatch;
  }

  public void setNoPossibleMatch(boolean noPossibleMatch) {
    this.noPossibleMatch = noPossibleMatch;
  }

  public boolean hasOneOrMoreAddress() {
    return oneOrMoreAddress;
  }

  public void setOneOrMoreAddress(boolean oneOrMoreAddress) {
    this.oneOrMoreAddress = oneOrMoreAddress;
  }

  public List<FeatData>  getSharedF1() {
    return sharedF1s;
  }

  public void addSharedF1s(List<FeatData> sharedF1s) {
    this.sharedF1s.addAll(sharedF1s);
  }

  public RiskScore getScoreOverride() {
    return scoreOverride;
  }

  public List<FeatData> getSharedExclusives() {
    return sharedExclusives;
  }

  public void addSharedExclusives(List<FeatData> sharedExclusives) {
    this.sharedExclusives.addAll(sharedExclusives);
  }

  /**
   * This method can override the data quality score.  It only deteriorates the score; Green can be changed to
   * Yellow or Red and Yellow can be changed to red.  If value of Green is passed in with the data quality score
   * of Red or Yellow, the value is ignored and score left unchanged.
   * 
   * @param override
   */
  public void setScoreOverride(String override) {
    // The precedence of overrides is: Red > Yellow > Green.
    String overrideUpper = override.toUpperCase();
    if (overrideUpper.equals("RED")) {
      scoreOverride = RiskScore.Red;
    } else if (overrideUpper.equals("YELLOW") && scoreOverride != RiskScore.Red) {
      scoreOverride = RiskScore.Yellow;
    } else if (overrideUpper.equals("GREEN") && (scoreOverride != RiskScore.Yellow && scoreOverride != RiskScore.Red)) {
      scoreOverride = RiskScore.Green;
    }
  }

  /**
   * Returns what the data quality score is (Red, Yellow or Green)
   * 
   * @return Score (Red, Yellow or Green)
   */
  public RiskScore getDataQualityScore() {
    boolean isRed = ambiguous || !multipleExclusives.isEmpty() || !sharedExclusives.isEmpty() || mutltipleDOBs;
    boolean isGreen = sourceIMDM && oneAndOnlyOneDOB && oneOrLessSSN && oneOrMoreAddress;
    
    if (isRed) {
      return RiskScore.Red;
    } else if (isGreen) {
      return RiskScore.Green;
    } else {
      return RiskScore.Yellow;
    }
  }

  /**
   * Returns what the collision score is (Red, Yellow or Green)
   * 
   * @return Score (Red, Yellow or Green)
   */
  public RiskScore getCollisionScore() {
    RiskScore qualityScore = getDataQualityScore();
    boolean isRed = qualityScore == RiskScore.Red || scoreOverride == RiskScore.Red;
    boolean isGreen = qualityScore == RiskScore.Green && (scoreOverride != RiskScore.Red && scoreOverride != RiskScore.Yellow) && sharedF1s.isEmpty() && noPossibleMatch;

    if (isRed) {
      return RiskScore.Red;
    } else if (isGreen) {
      return RiskScore.Green;
    } else {
      return RiskScore.Yellow;
    }
  }

  /**
   * Gives a document containing reasons for scores.
   * 
   * @return JSON document containing the reasons
   * 
   * @throws JSONException
   */
  public String getReason() {
    List<String> qualityReasons = new ArrayList<>();
    List<String> collisionReasons = new ArrayList<>();

    RiskScore qualityScore = getDataQualityScore();
    if (qualityScore == RiskScore.Red) {
      if (ambiguous) {
        qualityReasons.add(IS_AMBIGUOUS);
      }
      if (mutltipleDOBs) {
        qualityReasons.add(MULTIPLE_DOBS);
      }
      if (!multipleExclusives.isEmpty()) {
        String me = multipleExclusives.toString();
        if (me.length() > MAX_SUBSTRING_SIZE) {
          // limit the string size to avoid failed db insert
          me = getShortenedFeatureMap(multipleExclusives);
        }
        qualityReasons.add(MULTIPLE_EXCLUSIVES + " - " + me);
      }
      if (!sharedExclusives.isEmpty()) {
        String se = sharedExclusives.toString();
        if (se.length() > MAX_SUBSTRING_SIZE) {
          // limit the string size to avoid failed db insert
          se = getShortenedFetaDataList(sharedExclusives);
        }
        qualityReasons.add(SHARED_EXCLUSIVES + " - " + se);
      }
      collisionReasons.add(RED_DATA_QUALITY);
    } else if (qualityScore == RiskScore.Green) {
      if (sourceIMDM) {
        qualityReasons.add(IMDM_EXISTS);
      }
      if (oneAndOnlyOneDOB) {
        qualityReasons.add(ONE_DOB);
      }
      if (oneOrLessSSN) {
        qualityReasons.add(ONE_SSN);
      }
      if (oneOrMoreAddress) {
        qualityReasons.add(ONE_OR_MORE_ADDRESS);
      }
    } else {
      if (!sourceIMDM) {
        qualityReasons.add(NO_IMDM);
      }
      if (!oneAndOnlyOneDOB) {
        qualityReasons.add(NOT_ONE_DOB);
      }
      if (!oneOrLessSSN) {
        qualityReasons.add(NOT_ONE_SSN);
      }
      if (!oneOrMoreAddress) {
        qualityReasons.add(NO_ADDRESS);
      }
    }

    RiskScore collisionScore = getCollisionScore();
    if (collisionScore == RiskScore.Red) {
      if (scoreOverride == RiskScore.Red) {
        collisionReasons.add(MANUAL_RED);
      }
    } else if  (collisionScore == RiskScore.Green) {
      collisionReasons.add(GREEN_DATA_QUALITY);
      if (noPossibleMatch) {
        collisionReasons.add(NO_POSIBLE_MATCH);
      }
      if (sharedF1s.isEmpty()) {
        collisionReasons.add(NO_SHARED_F1);
      }
    } else {
      if (scoreOverride == RiskScore.Yellow) {
        collisionReasons.add(MANUAL_YELLOW);
      }
      if (qualityScore != RiskScore.Green) {
        collisionReasons.add(NOT_GREEN_DATA_QUALITY);
      }
      if (!noPossibleMatch) {
        collisionReasons.add(POSIBLE_MATCH);
      }
      if (!sharedF1s.isEmpty()) {
        String sf1 = sharedF1s.toString();
        if (sf1.length() > MAX_SUBSTRING_SIZE) {
          // limit the string size to avoid failed db insert
          sf1 = getShortenedFetaDataList(sharedF1s);
        }
        collisionReasons.add(SHARES_F1 + " - " + sf1);
      }
    }

    JsonObjectBuilder rootObject = Json.createObjectBuilder();
    rootObject.add("Quality", Json.createArrayBuilder(qualityReasons).build());
    rootObject.add("Collision", Json.createArrayBuilder(collisionReasons).build());

    return rootObject.build().toString();
  }

  private String getShortenedFeatureMap(Map<String, List<String>> featMap) {
    Map<String, List<String>> shortened = new HashMap<>();
    for (String key : featMap.keySet()) {
      List<String> values = featMap.get(key);
      if (shortened.toString().length() + key.length() + values.toString().length() > MAX_SUBSTRING_SIZE) {
        break;
      }
      shortened.put(key, values);
    }
    return shortened.toString();
  }

  private String getShortenedFetaDataList(List<FeatData> featData) {
    List<FeatData> fdList = new ArrayList<>();
    for (FeatData fd : featData) {
      if (fdList.toString().length() + fd.toString().length() > MAX_SUBSTRING_SIZE) {
        break;
      }
      fdList.add(fd);
    }
    return fdList.toString();
  }
}
