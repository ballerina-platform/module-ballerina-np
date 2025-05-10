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

isolated function getExpectedParameterSchema(string message) returns FunctionParameters {
    if message.startsWith("Rate this blog") {
        return expectedParamterSchemaStringForRateBlog;
    }

    if message.startsWith("Please rate this blog") {
        return expectedParamterSchemaStringForRateBlog2;
    }

    if message.startsWith("What is 1 + 1?") {
        return expectedParamterSchemaStringForRateBlog3;
    }

    if message.startsWith("Tell me") {
        return expectedParamterSchemaStringForRateBlog4;
    }

    if message.startsWith("What's the output of the Ballerina code below?") {
        return expectedParamterSchemaStringForBalProgram;
    }

    if message.startsWith("Which country") {
        return expectedParamterSchemaStringForCountry;
    }

    if message.startsWith("Who is a popular sportsperson") {
        return {"$schema": "https://json-schema.org/draft/2020-12/schema", "type": "object", "properties": {
            "result": {"type": ["object", "null"], "properties": {"firstName": {"type": "string"}, 
            "middleName": {"type": ["string", "null"]}, "lastName": {"type": "string"}, "yearOfBirth": {
            "type": "integer"}, "sport": {"type": "string"}}, 
            "required": ["firstName", "middleName", "lastName", "yearOfBirth", "sport"]}}};
    }

    return {};
}

isolated function getExpectedPrompt(string message) returns string {
    if message.startsWith("Rate this blog") {
        return expectedPromptStringForRateBlog;
    }

    if message.startsWith("Please rate this blog") {
        return expectedPromptStringForRateBlog2;
    }

    if message.startsWith("What is 1 + 1?") {
        return expectedPromptStringForRateBlog3;
    }

    if message.startsWith("Tell me") {
        return expectedPromptStringForRateBlog4;
    }

    if message.startsWith("What's the output of the Ballerina code below?") {
        return expectedPromptStringForBalProgram;
    }

    if message.startsWith("Which country") {
        return expectedPromptStringForCountry;
    }

    if message.startsWith("Who is a popular sportsperson") {
        return string `Who is a popular sportsperson that was born in the decade starting
            from 1990 with Simone in their name?`;
    }

    return "INVALID";
}

isolated function getTheMockLLMResult(string message) returns string {
    if message.startsWith("Rate this blog") {
        return "{\"result\": 4}";
    }

    if message.startsWith("Please rate this blog") {
        return review;
    }

    if message.startsWith("What is 1 + 1?") {
        return "{\"result\": 2}";
    }

    if message.startsWith("Tell me") {
        return "{\"result\": [{\"name\": \"Virat Kohli\", \"age\": 33}, {\"name\": \"Kane Williamson\", \"age\": 30}]}";
    }

    if message.startsWith("What's the output of the Ballerina code below?") {
        return "{\"result\": 30}";
    }

    if message.startsWith("Which country") {
        return "{\"result\": \"Sri Lanka\"}";
    }

    if message.startsWith("Who is a popular sportsperson") {
        return "{\"result\": {\"firstName\": \"Simone\", \"middleName\": null, " +
             "\"lastName\": \"Biles\", \"yearOfBirth\": 1997, \"sport\": \"Gymnastics\"}}";
    }

    return "INVALID";
}

isolated function getTestServiceResponse(string content) returns json =>
    {
        'object: "chat.completion",
        created: 0,
        model: "",
        id: "",
        choices: [
            {
                finish_reason: "stop",
                index: 0,
                logprobs: (),
                message: {
                    content: (),
                    role: "assistant",
                    tool_calls: [
                        {
                            id: GET_RESULTS_TOOL,
                            'function: {
                                name: GET_RESULTS_TOOL,
                                arguments: getTheMockLLMResult(content)
                            }
                        }
                    ]
                }
            }
        ]
    };
