package org.sunbird.dao.otp;

import java.util.List;
import java.util.Map;
import org.sunbird.request.RequestContext;

public interface OTPDao {

  /**
   * Fetch OTP details based on type (phone / email) and key.
   *
   * @param type Type of key (phone / email)
   * @param key Phone number or email address
   * @param context
   * @return OTP details
   */
  Map<String, Object> getOTPDetails(String type, String key, RequestContext context);

  /**
   * Insert OTP details for given type (phone / email) and key
   *
   * @param type Type of key (phone / email)
   * @param key Phone number or email address
   * @param otp Generated OTP
   * @param context
   */
  void insertOTPDetails(String type, String key, String otp, RequestContext context);

  /**
   * this method will be used to delete the Otp
   *
   * @param type
   * @param key
   * @param context
   */
  void deleteOtp(String type, String key, RequestContext context);

  void updateAttemptCount(Map<String, Object> otpDetails, RequestContext context);

  /**
   * Inserts OTP details into the database.
   *
   * @param type The type of the OTP (e.g., email, phone).
   * @param key The key associated with the OTP.
   * @param otp The OTP to be inserted.
   * @param contextType The type of context associated with the OTP.
   * @param contextAttributes The attributes of the context associated with the OTP.
   * @param context The request context.
   */
  void insertOTPDetailsV3(String type, String key, String otp, String contextType, String contextAttributes, RequestContext context);

  /**
   * Retrieves OTP details from the database.
   *
   * @param type The type of the OTP (e.g., email, phone).
   * @param key The key associated with the OTP.
   * @param context The request context.
   * @return A map containing OTP details.
   */
  Map<String, Object> getOTPDetailsV3(String type, String key, RequestContext context);

  /**
   * Updates the OTP (One-Time Password) details based on the provided parameters map.
   * This method is responsible for updating OTP details in the system.
   *
   * @param parametersMap A map containing parameters for updating OTP details.
   *                      It may include information such as type, key, and context token.
   * @param requestContext The request context associated with the update operation,
   *                       providing contextual information for the update process.
   */
  void updateOTPDetailsV3(Map<String, Object> parametersMap, RequestContext requestContext);

}
