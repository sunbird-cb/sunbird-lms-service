package org.sunbird.service.otp;

import java.util.List;
import java.util.Map;

import org.sunbird.dao.otp.OTPDao;
import org.sunbird.dao.otp.impl.OTPDaoImpl;
import org.sunbird.request.RequestContext;
import org.sunbird.service.user.UserService;
import org.sunbird.service.user.impl.UserServiceImpl;
import org.sunbird.util.SMSTemplateProvider;

public class OTPService {

  private final OTPDao otpDao = OTPDaoImpl.getInstance();
  private final UserService userService = UserServiceImpl.getInstance();

  public Map<String, Object> getOTPDetails(String type, String key, RequestContext context) {
    return otpDao.getOTPDetails(type, key, context);
  }

  public void insertOTPDetails(String type, String key, String otp, RequestContext context) {
    otpDao.insertOTPDetails(type, key, otp, context);
  }

  public void deleteOtp(String type, String key, RequestContext context) {
    otpDao.deleteOtp(type, key, context);
  }

  /**
   * This method will return either email or phone value of user based on the asked type in request
   *
   * @param userId
   * @param type value can be email, phone, recoveryEmail, recoveryPhone , prevUsedEmail or
   *     prevUsedPhone
   * @return
   */
  public String getEmailPhoneByUserId(String userId, String type, RequestContext context) {
    return userService.getDecryptedEmailPhoneByUserId(userId, type, context);
  }

  public String getSmsBody(
      String templateFile, Map<String, String> smsTemplate, RequestContext requestContext) {
    return SMSTemplateProvider.getSMSBody(templateFile, smsTemplate, requestContext);
  }

  public void updateAttemptCount(Map<String, Object> otpDetails, RequestContext context) {
    otpDao.updateAttemptCount(otpDetails, context);
  }


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
  public void insertOTPDetailsV3(String type, String key, String otp, String contextType, String contextAttributes, RequestContext context) {
    // Delegate the insertion operation to the OTP DAO.
    otpDao.insertOTPDetailsV3(type, key, otp, contextType, contextAttributes, context);
  }


  /**
   * Retrieves OTP details from the database.
   *
   * @param type The type of the OTP (e.g., email, phone).
   * @param key The key associated with the OTP .
   * @param requestContext The request context.
   * @return A map containing OTP details.
   */
  public Map<String, Object> getOTPDetailsV3(String type, String key,RequestContext requestContext) {
    // Delegate the retrieval operation to the OTP DAO.
    return otpDao.getOTPDetailsV3(type, key, requestContext);
  }

  /**
   * Updates the OTP (One-Time Password) details based on the provided parameters map.
   * This method delegates the update operation to the OTP DAO (Data Access Object).
   */
  public void updateOTPDetailsV3(String keyspaceName, String tableName, Map<String, Object> request, Map<String, Object> compositeKey, RequestContext context) {
    // Delegate the update operation to the OTP DAO
    otpDao.updateOTPDetailsV3(keyspaceName, tableName, request, compositeKey,context);
  }

}
