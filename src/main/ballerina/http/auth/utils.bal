// Copyright (c) 2018 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/log;
import ballerina/reflect;

# Auth annotation module.
const string ANN_MODULE = "ballerina/http";
# Resource level annotation name.
const string RESOURCE_ANN_NAME = "ResourceConfig";
# Service level annotation name.
const string SERVICE_ANN_NAME = "ServiceConfig";
# Authentication header name.
public const string AUTH_HEADER = "Authorization";
# Basic authentication scheme.
public const string AUTH_SCHEME_BASIC = "Basic";
# Bearer authentication scheme.
public const string AUTH_SCHEME_BEARER = "Bearer";

# Inbound authentication schemes.
public type InboundAuthScheme BASIC_AUTH|JWT_AUTH;

# Outbound authentication schemes.
public type OutboundAuthScheme BASIC_AUTH|OAUTH2|JWT_AUTH;

# Basic authentication scheme.
public const BASIC_AUTH = "BASIC_AUTH";
# OAuth2 authentication scheme.
public const OAUTH2 = "OAUTH2";
# JWT authentication scheme.
public const JWT_AUTH = "JWT_AUTH";

# Extracts the basic authentication header value from the request.
#
# + req - Request instance
# + return - Value of the basic authentication header, or nil if not found
public function extractBasicAuthHeaderValue(Request req) returns string? {
    // extract authorization header
    var headerValue = trap req.getHeader(AUTH_HEADER);
    if (headerValue is string) {
        return headerValue;
    } else {
        string reason = headerValue.reason();
        log:printDebug(function () returns string {
            return "Error in retrieving header " + AUTH_HEADER + ": " + reason;
        });
    }
}

# Tries to retrieve the annotation value for authentication hierarchically - first from the resource level and then
# from the service level, if it is not there in the resource level.
#
# + context - `FilterContext` instance
# + return - `ServiceResourceAuth` instance if its defined, else nil
function getServiceResourceAuthConfig(FilterContext context) returns ServiceResourceAuth? {
    // get authn details from the resource level
    ServiceResourceAuth? resourceLevelAuthAnn = getAuthAnnotation(ANN_MODULE, RESOURCE_ANN_NAME,
        reflect:getResourceAnnotations(context.serviceRef, context.resourceName));
    ServiceResourceAuth? serviceLevelAuthAnn = getAuthAnnotation(ANN_MODULE, SERVICE_ANN_NAME,
        reflect:getServiceAnnotations(context.serviceRef));
    // check if authentication is enabled
    boolean resourceSecured = isServiceResourceSecured(resourceLevelAuthAnn, serviceLevelAuthAnn);
    // if resource is not secured, no need to check further
    if (!resourceSecured) {
        return ();
    }

    // check if auth providers are given at resource level
    if (resourceLevelAuthAnn is ServiceResourceAuth) {
        return resourceLevelAuthAnn;
    } else {
        // no auth providers found in resource level, try in service level
        if (serviceLevelAuthAnn is ServiceResourceAuth) {
            return serviceLevelAuthAnn;
        }
    }
}

# Retrieves and return the auth annotation with the given module name, annotation name and annotation data.
#
# + annotationModule - Annotation module name
# + annotationName - Annotation name
# + annData - Array of annotationData instances
# + return - `ServiceResourceAuth` instance if its defined, else nil
function getAuthAnnotation(string annotationModule, string annotationName, reflect:annotationData[] annData) returns ServiceResourceAuth? {
    if (annData.length() == 0) {
        return ();
    }
    reflect:annotationData? authAnn = ();
    foreach var ann in annData {
        if (ann.name == annotationName && ann.moduleName == annotationModule) {
            authAnn = ann;
            break;
        }
    }
    if (authAnn is reflect:annotationData) {
        if (annotationName == RESOURCE_ANN_NAME) {
            HttpResourceConfig resourceConfig = <HttpResourceConfig>authAnn.value;
            return resourceConfig.auth;
        } else if (annotationName == SERVICE_ANN_NAME) {
            HttpServiceConfig serviceConfig = <HttpServiceConfig>authAnn.value;
            return serviceConfig.auth;
        }
    }
}

# Check for the service or the resource is secured by evaluating the enabled flag configured by the user.
#
# + resourceLevelAuthAnn - Resource level auth annotations
# + serviceLevelAuthAnn - Service level auth annotations
# + return - Whether the service or resource secured or not
function isServiceResourceSecured(ServiceResourceAuth? resourceLevelAuthAnn,
                                  ServiceResourceAuth? serviceLevelAuthAnn) returns boolean {
    boolean secured = true;
    if (resourceLevelAuthAnn is ServiceResourceAuth) {
        secured = resourceLevelAuthAnn.enabled;
    } else if (serviceLevelAuthAnn is ServiceResourceAuth) {
        secured = serviceLevelAuthAnn.enabled;
    }
    return secured;
}
