package org.sunbird.actor.otp;

import akka.actor.ActorRef;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.exception.ResponseCode;
import org.sunbird.keys.JsonKey;
import org.sunbird.operations.ActorOperations;
import org.sunbird.request.Request;
import org.sunbird.request.RequestContext;
import org.sunbird.response.ClientErrorResponse;
import org.sunbird.response.Response;
import org.sunbird.service.otp.OTPService;
import org.sunbird.service.ratelimit.RateLimitService;
import org.sunbird.service.ratelimit.RateLimitServiceImpl;
import org.sunbird.telemetry.dto.TelemetryEnvKey;
import org.sunbird.util.ProjectUtil;
import org.sunbird.util.Util;
import org.sunbird.util.otp.OTPUtil;
import org.sunbird.util.ratelimit.OtpRateLimiter;
import org.sunbird.util.ratelimit.RateLimiter;

import javax.inject.Inject;
import javax.inject.Named;
import java.text.MessageFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OTPActor extends BaseActor {

  private final OTPService otpService = new OTPService();
  private final RateLimitService rateLimitService = new RateLimitServiceImpl();
  private static final String SUNBIRD_OTP_ALLOWED_ATTEMPT = "sunbird_otp_allowed_attempt";
  private final ObjectMapper mapper = new ObjectMapper();

  @Inject
  @Named("send_otp_actor")
  private ActorRef sendOTPActor;

  @Override
  public void onReceive(Request request) throws Throwable {
    Util.initializeContext(request, TelemetryEnvKey.USER);
    if (ActorOperations.GENERATE_OTP.getValue().equals(request.getOperation())) {
      generateOTP(request);
    } else if (ActorOperations.VERIFY_OTP.getValue().equals(request.getOperation())) {
      verifyOTP(request);
    } else if (ActorOperations.GENERATE_OTP_V3.getValue().equals(request.getOperation())) {
      generateOTPV3(request);
    }
    else if (ActorOperations.VERIFY_OTP_V3.getValue().equals(request.getOperation())) {
      verifyOTPV3(request);
    }
    else {
      onReceiveUnsupportedOperation();
    }
  }

  private void generateOTP(Request request) {
    logger.debug(request.getRequestContext(), "OTPActor:generateOTP method call start.");
    String type = (String) request.getRequest().get(JsonKey.TYPE);
    String key = (String) request.getRequest().get(JsonKey.KEY);
    String userId = (String) request.getRequest().get(JsonKey.USER_ID);
    if (StringUtils.isNotBlank(userId)) {
      key = otpService.getEmailPhoneByUserId(userId, type, request.getRequestContext());
      type = getType(type);
      logger.info(
          request.getRequestContext(),
          "OTPActor:generateOTP:getEmailPhoneByUserId: called for userId = "
              + userId
              + " ,key = "
              + OTPUtil.maskId(key, type));
    }

    rateLimitService.throttleByKey(
        key,
        type,
        new RateLimiter[] {OtpRateLimiter.HOUR, OtpRateLimiter.DAY},
        request.getRequestContext());

    String otp;
    Map<String, Object> details = otpService.getOTPDetails(type, key, request.getRequestContext());

    if (MapUtils.isEmpty(details)) {
      otp = OTPUtil.generateOTP(request.getRequestContext());
      logger.info(
          request.getRequestContext(),
          "OTPActor:generateOTP: new otp generated for Key = "
              + OTPUtil.maskId(key, type)
              + " & OTP = "
              + OTPUtil.maskOTP(otp));
      otpService.insertOTPDetails(type, key, otp, request.getRequestContext());
    } else {
      otp = (String) details.get(JsonKey.OTP);
      logger.info(
          request.getRequestContext(),
          "OTPActor:generateOTP: Re-issuing otp for Key = "
              + OTPUtil.maskId(key, type)
              + " & OTP = "
              + OTPUtil.maskOTP(otp));
    }
    logger.info(
        request.getRequestContext(),
        "OTPActor:sendOTP : Calling SendOTPActor for Key = " + OTPUtil.maskId(key, type));
    sendOTP(request, otp, key, request.getRequestContext());

    Response response = new Response();
    response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);
    sender().tell(response, self());
  }

  private void verifyOTP(Request request) {
    String type = (String) request.getRequest().get(JsonKey.TYPE);
    String key = (String) request.getRequest().get(JsonKey.KEY);
    String otpInRequest = (String) request.getRequest().get(JsonKey.OTP);

    String userId = (String) request.getRequest().get(JsonKey.USER_ID);
    if (StringUtils.isNotBlank(userId)) {
      key = otpService.getEmailPhoneByUserId(userId, type, request.getRequestContext());
      type = getType(type);
      logger.info(
          request.getRequestContext(),
          "OTPActor:verifyOTP:getEmailPhoneByUserId: called for userId = "
              + userId
              + " ,key = "
              + OTPUtil.maskId(key, type));
    }
    Map<String, Object> otpDetails =
        otpService.getOTPDetails(type, key, request.getRequestContext());

    if (MapUtils.isEmpty(otpDetails)) {
      logger.info(
          request.getRequestContext(),
          "OTP_VALIDATION_FAILED:OTPActor:verifyOTP: Details not found for Key = "
              + OTPUtil.maskId(key, type)
              + " type = "
              + type);
      ProjectCommonException.throwClientErrorException(ResponseCode.errorOTPExpired);
    }
    int remainingCount = getRemainingAttemptedCount(otpDetails);
    if (remainingCount < 0) {
      logger.info(
              request.getRequestContext(),
              "OTP_VALIDATION_FAILED:OTPActor:verifyOTP: Attempts Exceeded For The OTP = "
                      + OTPUtil.maskId(key, type)
                      + " type = "
                      + type);
      ProjectCommonException.throwClientErrorException(ResponseCode.errorOTPAttemptExceeded);
    }
    String otpInDB = (String) otpDetails.get(JsonKey.OTP);
    if (StringUtils.isBlank(otpInDB) || StringUtils.isBlank(otpInRequest)) {
      logger.info(
          request.getRequestContext(),
          "OTP_VALIDATION_FAILED : OTPActor:verifyOTP: Mismatch for Key = "
              + OTPUtil.maskId(key, type)
              + " otpInRequest = "
              + OTPUtil.maskOTP(otpInRequest)
              + " otpInDB = "
              + OTPUtil.maskOTP(otpInDB));
      ProjectCommonException.throwClientErrorException(ResponseCode.errorInvalidOTP);
    }

    if (otpInRequest.equals(otpInDB)) {
      logger.info(
          request.getRequestContext(),
          "OTP_VALIDATION_SUCCESS:OTPActor:verifyOTP: Verified successfully Key = "
              + OTPUtil.maskId(key, type));
      otpService.deleteOtp(type, key, request.getRequestContext());
      Response response = new Response();
      response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);
      sender().tell(response, self());
    } else {
      logger.info(
          request.getRequestContext(),
          "OTP_VALIDATION_FAILED: OTPActor:verifyOTP: Incorrect OTP Key = "
              + OTPUtil.maskId(key, type)
              + " otpInRequest = "
              + OTPUtil.maskOTP(otpInRequest)
              + " otpInDB = "
              + OTPUtil.maskOTP(otpInDB));
      handleMismatchOtp(type, key, otpDetails, request.getRequestContext());
    }
  }

  private void handleMismatchOtp(
      String type, String key, Map<String, Object> otpDetails, RequestContext context) {
    int remainingCount = getRemainingAttemptedCount(otpDetails);
    logger.info(
        context,
        "OTPActor:handleMismatchOtp: Key = "
            + OTPUtil.maskId(key, type)
            + ",remaining attempt is "
            + remainingCount);
    int attemptedCount = (int) otpDetails.get(JsonKey.ATTEMPTED_COUNT);
    otpDetails.put(JsonKey.ATTEMPTED_COUNT, attemptedCount + 1);
    otpService.updateAttemptCount(otpDetails, context);
    ProjectCommonException ex =
        new ProjectCommonException(
            ResponseCode.otpVerificationFailed,
            MessageFormat.format(
                ResponseCode.otpVerificationFailed.getErrorMessage(), remainingCount),
            ResponseCode.CLIENT_ERROR.getResponseCode());

    ClientErrorResponse response = new ClientErrorResponse();
    response.setException(ex);
    String MAX_ALLOWED_ATTEMPT = "maxAllowedAttempt";
    response
        .getResult()
        .put(
            MAX_ALLOWED_ATTEMPT,
            Integer.parseInt(ProjectUtil.getConfigValue(SUNBIRD_OTP_ALLOWED_ATTEMPT)));
    String REMAINING_ATTEMPT = "remainingAttempt";
    response.getResult().put(REMAINING_ATTEMPT, remainingCount);
    sender().tell(response, self());
  }

  private int getRemainingAttemptedCount(Map<String, Object> otpDetails) {
    int allowedAttempt = Integer.parseInt(ProjectUtil.getConfigValue(SUNBIRD_OTP_ALLOWED_ATTEMPT));
    int attemptedCount = (int) otpDetails.get(JsonKey.ATTEMPTED_COUNT);
    return (allowedAttempt - (attemptedCount + 1));
  }

  private void sendOTP(Request request, String otp, String key, RequestContext context) {
    Request sendOtpRequest = new Request();
    sendOtpRequest.setRequestContext(context);
    sendOtpRequest.getRequest().putAll(request.getRequest());
    sendOtpRequest.getRequest().put(JsonKey.KEY, key);
    sendOtpRequest.getRequest().put(JsonKey.OTP, otp);
    sendOtpRequest.setOperation(ActorOperations.SEND_OTP.getValue());
    try {
      sendOTPActor.tell(sendOtpRequest, self());
    } catch (Exception ex) {
      logger.error(context, "Exception while sending OTP", ex);
    }
  }

  private String getType(String type) {
    switch (type) {
      case JsonKey.PREV_USED_EMAIL:
      case JsonKey.RECOVERY_EMAIL:
      case JsonKey.EMAIL:
        return JsonKey.EMAIL;
      case JsonKey.PREV_USED_PHONE:
      case JsonKey.RECOVERY_PHONE:
      case JsonKey.PHONE:
        return JsonKey.PHONE;
      default:
        return null;
    }
  }


  /*
   * This method generates OTP for the provided request.
   */
  private void generateOTPV3(Request request) {
    logger.debug(request.getRequestContext(), "OTPActor:generateOTP method call start.");
    // Extract necessary parameters from the request.
    String type = (String) request.getRequest().get(JsonKey.TYPE);
    String key = (String) request.getRequest().get(JsonKey.KEY);
    String userId = (String) request.getRequest().get(JsonKey.USER_ID);
    String contextType = (String) request.getRequest().get(JsonKey.CONTEXT_TYPE);
    List<String> contextAttributesList = null;
    Object contextAttributesObj = request.get(JsonKey.CONTEXT_ATTRIBUTES);
    if (contextAttributesObj != null) {
      contextAttributesList = (new ObjectMapper()).convertValue(contextAttributesObj,
              new TypeReference<>() {
              });
    }
    String contextAttributes = contextAttributesList != null ? String.join(",", contextAttributesList) : "";
    // If userId is not blank, retrieve email or phone associated with it and update key and type accordingly.
    if (StringUtils.isNotBlank(userId)) {
      key = otpService.getEmailPhoneByUserId(userId, type, request.getRequestContext());
      type = getType(type);
      // Log the operation.
      logger.info(
              request.getRequestContext(),
              "OTPActor:generateOTP:getEmailPhoneByUserId: called for userId = "
                      + userId
                      + " ,key = "
                      + OTPUtil.maskId(key, type));
    }
    // Throttle OTP generation based on rate limit.
    rateLimitService.throttleByKey(
            key,
            type,
            new RateLimiter[]{OtpRateLimiter.HOUR, OtpRateLimiter.DAY},
            request.getRequestContext());
    // Get OTP details based on type, key, contextType, and contextAttributes.
    String otp;
    Map<String, Object> details = otpService.getOTPDetailsV3(type, key, request.getRequestContext());
    // If no details found, generate a new OTP, log its generation, insert the OTP details, and log the insertion.
    if (MapUtils.isEmpty(details)) {
      otp = OTPUtil.generateOTP(request.getRequestContext());
      logger.info(
              request.getRequestContext(),
              "OTPActor:generateOTP: new otp generated for Key = "
                      + OTPUtil.maskId(key, type)
                      + " & OTP = "
                      + OTPUtil.maskOTP(otp));
      otpService.insertOTPDetailsV3(type, key, otp, contextType, contextAttributes, request.getRequestContext());
    } else {
      // If details found, re-issue the OTP and log this action.
      otp = (String) details.get(JsonKey.OTP);
      logger.info(
              request.getRequestContext(),
              "OTPActor:generateOTP: Re-issuing otp for Key = "
                      + OTPUtil.maskId(key, type)
                      + " & OTP = "
                      + OTPUtil.maskOTP(otp));
    }
    logger.info(
            request.getRequestContext(),
            "OTPActor:sendOTP : Calling SendOTPActor for Key = " + OTPUtil.maskId(key, type));
    // Call the sendOTP method.
    sendOTP(request, otp, key, request.getRequestContext());
    // Create a response indicating success and send it.
    Response response = new Response();
    response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);
    sender().tell(response, self());
  }


  /**
   * Verifies the OTP (One-Time Password) provided in the request.
   * If the OTP is valid, updates OTP details and sends a success response;
   * otherwise, handles the mismatch or invalid OTP.
   * @param request The request containing OTP-related information.
   */
  private void verifyOTPV3(Request request) throws JsonProcessingException {
    // Extracting parameters from the request
    String type = (String) request.getRequest().get(JsonKey.TYPE);
    String key = (String) request.getRequest().get(JsonKey.KEY);
    String otpInRequest = (String) request.getRequest().get(JsonKey.OTP);

    String userId = (String) request.getRequest().get(JsonKey.USER_ID);
    // If userId is present, get the key associated with the userId
    if (StringUtils.isNotBlank(userId)) {
      key = otpService.getEmailPhoneByUserId(userId, type, request.getRequestContext());
      type = getType(type);
      logger.info(
              request.getRequestContext(),
              "OTPActor:verifyOTP:getEmailPhoneByUserId: called for userId = "
                      + userId
                      + " ,key = "
                      + OTPUtil.maskId(key, type));
    }
    // Retrieving OTP details from the service
    Map<String, Object> otpDetails =
            otpService.getOTPDetailsV3(type, key, request.getRequestContext());
    // If OTP details not found, throw error
    if (MapUtils.isEmpty(otpDetails)) {
      logger.info(
              request.getRequestContext(),
              "OTP_VALIDATION_FAILED:OTPActor:verifyOTP: Details not found for Key = "
                      + OTPUtil.maskId(key, type)
                      + " type = "
                      + type);
      ProjectCommonException.throwClientErrorException(ResponseCode.errorOTPExpired);
    }
    // Check if the number of remaining attempts is exceeded
    int remainingCount = getRemainingAttemptedCount(otpDetails);
    if (remainingCount < 0) {
      logger.info(
              request.getRequestContext(),
              "OTP_VALIDATION_FAILED:OTPActor:verifyOTP: Attempts Exceeded For The OTP = "
                      + OTPUtil.maskId(key, type)
                      + " type = "
                      + type);
      ProjectCommonException.throwClientErrorException(ResponseCode.errorOTPAttemptExceeded);
    }
    // Retrieve OTP from the database
    String otpInDB = (String) otpDetails.get(JsonKey.OTP);
    // Check if OTPs are blank
    if (StringUtils.isBlank(otpInDB) || StringUtils.isBlank(otpInRequest)) {
      logger.info(
              request.getRequestContext(),
              "OTP_VALIDATION_FAILED : OTPActor:verifyOTP: Mismatch for Key = "
                      + OTPUtil.maskId(key, type)
                      + " otpInRequest = "
                      + OTPUtil.maskOTP(otpInRequest)
                      + " otpInDB = "
                      + OTPUtil.maskOTP(otpInDB));
      ProjectCommonException.throwClientErrorException(ResponseCode.errorInvalidOTP);
    }
    // If OTPs match, update OTP details and send success response
    if (otpInRequest.equals(otpInDB)) {
      logger.info(
              request.getRequestContext(),
              "OTP_VALIDATION_SUCCESS:OTPActor:verifyOTP: Verified successfully Key = "
                      + OTPUtil.maskId(key, type));
      Map<String, Object> compostieKeyMap = new HashMap<>();
      compostieKeyMap.put(JsonKey.TYPE, type);
      compostieKeyMap.put(JsonKey.KEY, key);
      Map<String,Object> requestParamsMap =  new HashMap<>();
      requestParamsMap.put(JsonKey.CONTEXT_TYPE, otpDetails.get(JsonKey.CONTEXT_TYPE));
      requestParamsMap.put(JsonKey.CONTEXT_ATTRIBUTES,otpDetails.get(JsonKey.CONTEXT_ATTRIBUTES));
      String contextToken = generateToken(mapper.writeValueAsString(requestParamsMap));
      requestParamsMap.put(JsonKey.CONTEXT_TOKEN, contextToken);
      otpService.updateOTPDetailsV3(JsonKey.SUNBIRD,JsonKey.OTP,requestParamsMap,compostieKeyMap, request.getRequestContext());
      Response response = new Response();
      response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);
      response.put(JsonKey.CONTEXT_TOKEN,contextToken);
      sender().tell(response, self());
    } else {
      logger.info(
              request.getRequestContext(),
              "OTP_VALIDATION_FAILED: OTPActor:verifyOTP: Incorrect OTP Key = "
                      + OTPUtil.maskId(key, type)
                      + " otpInRequest = "
                      + OTPUtil.maskOTP(otpInRequest)
                      + " otpInDB = "
                      + OTPUtil.maskOTP(otpInDB));
      handleMismatchOtp(type, key, otpDetails, request.getRequestContext());
    }
  }


  public static String generateToken(String contextFields) {
    long currentTimeMillis = System.currentTimeMillis();
    long expirationTimeMillis = currentTimeMillis + Long.parseLong(ProjectUtil.getConfigValue(JsonKey.OTP_EXPIRATION_TIME_TOKEN)); 
    return Jwts.builder()
            .setSubject(contextFields)
            .setExpiration(new Date(expirationTimeMillis))
            .signWith(SignatureAlgorithm.HS256, ProjectUtil.getConfigValue(JsonKey.OTP_VALIDATION_SECRET_KEY))
            .compact();
  }
}
