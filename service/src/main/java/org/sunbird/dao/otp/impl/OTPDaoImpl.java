package org.sunbird.dao.otp.impl;

import java.sql.Timestamp;
import java.util.*;
import org.apache.commons.collections.CollectionUtils;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.dao.otp.OTPDao;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.keys.JsonKey;
import org.sunbird.logging.LoggerUtil;
import org.sunbird.request.RequestContext;
import org.sunbird.response.Response;
import org.sunbird.util.PropertiesCache;

public class OTPDaoImpl implements OTPDao {
  private final LoggerUtil logger = new LoggerUtil(OTPDaoImpl.class);
  private final CassandraOperation cassandraOperation = ServiceFactory.getInstance();
  private static final String TABLE_NAME = JsonKey.OTP;
  private static volatile OTPDao otpDao;

  public static OTPDao getInstance() {
    if (otpDao == null) {
      synchronized (OTPDaoImpl.class) {
        if (otpDao == null) {
          otpDao = new OTPDaoImpl();
        }
      }
    }
    return otpDao;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> getOTPDetails(String type, String key, RequestContext context) {
    Map<String, Object> request = new HashMap<>();
    request.put(JsonKey.TYPE, type);
    request.put(JsonKey.KEY, key);
    List<String> fields = new ArrayList<>();
    fields.add(JsonKey.TYPE);
    fields.add(JsonKey.KEY);
    fields.add(JsonKey.ATTEMPTED_COUNT);
    fields.add(JsonKey.CREATED_ON);
    fields.add(JsonKey.OTP);
    List<String> ttlFields = new ArrayList<>();
    ttlFields.add(JsonKey.OTP);
    Response result =
        cassandraOperation.getRecordWithTTLById(
            JsonKey.SUNBIRD, TABLE_NAME, request, ttlFields, fields, context);
    List<Map<String, Object>> otpMapList = (List<Map<String, Object>>) result.get(JsonKey.RESPONSE);
    if (CollectionUtils.isEmpty(otpMapList)) {
      return null;
    }
    return otpMapList.get(0);
  }

  @Override
  public void insertOTPDetails(String type, String key, String otp, RequestContext context) {
    Map<String, Object> request = new HashMap<>();
    request.put(JsonKey.TYPE, type);
    request.put(JsonKey.KEY, key);
    request.put(JsonKey.OTP, otp);
    request.put(JsonKey.ATTEMPTED_COUNT, 0);
    request.put(JsonKey.CREATED_ON, new Timestamp(Calendar.getInstance().getTimeInMillis()));
    String expirationInSeconds =
        PropertiesCache.getInstance().getProperty(JsonKey.SUNBIRD_OTP_EXPIRATION);
    int ttl = Integer.valueOf(expirationInSeconds);
    cassandraOperation.insertRecordWithTTL(JsonKey.SUNBIRD, TABLE_NAME, request, ttl, context);
  }

  @Override
  public void deleteOtp(String type, String key, RequestContext context) {
    Map<String, String> compositeKeyMap = new HashMap<>();
    compositeKeyMap.put(JsonKey.TYPE, type);
    compositeKeyMap.put(JsonKey.KEY, key);
    cassandraOperation.deleteRecord(JsonKey.SUNBIRD, TABLE_NAME, compositeKeyMap, context);
    logger.debug(context, "OTPDaoImpl:deleteOtp:otp deleted");
  }

  @Override
  public void updateAttemptCount(Map<String, Object> otpDetails, RequestContext context) {
    Map<String, Object> request = new HashMap<>();
    int ttl = (int) otpDetails.get("otp_ttl");
    otpDetails.remove("otp_ttl");
    request.putAll(otpDetails);
    request.remove(JsonKey.KEY);
    request.remove(JsonKey.TYPE);
    Map<String, Object> compositeKey = new HashMap<>();
    compositeKey.put(JsonKey.TYPE, otpDetails.get(JsonKey.TYPE));
    compositeKey.put(JsonKey.KEY, otpDetails.get(JsonKey.KEY));
    cassandraOperation.updateRecordWithTTL(
        JsonKey.SUNBIRD, TABLE_NAME, request, compositeKey, ttl, context);
  }


  /**
   * Inserts OTP details into the database.
   *
   * @param type              The type of the OTP (e.g., email, phone).
   * @param key               The key associated with the OTP.
   * @param otp               The OTP to be inserted.
   * @param contextType       The type of context associated with the OTP.
   * @param contextAttributes The attributes of the context associated with the OTP.
   * @param context           The request context.
   */
  @Override
  public void insertOTPDetailsV3(String type, String key, String otp, String contextType, String contextAttributes, RequestContext context) {
    // Create a map to store OTP details.
    Map<String, Object> request = new HashMap<>();
    // Populate the map with OTP details.
    request.put(JsonKey.TYPE, type);
    request.put(JsonKey.KEY, key);
    request.put(JsonKey.OTP, otp);
    request.put(JsonKey.CONTEXT_TYPE, contextType);
    request.put(JsonKey.CONTEXT_ATTRIBUTES, contextAttributes);
    request.put(JsonKey.ATTEMPTED_COUNT, 0);
    request.put(JsonKey.CREATED_ON, new Timestamp(Calendar.getInstance().getTimeInMillis()));
    // Retrieve OTP expiration time from properties and convert it to TTL.
    String expirationInSeconds = PropertiesCache.getInstance().getProperty(JsonKey.SUNBIRD_OTP_EXPIRATION);
    int ttl = Integer.valueOf(expirationInSeconds);
    // Insert the OTP details into the database with TTL (time-to-live).
    cassandraOperation.insertRecordWithTTL(JsonKey.SUNBIRD, TABLE_NAME, request, ttl, context);
  }


  /**
   * Retrieves OTP details from the database.
   *
   * @param type              The type of the OTP (e.g., email, phone).
   * @param key               The key associated with the OTP.
   * @param context           The request context.
   * @return A map containing OTP details, or null if no details are found.
   */
  @Override
  public Map<String, Object> getOTPDetailsV3(String type, String key, RequestContext context) {
    // Create a map to store the request parameters.
    Map<String, Object> request = new HashMap<>();
    request.put(JsonKey.TYPE, type);
    request.put(JsonKey.KEY, key);
    // Define the fields to retrieve from the database.
    List<String> fields = new ArrayList<>();
    fields.add(JsonKey.TYPE);
    fields.add(JsonKey.KEY);
    fields.add(JsonKey.ATTEMPTED_COUNT);
    fields.add(JsonKey.CREATED_ON);
    fields.add(JsonKey.OTP);
    fields.add(JsonKey.CONTEXT_TYPE);
    fields.add(JsonKey.CONTEXT_ATTRIBUTES);
    // Define the fields with TTL (time-to-live).
    List<String> ttlFields = new ArrayList<>();
    ttlFields.add(JsonKey.OTP);
    // Retrieve OTP details from the database.
    Response result = cassandraOperation.getRecordWithTTLById(JsonKey.SUNBIRD, TABLE_NAME, request, ttlFields, fields, context);
    // Extract the OTP details from the response.
    List<Map<String, Object>> otpMapList = (List<Map<String, Object>>) result.get(JsonKey.RESPONSE);
    // Check if OTP details are found.
    if (CollectionUtils.isEmpty(otpMapList)) {
      return null; // Return null if no details are found.
    }
    // Return the first OTP details found.
    return otpMapList.get(0);
  }

  /**
   * Overrides the method to update OTP (One-Time Password) details based on the provided parameters map.
   * This method delegates the update operation to the Cassandra database operation.
   *
   * @param parametersMap A map containing parameters for updating OTP details.
   *                      It may include information such as type, key, and context token.
   * @param requestContext The request context associated with the update operation,
   *                       providing contextual information for the update process.
   */
  @Override
  public void updateOTPDetailsV3(Map<String, Object> parametersMap, RequestContext requestContext) {
    // Delegate the update operation to the Cassandra database operation
    cassandraOperation.upsertRecord(JsonKey.SUNBIRD, TABLE_NAME, parametersMap, requestContext);
  }

}
