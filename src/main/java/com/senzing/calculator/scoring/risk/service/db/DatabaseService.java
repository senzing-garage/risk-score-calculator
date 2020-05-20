package com.senzing.calculator.scoring.risk.service.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.senzing.listener.senzing.service.exception.ServiceExecutionException;

public class DatabaseService {

  Connection connection;
  String dbType;

  private static final String MYSQL_TYPE = "mysql";
  private static final String OTHER_TYPE = "other";

  private final static String DELETE_QUERY = "DELETE FROM RES_RISK_SCORE WHERE RES_ENT_ID = ? AND LENS_ID = ?";
  private final static String UPSERT_QUERY = "INSERT INTO RES_RISK_SCORE (RES_ENT_ID, LENS_ID, QUALITY_STATE, COLLISION_STATE, REASON) "
                                                     + "VALUES (?, ?, ?, ?, ?) "
                                                     + "ON CONFLICT (RES_ENT_ID, LENS_ID) DO UPDATE SET QUALITY_STATE = ?, COLLISION_STATE = ?, REASON = ?";
  private final static String UPSERT_QUERY_MYSQL = "INSERT INTO RES_RISK_SCORE(RES_ENT_ID, LENS_ID, QUALITY_STATE, COLLISION_STATE, REASON) "
                                                     + "VALUES (?, ?, ?, ?, ?) "
                                                     + "ON DUPLICATE KEY UPDATE QUALITY_STATE = ?, COLLISION_STATE = ?, REASON = ?";

  public DatabaseService() {
  }

  public void init(String url) throws SQLException {
    connection = DriverManager.getConnection(url);
    connection.setAutoCommit(false);
    if (url.contains(MYSQL_TYPE)) {
      dbType = MYSQL_TYPE;
    } else {
      dbType = OTHER_TYPE;
    }
    
  }

  public void postRiskScore(long entityID, int lensID, String qualityScore, String collisionScore, String reason)
      throws ServiceExecutionException {

    PreparedStatement postStatement = null;

    try {
      if (qualityScore == null && collisionScore == null) {
        postStatement = connection.prepareStatement(DELETE_QUERY);
        int index = 1;
        postStatement.setLong(index++, entityID);
        postStatement.setInt(index++, lensID);
      } else {
        String query = dbType.equals(MYSQL_TYPE) ? UPSERT_QUERY_MYSQL : UPSERT_QUERY;
        postStatement = createUpsertStatement(query, entityID, lensID, qualityScore, collisionScore, reason);
      }
      postStatement.execute();
    } catch (SQLException e) {
      try {
        connection.rollback();
      } catch (SQLException e1) {
        throw new ServiceExecutionException(e1);
      }
    }
    try {
      connection.commit();
    } catch (SQLException e) {
      throw new ServiceExecutionException(e);
    }
  }

  private PreparedStatement createUpsertStatement(String query, long entityID, int lensID, String qualityScore, String collisionScore,
      String reason) throws SQLException {
    PreparedStatement statement = connection.prepareStatement(query);
    int index = 1;
    statement.setLong(index++, entityID);
    statement.setInt(index++, lensID);
    statement.setString(index++, qualityScore);
    statement.setString(index++, collisionScore);
    statement.setString(index++, reason);
    statement.setString(index++, qualityScore);
    statement.setString(index++, collisionScore);
    statement.setString(index++, reason);
    return statement;
  }
}
