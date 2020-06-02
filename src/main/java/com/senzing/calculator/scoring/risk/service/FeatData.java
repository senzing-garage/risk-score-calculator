package com.senzing.calculator.scoring.risk.service;

public class FeatData {

  private String feature;
  private String description;

  public String getFeature() {
    return feature;
  }
  public void setFeature(String feature) {
    this.feature = feature;
  }
  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public String toString() {
    return "(" + feature + ":" + description + ")";
  }
}
