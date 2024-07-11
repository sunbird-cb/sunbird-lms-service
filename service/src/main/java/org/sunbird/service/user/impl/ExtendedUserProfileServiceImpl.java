package org.sunbird.service.user.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.exception.ResponseCode;
import org.sunbird.keys.JsonKey;
import org.sunbird.request.Request;
import org.sunbird.service.user.ExtendedUserProfileService;
import org.sunbird.util.ProjectUtil;
import org.sunbird.util.user.UserExtendedProfileSchemaValidator;
import java.util.Map;

public class ExtendedUserProfileServiceImpl implements ExtendedUserProfileService {
    private static final String SCHEMA = "profileDetails.json";
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void validateProfile(Request userRequest) {
        if (userRequest!=null && userRequest.get(JsonKey.PROFILE_DETAILS)!=null) {
            try{
                Map<String, Object> profileDetails = (Map<String, Object>) userRequest.get(JsonKey.PROFILE_DETAILS);
                if (profileDetails.get(JsonKey.PERSONAL_DETAILS) != null) {
                    Map<String, Object> personalDetails = (Map<String, Object>) profileDetails.get(JsonKey.PERSONAL_DETAILS);
                    if (StringUtils.isNotBlank((String) personalDetails.get(JsonKey.FIRST_NAME)) || StringUtils.isNotBlank((String) personalDetails.get(JsonKey.FIRST_NAME_LOWER_CASE))) {
                        String firstName = (String) personalDetails.get(JsonKey.FIRST_NAME);
                        if (StringUtils.isBlank(firstName)) {
                            firstName = (String) personalDetails.get(JsonKey.FIRST_NAME_LOWER_CASE);
                        }
                        personalDetails.put(JsonKey.FIRST_NAME_LOWER_CASE, formatFirstName(firstName));
                        personalDetails.remove(JsonKey.FIRST_NAME);
                    }
                }
                String userProfile = mapper.writeValueAsString(userRequest.getRequest().get(JsonKey.PROFILE_DETAILS));
                JSONObject obj = new JSONObject(userProfile);
                UserExtendedProfileSchemaValidator.validate(SCHEMA, obj);
                ((Map)userRequest.getRequest().get(JsonKey.PROFILE_DETAILS)).put(JsonKey.MANDATORY_FIELDS_EXISTS, obj.get(JsonKey.MANDATORY_FIELDS_EXISTS));
            } catch (Exception e){
                e.printStackTrace();
                //TODO - Need to find proper error message
                throw new ProjectCommonException(
                        ResponseCode.extendUserProfileNotLoaded,
                        ResponseCode.extendUserProfileNotLoaded.getErrorMessage(),
                        ResponseCode.extendUserProfileNotLoaded.getResponseCode());
            }
        }
    }

    private String formatFirstName(String firstName) {
        String[] words = firstName.split("\\s+");
        StringBuilder modifiedFirstName = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                modifiedFirstName.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return modifiedFirstName.toString().trim();
    }
}