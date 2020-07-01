package com.senzing.calculator.scoring.risk.service;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.senzing.calculator.scoring.risk.service.db.DatabaseService;
import com.senzing.listener.senzing.service.exception.ServiceExecutionException;
import com.senzing.listener.senzing.service.exception.ServiceSetupException;
import com.senzing.listener.senzing.service.g2.G2Service;

import mockit.Mock;
import mockit.MockUp;

public class RiskScorerTest {

  @Test
  public void testSettersAndGetters() throws ServiceExecutionException, ServiceSetupException {

    RiskScorer riskScorer = new RiskScorer();
    riskScorer.setAmbiguous(true);
    riskScorer.setMutltipleDOBs(true);
    riskScorer.setOneAndOnlyOneDOB(true);
    riskScorer.setOneOrLessSSN(true);
    riskScorer.setOneOrMoreAddress(true);
    riskScorer.setNoPossibleMatch(true);
    riskScorer.setScoreOverride("Yellow");
    riskScorer.addMultipleExclusives("SSN", new ArrayList<String>(Arrays.asList("123-45-6789", "987-65-4321")));
    riskScorer.addSharedExclusives(getSharedFeatures());
    riskScorer.addSharedF1s(getSharedFeatures());
    riskScorer.addQueryRisk("+ADDRESS+NAME", "Y");

    assertThat(riskScorer.isAmbiguous(), is(equalTo(true)));
    assertThat(riskScorer.hasMutltipleDOBs(), is(equalTo(true)));
    assertThat(riskScorer.hasOneAndOnlyOneDOB(), is(equalTo(true)));
    assertThat(riskScorer.hasOneOrMoreAddress(), is(equalTo(true)));
    assertThat(riskScorer.hasOneOrLessSSN(), is(equalTo(true)));
    assertThat(riskScorer.getTrustedSources().size(), is(equalTo(0)));
    assertThat(riskScorer.hasNoPossibleMatch(), is(equalTo(true)));
    assertThat(riskScorer.getScoreOverride(), is(equalTo(RiskScore.Yellow)));
    assertThat(riskScorer.getMultipleExclusives().size(), is(equalTo(1)));
    assertThat(riskScorer.getSharedExclusives().size(), is(equalTo(2)));
    assertThat(riskScorer.getSharedF1().size(), is(equalTo(2)));
    assertThat(riskScorer.getQueryRiskScore(), is(equalTo(RiskScore.Yellow)));
    assertThat(riskScorer.getQueryRiskReason(), is(equalTo("[+ADDRESS+NAME]")));

    riskScorer.setAmbiguous(false);
    riskScorer.setMutltipleDOBs(false);
    riskScorer.setOneAndOnlyOneDOB(false);
    riskScorer.setOneOrLessSSN(false);
    riskScorer.setOneOrMoreAddress(false);
    riskScorer.setNoPossibleMatch(false);
    riskScorer.addTrustedSource("GoodSource");
    riskScorer.setScoreOverride("Red");
    riskScorer.addQueryRisk("+NAME+SSN", "R");

    assertThat(riskScorer.isAmbiguous(), is(equalTo(false)));
    assertThat(riskScorer.hasMutltipleDOBs(), is(equalTo(false)));
    assertThat(riskScorer.hasOneAndOnlyOneDOB(), is(equalTo(false)));
    assertThat(riskScorer.hasOneOrMoreAddress(), is(equalTo(false)));
    assertThat(riskScorer.hasOneOrLessSSN(), is(equalTo(false)));
    assertThat(riskScorer.getTrustedSources().size(), is(equalTo(1)));
    assertThat(riskScorer.hasNoPossibleMatch(), is(equalTo(false)));
    assertThat(riskScorer.getScoreOverride(), is(equalTo(RiskScore.Red)));
    assertThat(riskScorer.getQueryRiskScore(), is(equalTo(RiskScore.Red)));
    assertThat(riskScorer.getQueryRiskReason(), is(equalTo("[+NAME+SSN]")));
  }

  @Test
  public void testSetScoreOverrideOnlyChangesToWorseScore() throws ServiceExecutionException, ServiceSetupException {

    RiskScorer riskScorer = new RiskScorer();
    riskScorer.setScoreOverride("Green");
    assertThat(riskScorer.getScoreOverride(), is(equalTo(RiskScore.Green)));

    riskScorer.setScoreOverride("Yellow");
    assertThat(riskScorer.getScoreOverride(), is(equalTo(RiskScore.Yellow)));

    riskScorer.setScoreOverride("Green");
    assertThat(riskScorer.getScoreOverride(), is(equalTo(RiskScore.Yellow)));

    riskScorer.setScoreOverride("Red");
    assertThat(riskScorer.getScoreOverride(), is(equalTo(RiskScore.Red)));

    riskScorer.setScoreOverride("Yellow");
    assertThat(riskScorer.getScoreOverride(), is(equalTo(RiskScore.Red)));

    riskScorer.setScoreOverride("Green");
    assertThat(riskScorer.getScoreOverride(), is(equalTo(RiskScore.Red)));
  }

  @Test
  public void testGreenDataQualityAndCollision() throws ServiceExecutionException, ServiceSetupException {
    RiskScorer riskScorer = new RiskScorer();
    riskScorer.setAmbiguous(false);
    riskScorer.setMutltipleDOBs(false);
    riskScorer.setOneAndOnlyOneDOB(true);
    riskScorer.setOneOrLessSSN(true);
    riskScorer.setOneOrMoreAddress(true);
    riskScorer.addTrustedSource("GoodSource");
    riskScorer.setNoPossibleMatch(true);

    assertThat(riskScorer.getDataQualityScore(), is(equalTo(RiskScore.Green)));
    assertThat(riskScorer.getCollisionScore(), is(equalTo(RiskScore.Green)));
    assertThat(riskScorer.getReason(), is(containsString("At least 1 trusted source record")));
    assertThat(riskScorer.getReason(), is(containsString("One and only one DOB")));
    assertThat(riskScorer.getReason(), is(containsString("One or less SSN")));
    assertThat(riskScorer.getReason(), is(containsString("One or more addresses")));
    assertThat(riskScorer.getReason(), is(containsString("Green data quality")));
    assertThat(riskScorer.getReason(), is(containsString("No possible match")));
    assertThat(riskScorer.getReason(), is(containsString("No shared F1 types with other entities")));
  }

  @Test
  public void testRedDataQualityAndCollisions() throws ServiceExecutionException, ServiceSetupException {
    RiskScorer riskScorer = new RiskScorer();
    riskScorer.setAmbiguous(true);
    riskScorer.setMutltipleDOBs(true);
    riskScorer.setOneAndOnlyOneDOB(false);
    riskScorer.setOneOrLessSSN(false);
    riskScorer.setOneOrMoreAddress(true);
    riskScorer.setNoPossibleMatch(false);
    riskScorer.addMultipleExclusives("SSN", new ArrayList<String>(Arrays.asList("123-45-6789", "987-65-4321")));
    riskScorer.addSharedExclusives(getSharedFeatures());
    riskScorer.addSharedF1s(getSharedFeatures());

    assertThat(riskScorer.getDataQualityScore(), is(equalTo(RiskScore.Red)));
    assertThat(riskScorer.getCollisionScore(), is(equalTo(RiskScore.Red)));
    assertThat(riskScorer.getReason(), is(containsString("Ambiguous relationships")));
    assertThat(riskScorer.getReason(), is(containsString("More than one DOB")));
    assertThat(riskScorer.getReason(), is(containsString("More than one F1E or F1ES of the same type - {SSN=[123-45-6789, 987-65-4321]}")));
    assertThat(riskScorer.getReason(), is(containsString("F1E or F1ES shared with other entities - [(CustID:some data), (MemberID:some other data)]")));
    assertThat(riskScorer.getReason(), is(containsString("Red data quality")));
  }

  @Test
  public void testYellowDataQualityAndCollisions() throws ServiceExecutionException, ServiceSetupException {
    RiskScorer riskScorer = new RiskScorer();
    riskScorer.setAmbiguous(false);
    riskScorer.setMutltipleDOBs(false);
    riskScorer.setOneAndOnlyOneDOB(false);
    riskScorer.setOneOrLessSSN(false);
    riskScorer.setOneOrMoreAddress(true);
    riskScorer.setNoPossibleMatch(true);
    riskScorer.addSharedF1s(getSharedFeatures());

    assertThat(riskScorer.getDataQualityScore(), is(equalTo(RiskScore.Yellow)));
    assertThat(riskScorer.getCollisionScore(), is(equalTo(RiskScore.Yellow)));
    assertThat(riskScorer.getReason(), is(containsString("No record from trusted source")));
    assertThat(riskScorer.getReason(), is(containsString("Not one and only one DOB")));
    assertThat(riskScorer.getReason(), is(containsString("More than one SSN")));
    assertThat(riskScorer.getReason(), is(containsString("Shares F1 types with other entities - [(CustID:some data), (MemberID:some other data)]")));
    assertThat(riskScorer.getReason(), is(containsString("Data quality not green")));
  }

  private List<FeatData> getSharedFeatures() {
    List<FeatData> sharedExclusives = new ArrayList<>();
    FeatData fd = new FeatData();
    fd.setDescription("some data");
    fd.setFeature("CustID");
    sharedExclusives.add(fd);
    fd = new FeatData();
    fd.setDescription("some other data");
    fd.setFeature("MemberID");
    sharedExclusives.add(fd);
    return sharedExclusives;
  }
}
