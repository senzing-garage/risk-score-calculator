package com.senzing.calculator.scoring.risk.service;

import java.util.List;

public class QueryRiskData {

  private String criteriaString;
  private List<String> criteriaList;
  private String score;

  public String getCriteriaString() {
    return criteriaString;
  }
  public void setCriteriaString(String criteriaString) {
    this.criteriaString = criteriaString;
  }
  public List<String> getCriteriaList() {
    return criteriaList;
  }
  public void setCriteriaList(List<String> criteriaList) {
    this.criteriaList = criteriaList;
  }
  public String getScore() {
    return score;
  }
  public void setScore(String score) {
    this.score = score;
  }

}
