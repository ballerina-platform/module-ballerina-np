// Copyright (c) 2025 WSO2 LLC. (http://www.wso2.org).
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

import ballerina/jballerina.java;

type JsonSchema record {|
    string 'type;
    map<JsonSchema|JsonArraySchema|map<json>> properties?;
    string[] required?;
    boolean nullable?;
|};

type JsonArraySchema record {|
    string 'type = "array";
    JsonSchema items;
    boolean nullable?;
|};

isolated function generateJsonSchemaForTypedescAsJson(typedesc<json> expectedResponseTypedesc) returns map<json> =>
    let map<json>? ann = expectedResponseTypedesc.@JsonSchema in ann ?:
         (generateJsonSchemaForTypedescNative(expectedResponseTypedesc) 
                ?: generateJsonSchemaForTypedesc(expectedResponseTypedesc, containsNil(expectedResponseTypedesc)));

isolated function generateJsonSchemaForTypedesc(typedesc<json> expectedResponseTypedesc, boolean nilableType) returns JsonSchema|JsonArraySchema|map<json> {
    if isSimpleType(expectedResponseTypedesc) {
        return <JsonSchema>{
            'type: getStringRepresentation(<typedesc<json>>expectedResponseTypedesc)
        };
    }

    boolean isArray = expectedResponseTypedesc is typedesc<json[]>;

    typedesc<map<json>?> recTd;

    if isArray {
        typedesc<json> arrayMemberType = getArrayMemberType(<typedesc<json[]>>expectedResponseTypedesc);
        map<json>? ann = arrayMemberType.@JsonSchema;
        if ann !is () {
            return ann;
        }
        if isSimpleType(arrayMemberType) {
            return <JsonArraySchema>{
                items: {
                    'type: getStringRepresentation(<typedesc<json>>arrayMemberType),
                    nullable: nilableType ? true: ()
                }
            };
        }
        recTd = <typedesc<map<json>?>>arrayMemberType;
    } else {
        recTd = <typedesc<map<json>?>>expectedResponseTypedesc;
    }

    string[] names = [];
    boolean[] required = [];
    typedesc<json>[] types = [];
    boolean[] nilable = [];
    populateFieldInfo(recTd, names, required, types, nilable);
    return generateJsonSchema(names, required, types, nilable, isArray, containsNil(recTd));
}

isolated function populateFieldInfo(typedesc<json> expectedResponseTypedesc, string[] names, boolean[] required,
        typedesc<json>[] types, boolean[] nilable) = @java:Method {
    name: "populateFieldInfo",
    'class: "io.ballerina.lib.np.Native"
} external;

isolated function getArrayMemberType(typedesc<json> expectedResponseTypedesc) returns typedesc<json> = @java:Method {
    name: "getArrayMemberType",
    'class: "io.ballerina.lib.np.Native"
} external;

isolated function containsNil(typedesc<json> expectedResponseTypedesc) returns boolean = @java:Method {
    name: "containsNil",
    'class: "io.ballerina.lib.np.Native"
} external;

isolated function generateJsonSchema(string[] names, boolean[] required,
        typedesc<json>[] types, boolean[] nilable, boolean isArray, boolean nilableType) returns JsonSchema|JsonArraySchema {
    map<JsonSchema|JsonArraySchema|map<json>> properties = {};
    string[] requiredSchema = [];

    JsonSchema schema = {
        'type: "object",
        nullable: nilableType ? true: (),
        properties,
        required: requiredSchema
    };

    foreach int i in 0 ..< names.length() {
        string fieldName = names[i];
        map<json>? ann = types[i].@JsonSchema;
        JsonSchema|JsonArraySchema|map<json> fieldSchema = ann is () ? getJsonSchemaType(types[i], nilable[i]) : ann;
        properties[fieldName] = fieldSchema;
        if required[i] {
            requiredSchema.push(fieldName);
        }
    }

    if isArray {
        return <JsonArraySchema>{
            items: schema,
            'type: "array",
            nullable: nilableType ? true: ()
        };
    }

    return schema;
}

isolated function getJsonSchemaType(typedesc<json> fieldType, boolean nilable) returns JsonSchema|JsonArraySchema|map<json> {
    if isSimpleType(fieldType) {
        return <JsonSchema>{
                'type: getStringRepresentation(fieldType),
                nullable: nilable ? true: ()
            };
    }

    return generateJsonSchemaForTypedesc(fieldType, nilable);
}

isolated function isSimpleType(typedesc<json> expectedResponseTypedesc) returns boolean =>
    expectedResponseTypedesc is typedesc<string|int|float|decimal|boolean|()>;

isolated function getStringRepresentation(typedesc<json> fieldType) returns string {
    if fieldType is typedesc<()> {
        return "null";
    }
    if fieldType is typedesc<string> {
        return "string";
    }
    if fieldType is typedesc<int> {
        return "integer";
    }
    if fieldType is typedesc<float|decimal> {
        return "number";
    }
    if fieldType is typedesc<boolean> {
        return "boolean";
    }

    panic error("JSON schema generation is not yet supported for type: " + fieldType.toString());
}

isolated function generateJsonSchemaForTypedescNative(typedesc<anydata> td) returns map<json>? = @java:Method {
    'class: "io.ballerina.lib.np.Native"
} external;
