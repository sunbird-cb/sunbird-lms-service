package org.sunbird.user.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.collections.CollectionUtils;
import org.json.JSONObject;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.request.Request;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.learner.util.Util;
import org.sunbird.models.user.User;
import org.sunbird.user.profile.ProfileUtil;
import org.sunbird.user.service.IUserProfileService;
import org.sunbird.user.service.UserProfileReadService;
import org.sunbird.validator.user.JsonSchemaValidator;


import java.util.List;
import java.util.Map;

import static org.sunbird.common.request.orgvalidator.BaseOrgRequestValidator.ERROR_CODE;

public class UserProfileService implements IUserProfileService {

    private LoggerUtil logger = new LoggerUtil(UserProfileReadService.class);
    private static final String SCHEMA = "profileDetails.json";

    private CassandraOperation cassandraOperation = ServiceFactory.getInstance();




    @Override
    public void validateProfile(Request userRequest) {

        if (userRequest!=null && userRequest.getRequest().get(JsonKey.PROFILE_DETAILS)!=null) {
            try{
                JsonSchemaValidator.loadSchemas();
                String userProfile = ProfileUtil.mapper.writeValueAsString(userRequest.getRequest().get(JsonKey.PROFILE_DETAILS));
                JSONObject obj = new JSONObject(userProfile);
                JsonSchemaValidator.validate(SCHEMA, obj);
                ((Map)userRequest.getRequest().get(JsonKey.PROFILE_DETAILS)).put(JsonKey.MANDATORY_FIELDS_EXISTS, obj.get(JsonKey.MANDATORY_FIELDS_EXISTS));

            } catch (Exception e){
                logger.error("validate profile exception:",e);
                throw new ProjectCommonException(
                        "INVALID_PAYLOAD",
                        e.getMessage(),
                        ERROR_CODE);
            }
        }
    }

    @Override
    public void updateProfile(String uuid, Map<String, Object> profileFields) {

        Util.DbInfo usrDbInfo = Util.dbInfoMap.get(JsonKey.USER_DB);
        Response record =
                cassandraOperation.getRecordById(
                        usrDbInfo.getKeySpace(), usrDbInfo.getTableName(), uuid, null);
        List<Map<String, Object>> userList = (List<Map<String, Object>>) record.get(JsonKey.RESPONSE);
        if (CollectionUtils.isNotEmpty(userList)) {
            try{
                Map<String, Object> userMap = userList.get(0);
                JsonNode profileMap = (JsonNode)userMap.get(JsonKey.PROFILE_DETAILS);
                //update the profile object with request fields
                for(Map.Entry entry: profileFields.entrySet()){
                    replaceField((ObjectNode)profileMap, entry.getKey().toString(), entry.getValue().toString());
                }
                System.out.println("replaced profile ==> "+profileMap);
                Map m = ProfileUtil.mapper.convertValue(profileMap, Map.class);
                User user = new User();
                user.setId(uuid);
                user.setProfileDetails(m);

                Map requestMap = ProfileUtil.mapper.convertValue(user, Map.class);

                Response response =
                        cassandraOperation.updateRecord(
                                usrDbInfo.getKeySpace(),
                                usrDbInfo.getTableName(),
                                requestMap,
                                null);

                if (((String) response.get(JsonKey.RESPONSE)).equalsIgnoreCase(JsonKey.SUCCESS)) {
                    logger.info(null, "UserProfile: updateUser: User profile update successfully");
                } else {
                    logger.info(null, "UserProfile: updateUser: User profile update failure");
                }
            }catch(Exception e){
                logger.error("UserProfile: updateUser: User profile update failure", e);

            }


        }

    }


    private static void replaceField(ObjectNode parent, String fieldName, String newValue) {
        if (parent.has(fieldName)) {
            parent.put(fieldName, newValue);
        }
        parent.fields().forEachRemaining(entry -> {
            JsonNode entryValue = entry.getValue();
            if (entryValue.isArray()) {
                for (int i = 0; i < entryValue.size(); i++) {
                    if (entry.getValue().get(i).isObject())
                        replaceField((ObjectNode) entry.getValue().get(i), fieldName, newValue);
                }
            } else if (entryValue.isObject()) {
                replaceField((ObjectNode) entry.getValue(), fieldName, newValue);
            }
        });
    }


}
