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

import ballerina/ai;
import ballerina/test;

const ERROR_MESSAGE = "Error occurred while attempting to parse the response from the LLM as the expected type. Retrying and/or validating the prompt could fix the response.";

@test:Config
function testPromptAsCodeFunctionWithSimpleExpectedTypeWithDefaultAzureOpenAIClient() returns error? {
    int rating = check callLlm(`Rate this blog out of 10.
        Title: ${blog1.title}
        Content: ${blog1.content}`);
    test:assertEquals(rating, 4);
}

@test:Config
function testPromptAsCodeFunctionWithStructuredExpectedTypeWithOpenAIClient() returns error? {
    ai:ModelProvider model = check new OpenAIModel({
        connectionConfig: {
            auth: {token: "not-a-real-token"}
        },
        serviceUrl: "http://localhost:8080/llm/openai"
    }, "gpt4o");
    Review review = check callLlm(`Please rate this blog out of 10.
        Title: ${blog2.title}
        Content: ${blog2.content}`, {model});
    test:assertEquals(review, review);
}

@test:Config
function testJsonConversionError() {
    boolean|error rating = callLlm(`What is 1 + 1?`);
    test:assertTrue(rating is error);
    test:assertTrue((<error>rating).message().includes(ERROR_MESSAGE));
}

type RecordForInvalidBinding record {|
    string name;
|};

@test:Config
function testJsonConversionError2() {
    RecordForInvalidBinding[]|error rating = callLlm(`Tell me name and the age of the top 10 world class cricketers`);
    test:assertTrue(rating is error);
    test:assertTrue((<error>rating).message().includes(ERROR_MESSAGE));
}

@test:Config
function testJsonContentAfterTextDescription() returns error? {
    int result = check callLlm(`What's the output of the Ballerina code below?

    ${"```"}ballerina
    import ballerina/io;

    public function main() {
        int x = 10;
        int y = 20;
        io:println(x + y);
    }
    ${"```"}`);
    test:assertEquals(result, 30);
}

@test:Config
function testJsonContentWithoutJsonAfterBackticks() returns error? {
    string result = check callLlm(`Which country is known as the pearl of the Indian Ocean?`);
    test:assertEquals(result, "Sri Lanka");
}

# Represents a person who plays a sport.
type SportsPerson record {|
    # First name of the person
    string firstName;
    # Middle name of the person
    string? middleName;
    # Last name of the person
    string lastName;
    # Year the person was born
    int yearOfBirth;
    # Sport that the person plays
    string sport;
|};

type SportsPersonOptional SportsPerson?;

@test:Config
function testSchemaGeneratedForComplexTypeAtRuntime() returns error? {
    typedesc<json> td = SportsPersonOptional;
    int decadeStart = 1990;
    string nameSegment = "Simone";
    json result = check callLlm(`Who is a popular sportsperson that was born in the decade starting
            from ${decadeStart} with ${nameSegment} in their name?`, expectedResponseTypedesc = td);
    test:assertEquals(result, <SportsPerson>{
                firstName: "Simone",
                lastName: "Biles",
                middleName: (),
                sport: "Gymnastics",
                yearOfBirth: 1997
            });
}

distinct isolated client class CustomModelWithInvalidReturn {
    *ai:ModelProvider;

    isolated remote function chat(ai:ChatMessage[] messages, ai:ChatCompletionFunctions[] tools = [], string? stop = ())
            returns ai:ChatAssistantMessage|ai:LlmError {
        return {
            role: "assistant",
            toolCalls: [
                {
                    name: GET_RESULTS_TOOL,
                    arguments: "{\"firstName\": \"Simone\", \"lastName\": \"Biles\", \"middleName\": 1, \"sport\": \"Gymnastics\", \"yearOfBirth\": 1997}"
                }
            ]
        };
    }
};

@test:Config
function testCustomModelWithInvalidReturn() returns error? {
    int decadeStart = 1990;
    string nameSegment = "Simone";
    SportsPerson|error result = callLlm(`Who is a popular sportsperson that was born in the decade starting
            from ${decadeStart} with ${nameSegment} in their name?`, {model: new CustomModelWithInvalidReturn()});
    test:assertTrue(result is error);
    test:assertTrue((<error>result).message().includes(ERROR_MESSAGE));
}
