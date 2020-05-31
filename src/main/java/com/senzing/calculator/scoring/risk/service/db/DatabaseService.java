package com.senzing.calculator.scoring.risk.service.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.senzing.listener.senzing.service.exception.ServiceExecutionException;

public class DatabaseService {

  private Connection connection;
  private String upsertQuery;
  private PreparedStatement postStatement;
  private PreparedStatement deleteStatement;

  private static final String MYSQL_TYPE = "mysql";

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
      upsertQuery = UPSERT_QUERY_MYSQL;
    } else {
      upsertQuery = UPSERT_QUERY;
    }
    postStatement = connection.prepareStatement(upsertQuery);
    deleteStatement = connection.prepareStatement(DELETE_QUERY);
  }

  public void postRiskScore(long entityID, int lensID, String qualityScore, String collisionScore, String reason)
      throws ServiceExecutionException {

    try {
      if (qualityScore == null && collisionScore == null) {
        int index = 1;
        deleteStatement.setLong(index++, entityID);
        deleteStatement.setInt(index++, lensID);
        deleteStatement.execute();
      } else {
        populateUpsertStatement(postStatement, entityID, lensID, qualityScore, collisionScore, reason);
        postStatement.execute();
      }
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

  public void refreshStatements() throws SQLException {
    postStatement.close();
    deleteStatement.close();
    postStatement = connection.prepareStatement(upsertQuery);
    deleteStatement = connection.prepareStatement(DELETE_QUERY);
  }

  private PreparedStatement populateUpsertStatement(PreparedStatement statement, long entityID, int lensID, String qualityScore, String collisionScore,
      String reason) throws SQLException {
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
