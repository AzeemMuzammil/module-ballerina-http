// Copyright (c) 2022 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import ballerina/constraint;
import ballerina/http;
import ballerina/test;

final http:Client clientValidationTestClient = check new("http://localhost:" + clientDatabindingTestPort2.toString());

type ValidationPerson record {|
    @constraint:String {
        maxLength: 5,
        minLength: 2
    }
    string name;
    @constraint:Int {
        minValueExclusive: 5
    }
    int age;
|};

@constraint:Float {
    minValue: 5.2
}
type Price float;

@constraint:Array {
    length: 2
}
type Weight decimal[];

type ValidationItem record {|
    Weight weight;
|};

service /validation on clientDBBackendListener {
    resource function get getRecord(string name, int age) returns ValidationPerson {
        ValidationPerson person = {name: name, age: age};
        return person;
    }

    resource function get getPrice(float price) returns float {
        return price;
    }

    resource function get getWeight() returns ValidationItem {
        return {weight:[2.3, 5.4]};
    }

    resource function get get3Weight() returns ValidationItem {
        return {weight:[2.3, 5.4, 6.7]};
    }

    resource function post getRecord(@http:Payload ValidationPerson person) returns string {
        return person.name;
    }

    resource function post getPrice(@http:Payload Price price) returns float {
        return price;
    }

    resource function post getWeight(@http:Payload ValidationItem item) returns ValidationItem {
        return item;
    }
}

@test:Config {}
function testConstraintRecordStringField() returns error? {
    ValidationPerson person = check clientValidationTestClient->get("/validation/getRecord?name=wso2&age=15");
    test:assertEquals(person.name, "wso2");
}

@test:Config {}
function testConstraintRecordStringFieldMinLengthError() {
    ValidationPerson|error err = clientValidationTestClient->get("/validation/getRecord?name=a&age=15");
    if err is http:PayloadValidationError {
        test:assertEquals(err.message(), "payload validation failed: Validation failed for 'minLength' constraint(s).");
    } else {
        test:assertFail(msg = "Found unexpected output type");
    }
}

@test:Config {}
function testConstraintRecordStringFieldMaxLengthError() {
    ValidationPerson|error err = clientValidationTestClient->get("/validation/getRecord?name=ballerina&age=15");
    if err is http:PayloadValidationError {
        test:assertEquals(err.message(), "payload validation failed: Validation failed for 'maxLength' constraint(s).");
    } else {
        test:assertFail(msg = "Found unexpected output type");
    }
}

@test:Config {}
function testConstraintRecordIntFieldMaxLengthError() {
    ValidationPerson|error err = clientValidationTestClient->get("/validation/getRecord?name=ballerina&age=5");
    if err is error {
        test:assertEquals(err.message(),
            "payload validation failed: Validation failed for 'maxLength','minValueExclusive' constraint(s).");
    } else {
        test:assertFail(msg = "Found unexpected output type");
    }
}

@test:Config {}
function testConstraintTypeFloatField() returns error? {
    Price price = check clientValidationTestClient->get("/validation/getPrice?price=7.4");
    test:assertEquals(price, 7.4f);
}


@test:Config {}
function testConstraintTypeFloatFieldMinValueError() {
    Price|error err = clientValidationTestClient->get("/validation/getPrice?price=5.1");
    if err is error {
        test:assertEquals(err.message(), "payload validation failed: Validation failed for 'minValue' constraint(s).");
    } else {
        test:assertFail(msg = "Found unexpected output type");
    }
}

@test:Config {}
function testConstraintTypeArrayField() returns error? {
    ValidationItem item = check clientValidationTestClient->get("/validation/getWeight");
    test:assertEquals(item.weight, [2.3d, 5.4d]);
}

@test:Config {}
function testConstraintTypeArrayFieldError(){
    ValidationItem|error err = clientValidationTestClient->get("/validation/get3Weight");
    if err is error {
        test:assertEquals(err.message(), "payload validation failed: Validation failed for 'length' constraint(s).");
    } else {
        test:assertFail(msg = "Found unexpected output type");
    }
}

@test:Config {}
function testResourceConstraintRecordStringField() returns error? {
    ValidationPerson p = {name: "wso2", age: 7};
    string name = check clientValidationTestClient->post("/validation/getRecord", p);
    test:assertEquals(name, "wso2");
}

@test:Config {}
function testResourceConstraintRecordStringFieldMinLengthError() {
    ValidationPerson p = {name: "a", age: 7};
    string|error response = clientValidationTestClient->post("/validation/getRecord", p);
    if response is http:ClientRequestError {
        test:assertEquals(response.detail().statusCode, 400, msg = "Found unexpected output");
        assertErrorHeaderValue(response.detail().headers[CONTENT_TYPE], TEXT_PLAIN);
        test:assertEquals(response.detail().body,
        "payload validation failed: Validation failed for '$.name:minLength' constraint(s).");
    } else {
        test:assertFail(msg = "Found unexpected output type");
    }
}

@test:Config {}
function testResourceConstraintRecordStringFieldMaxLengthError() {
    ValidationPerson p = {name: "ballerina", age: 15};
    string|error response = clientValidationTestClient->post("/validation/getRecord", p);
    if response is http:ClientRequestError {
        test:assertEquals(response.detail().statusCode, 400, msg = "Found unexpected output");
        assertErrorHeaderValue(response.detail().headers[CONTENT_TYPE], TEXT_PLAIN);
        test:assertEquals(response.detail().body,
        "payload validation failed: Validation failed for '$.name:maxLength' constraint(s).");
    } else {
        test:assertFail(msg = "Found unexpected output type");
    }
}

@test:Config {}
function testResourceConstraintRecordIntFieldMaxLengthError() {
    ValidationPerson p = {name: "ballerina", age: 5};
    string|error response = clientValidationTestClient->post("/validation/getRecord", p);
    if response is http:ClientRequestError {
        test:assertEquals(response.detail().statusCode, 400, msg = "Found unexpected output");
        assertErrorHeaderValue(response.detail().headers[CONTENT_TYPE], TEXT_PLAIN);
        test:assertEquals(response.detail().body,
        "payload validation failed: Validation failed for '$.age:minValueExclusive','$.name:maxLength' constraint(s).");
    } else {
        test:assertFail(msg = "Found unexpected output type");
    }
}

@test:Config {}
function testResourceConstraintTypeFloatField() returns error? {
    Price price = check clientValidationTestClient->post("/validation/getPrice", 7.4f);
    test:assertEquals(price, 7.4f);
}

@test:Config {}
function testResourceConstraintTypeFloatFieldMinValueError() {
    float|error response = clientValidationTestClient->post("/validation/getPrice", 4.1f);
    if response is http:ClientRequestError {
        test:assertEquals(response.detail().statusCode, 400, msg = "Found unexpected output");
        assertErrorHeaderValue(response.detail().headers[CONTENT_TYPE], TEXT_PLAIN);
        test:assertEquals(response.detail().body,
        "payload validation failed: Validation failed for '$:minValue' constraint(s).", msg = "Found unexpected output");
    } else {
        test:assertFail(msg = "Found unexpected output type");
    }
}

@test:Config {}
function testResourceConstraintTypeArrayField() returns error? {
    ValidationItem item = {weight: [2.3d, 5.4d]};
    ValidationItem response = check clientValidationTestClient->post("/validation/getWeight", item);
    test:assertEquals(response.weight, [2.3d, 5.4d]);
}

@test:Config {}
function testResourceConstraintTypeArrayFieldError() {
    ValidationItem item = {weight: [2.3d, 5.4d, 6.7d]};
    ValidationItem|error response = clientValidationTestClient->post("/validation/getWeight", item);
    if response is http:ClientRequestError {
        test:assertEquals(response.detail().statusCode, 400, msg = "Found unexpected output");
        assertErrorHeaderValue(response.detail().headers[CONTENT_TYPE], TEXT_PLAIN);
        test:assertEquals(response.detail().body,
        "payload validation failed: Validation failed for '$.weight:length' constraint(s).");
    } else {
        test:assertFail(msg = "Found unexpected output type");
    }
}
