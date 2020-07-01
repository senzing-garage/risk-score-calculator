package com.senzing.calculator.scoring.risk.service.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.senzing.listener.senzing.service.exception.ServiceExecutionException;

public class DatabaseService {

  private Connection connection;
  private String upsertQuery;
  private PreparedStatement postStatement;
  private PreparedStatement deleteStatement;

  private static final int DEFAULT_BATCH_SIZE = 20;

  private static final String MYSQL_TYPE = "mysql";
  private static final String DOUBLE_QUOTE = "\"";
  private static final String LIB_FEAT_ID_FIELD = "LIB_FEAT_ID";
  private static final String USAGE_TYPE_FIELD = "USAGE_TYPE";
  private static final String RES_ENT_ID_FIELD = "RES_ENT_ID";

  private final static String DELETE_QUERY = "DELETE FROM RES_RISK_SCORE WHERE RES_ENT_ID = ? AND LENS_ID = ?";
  private final static String UPSERT_QUERY = "INSERT INTO RES_RISK_SCORE (RES_ENT_ID, LENS_ID, QUALITY_STATE, COLLISION_STATE, QUERY_STATE, REASON, QUERY_REASON) "
                                                     + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                                                     + "ON CONFLICT (RES_ENT_ID, LENS_ID) DO UPDATE SET QUALITY_STATE = ?, COLLISION_STATE = ?, QUERY_STATE = ?, REASON = ?, QUERY_REASON = ?";
  private final static String UPSERT_QUERY_MYSQL = "INSERT INTO RES_RISK_SCORE (RES_ENT_ID, LENS_ID, QUALITY_STATE, COLLISION_STATE, QUERY_STATE, REASON, QUERY_REASON) "
                                                     + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                                                     + "ON DUPLICATE KEY UPDATE QUALITY_STATE = ?, COLLISION_STATE = ?, QUERY_STATE = ?, REASON = ?, QUERY_REASON = ?";
  private final static String GET_ENTITIES_BY_FEATURES_QUERY = "SELECT LIB_FEAT_ID,UTYPE_CODE,RES_ENT_ID FROM RES_FEAT_EKEY "
                                                               + "WHERE LENS_ID=? AND RES_ENT_ID!=? AND LIB_FEAT_ID IN ";


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

  /**
   * Posts a risk score into RES_RISK_SCORE table. It inserts if no record existing and updates otherwise.
   * 
   * @param entityID ID for the entity being scored
   * @param lensID Lens ID being scored
   * @param qualityScore Score for data quality
   * @param collisionScore Score for collision
   * @param reason Explanation for the scores
   * 
   * @throws ServiceExecutionException
   */
  public void postRiskScore(long entityID, int lensID, String qualityScore, String collisionScore, String queryScore,
      String reason, String queryReason)
      throws ServiceExecutionException {

    try {
      if (qualityScore == null && collisionScore == null) {
        int index = 1;
        deleteStatement.setLong(index++, entityID);
        deleteStatement.setInt(index++, lensID);
        deleteStatement.execute();
      } else {
        populateUpsertStatement(postStatement, entityID, lensID, qualityScore, collisionScore, queryScore, reason, queryReason);
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

  /**
   * Gets res entities for feature IDs. One entity ID is used to exclude it from the selection.
   * 
   * @param ids List of feature IDs
   * @param entityID Entity ID to exclude from selection. Set to 0 if none should be excluded.
   * @param lensID Lens ID
   * 
   * @return JSON document of the entities returned. The format is [{"LIB_FEAT_ID":12,"USAGE_TYPE":"","RES_ENT_ID":1234}, ...]
   * 
   * @throws ServiceExecutionException
   */
  public String findEntitiesByFeatureIDs(List<Long> ids, long entityID, int lensID) throws ServiceExecutionException {
    return findEntitiesByFeatureIDs(ids, entityID, lensID, DEFAULT_BATCH_SIZE);
  }

  /**
   * Gets res entities for feature IDs. One entity ID is used to exclude it from the selection.
   * 
   * @param ids List of feature IDs
   * @param entityID Entity ID to exclude from selection. Set to 0 if none should be excluded.
   * @param lensID Lens ID
   * @param batchSize Defines the size of IDs queried in one statement.
   * 
   * @return JSON document of the entities returned. The format is [{"LIB_FEAT_ID":12,"USAGE_TYPE":"","RES_ENT_ID":1234}, ...]
   * 
   * @throws ServiceExecutionException
   */
  public String findEntitiesByFeatureIDs(List<Long> ids, long entityID, int lensID, int batchSize) throws ServiceExecutionException {
    if (batchSize <= 0) {
      return "[]";
    }

    // Split the IDs up into smaller batches.  This is to limit the number prepared statements needed. Too high of
    // a number can be detrimental to performance.
    List<List<Long>> idBatches = new ArrayList<>();
    for (int i = 0; i < ids.size()/10+1; i++) {
      List<Long> batch = ids.subList(i*10, Math.min((i+1)*10, ids.size()));
      if (batch.size() > 0) {
        idBatches.add(batch);
      }
    }

    StringBuilder returnVal = new StringBuilder();
    for (List<Long> batch : idBatches) {
      PreparedStatement selectStmt = null;
      try {
        String inClause = buildInClause(batch.size());
        selectStmt = connection.prepareStatement(GET_ENTITIES_BY_FEATURES_QUERY + inClause);
        int index = 1;
        selectStmt.setInt(index++, lensID);
        selectStmt.setLong(index++, entityID);
        for (Long id : batch) {
          selectStmt.setLong(index++, id);
        }

        ResultSet result = selectStmt.executeQuery();

        List<String> rowResults = new ArrayList<>();
        while (result.next()) {
          StringBuilder rowString = new StringBuilder();
          rowString.append('{');
          rowString.append(quoted(LIB_FEAT_ID_FIELD)).append(':').append(result.getString(1)).append(',');
          rowString.append(quoted(USAGE_TYPE_FIELD)).append(':').append(quoted(result.getString(2))).append(',');
          rowString.append(quoted(RES_ENT_ID_FIELD)).append(':').append(result.getString(3));
          rowString.append('}');
          rowResults.add(rowString.toString());
          returnVal.append(result.toString());
        }

        return rowResults.toString();
      } catch (SQLException e) {
        throw new ServiceExecutionException(e);
      } finally {
        if (selectStmt != null) {
          try {
            selectStmt.close();
          } catch (SQLException e) {e.printStackTrace();}
        }
      }
    }
    return returnVal.toString();
  }

  String quoted(String value) {
    return DOUBLE_QUOTE + value + DOUBLE_QUOTE;
  }

  private String buildInClause(int size) {
    StringBuilder inClause = new StringBuilder().append('(').append('?').append(",?".repeat(size-1)).append(')');
    return inClause.toString();
  }

  private PreparedStatement populateUpsertStatement(PreparedStatement statement, long entityID, int lensID, String qualityScore, String collisionScore,
      String queryScore, String reason, String queryReason) throws SQLException {
    int index = 1;
    statement.setLong(index++, entityID);
    statement.setInt(index++, lensID);
    statement.setString(index++, qualityScore);
    statement.setString(index++, collisionScore);
    statement.setString(index++, queryScore);
    statement.setString(index++, reason);
    statement.setString(index++, queryReason);
    statement.setString(index++, qualityScore);
    statement.setString(index++, collisionScore);
    statement.setString(index++, queryScore);
    statement.setString(index++, reason);
    statement.setString(index++, queryReason);
    return statement;
  }
}
