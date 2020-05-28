package com.senzing.calculator.scoring.risk.service;

import java.util.ArrayList;
import java.util.List;

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
 */
public class RiskScorer {
  // Red quality.
  private boolean ambiguous;
  private boolean mutltipleDOBs;
  private boolean multipleExclusives;
  private boolean sharedF1Exclusives;
  // Green collision.
  private boolean noPossibleMatch;
  private boolean noSharedF1;
  // Green quality.
  private boolean oneAndOnlyOneDOB;
  private boolean oneAndOnlyOneSSN;
  private boolean sourceIMDM;

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
  private static final String ONE_SSN = "One and only one SSN";
  private static final String ONE_DOB = "One and only one DOB";

  // Green collision reasons.
  private static final String GREEN_DATA_QUALITY = "Green data quality";
  private static final String NO_SHARED_F1 = "No shared F1 types with other entities";
  private static final String NO_POSIBLE_MATCH = "No possible match";

  // Yellow quality reasons.
  private static final String NO_IMDM = "No iMDM record";
  private static final String NOT_ONE_SSN = "Not one and only one SSN";
  private static final String NOT_ONE_DOB = "Not one and only one DOB";
  
  // Yellow collision reasons.
  private static final String NOT_GREEN_DATA_QUALITY = "Data quality not green";
  private static final String SHARES_F1 = "Shares F1 types with other entities";
  private static final String POSIBLE_MATCH = "Possible match exists";
  private static final String MANUAL_YELLOW = "Manually flagged yellow";

  public RiskScorer() {
    ambiguous = false;
    mutltipleDOBs = false;
    multipleExclusives = false;
    sharedF1Exclusives = false;
    noPossibleMatch = false;
    noSharedF1 = false;
    oneAndOnlyOneDOB = false;
    oneAndOnlyOneSSN = false;
    sourceIMDM = false;
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

  public boolean hasMultipleExclusives() {
    return multipleExclusives;
  }

  public void setMultipleExclusives(boolean multipleExclusives) {
    this.multipleExclusives = multipleExclusives;
  }

  public boolean hasSharedF1Exclusives() {
    return sharedF1Exclusives;
  }

  public void setSharedF1Exclusives(boolean sharedF1Exclusives) {
    this.sharedF1Exclusives = sharedF1Exclusives;
  }

  public boolean hasOneAndOnlyOneDOB() {
    return oneAndOnlyOneDOB;
  }

  public void setOneAndOnlyOneDOB(boolean oneAndOnlyOneDOB) {
    this.oneAndOnlyOneDOB = oneAndOnlyOneDOB;
  }

  public boolean hasOneAndOnlyOneSSN() {
    return oneAndOnlyOneSSN;
  }

  public void setOneAndOnlyOneSSN(boolean oneAndOnlyOneSSN) {
    this.oneAndOnlyOneSSN = oneAndOnlyOneSSN;
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

  public boolean hasNoSharedF1() {
    return noSharedF1;
  }

  public void setNoSharedF1(boolean noSharedF1) {
    this.noSharedF1 = noSharedF1;
  }

  public RiskScore getScoreOverride() {
    return scoreOverride;
  }

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

  public RiskScore getDataQualityScore() {
    boolean isRed = ambiguous || multipleExclusives || sharedF1Exclusives || mutltipleDOBs;
    boolean isGreen = sourceIMDM && oneAndOnlyOneDOB && oneAndOnlyOneSSN;
    
    if (isRed) {
      return RiskScore.Red;
    } else if (isGreen) {
      return RiskScore.Green;
    } else {
      return RiskScore.Yellow;
    }
  }

  public RiskScore getCollisionScore() {
    RiskScore qualityScore = getDataQualityScore();
    boolean isRed = qualityScore == RiskScore.Red || scoreOverride == RiskScore.Red;
    boolean isGreen = qualityScore == RiskScore.Green && (scoreOverride != RiskScore.Red && scoreOverride != RiskScore.Yellow) && noSharedF1 && noPossibleMatch;

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
      if (multipleExclusives) {
        qualityReasons.add(MULTIPLE_EXCLUSIVES);
      }
      if (sharedF1Exclusives) {
        qualityReasons.add(SHARED_EXCLUSIVES);
      }
      collisionReasons.add(RED_DATA_QUALITY);
    } else if (qualityScore == RiskScore.Green) {
      if (sourceIMDM) {
        qualityReasons.add(IMDM_EXISTS);
      }
      if (oneAndOnlyOneDOB) {
        qualityReasons.add(ONE_SSN);
      }
      if (oneAndOnlyOneSSN) {
        qualityReasons.add(ONE_DOB);
      }
    } else {
      if (!sourceIMDM) {
        qualityReasons.add(NO_IMDM);
      }
      if (!oneAndOnlyOneDOB) {
        qualityReasons.add(NOT_ONE_DOB);
      }
      if (!oneAndOnlyOneSSN) {
        qualityReasons.add(NOT_ONE_SSN);
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
      if (noSharedF1) {
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
      if (!noSharedF1) {
        collisionReasons.add(SHARES_F1);
      }
    }

    JsonObjectBuilder rootObject = Json.createObjectBuilder();
    rootObject.add("Quality", Json.createArrayBuilder(qualityReasons).build());
    rootObject.add("Collision", Json.createArrayBuilder(collisionReasons).build());

    return rootObject.build().toString();
  }

  @Override
  public String toString() {
    RiskScore quality = getDataQualityScore();
    RiskScore collision = getCollisionScore();
    return "RiskScorer [quality=" + quality + ", collision=" + collision + ", ambiguous=" + ambiguous + ", mutltipleDOBs="
        + mutltipleDOBs + ", multipleExclusives=" + multipleExclusives + ", sharedF1Exclusives=" + sharedF1Exclusives
        + ", scoreOverride=" + scoreOverride + ", oneAndOnlyOneDOB="
        + oneAndOnlyOneDOB + ", oneAndOnlyOneSSN=" + oneAndOnlyOneSSN + ", sourceIMDM=" + sourceIMDM
        + ", noPossibleMatch=" + noPossibleMatch + ", noSharedF1=" + noSharedF1 + "]";
  }

  
}
