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

import ballerina/auth;
import ballerina/reflect;

# Representation of the Authentication filter.
#
# + authConfig - Array of inbound authentication configurations
public type AuthnFilter object {

    public InboundAuthConfig[] authConfig;

    public function __init(InboundAuthConfig[] authConfig) {
        self.authConfig = authConfig;
    }

    # Request filter method which attempts to authenticated the request.
    #
    # + caller - Caller for outbound HTTP responses
    # + request - An inboud HTTP request message
    # + context - A filter context
    # + return - True if the filter succeeds
    public function filterRequest(Caller caller, Request request, FilterContext context) returns boolean {
        boolean authenticated = true;
        var resourceAuthConfig = getResourceAuthConfig(context);
        var resourceInboundAuthConfig = resourceAuthConfig["authConfig"];
        if (resourceInboundAuthConfig is InboundAuthConfig[]) {
            authenticated = handleAuthnRequest(resourceInboundAuthConfig, request);
        } else {
            authenticated = handleAuthnRequest(self.authConfig, request);
        }
        return isAuthnSuccessful(caller, authenticated);
    }

    public function filterResponse(Response response, FilterContext context) returns boolean {
        return true;
    }
};

function handleAuthnRequest(InboundAuthConfig[] inboundAuthConfig, Request request) returns boolean {
    foreach InboundAuthConfig authConfig in inboundAuthConfig {
        AuthnHandler authnHandler = authConfig.authnHandler;
        auth:AuthProvider[] authProviders = authConfig.authProviders;
        if (authProviders.length() > 0) {
            foreach auth:AuthProvider provider in authProviders {
                if (authnHandler.canHandle(request)) {
                    boolean authnSuccessful = authnHandler.handle(request);
                    if (authnSuccessful) {
                        // If one of the authenticators from the chain could successfully authenticate the user, it is not
                        // required to look through other providers. The authenticator chain is using "OR" combination of
                        // provider results.
                        return true;
                    }
                }
            }
        }
    }
    return false;
}

# Verifies if the authentication is successful. If not responds to the user.
#
# + caller - Caller for outbound HTTP responses
# + authenticated - Authorization status for the request
# + return - Authorization result to indicate if the filter can proceed(true) or not(false)
function isAuthnSuccessful(Caller caller, boolean authenticated) returns boolean {
    Response response = new;
    if (!authenticated) {
        response.statusCode = 401;
        response.setTextPayload("Authentication failure");
        var err = caller->respond(response);
        if (err is error) {
            panic err;
        }
        return false;
    }
    return true;
}
