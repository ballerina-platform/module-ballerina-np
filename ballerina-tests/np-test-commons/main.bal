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
import ballerina/http;
import ballerina/test;

const NO_RELEVANT_RESPONSE_FROM_THE_LLM = "No relevant response from the LLM";
const FUNCTION = "function";
const GET_RESULTS_TOOL = "getResults";

service /llm on new http:Listener(8080) {
    resource function post openai/chat/completions(OpenAICreateChatCompletionRequest payload)
            returns json|error {
        ai:ChatMessage message = payload.messages[0];
        anydata content = message["content"];
        string contentStr = content.toString();
        test:assertEquals(message.role, "user");
        ChatCompletionFunctions[]? tools = payload?.tools;
        if tools is () {
            test:assertFail(NO_RELEVANT_RESPONSE_FROM_THE_LLM);
        }

        FunctionParameters? parameters = tools[0].parameters;
        if parameters is () {
            test:assertFail(NO_RELEVANT_RESPONSE_FROM_THE_LLM);
        }
        test:assertEquals(contentStr, getExpectedPrompt(contentStr));
        test:assertEquals(parameters, getExpectedParameterSchema(contentStr));

        return {
            choices: [
                {
                    message: {
                        content: (),
                        role: "assistant",
                        tool_calls: [
                            {
                                name: GET_RESULTS_TOOL,
                                arguments: getMockLLMResponse(contentStr)
                            }
                        ]
                    }
                }
            ]
        };
    }

    resource function post 'default/chat/complete(DefaultChatCompletionRequest req)
            returns ai:ChatAssistantMessage|error {
        ai:ChatMessage message = req.messages[0];
        anydata content = message["content"];
        string contentStr = content.toString();
        ChatCompletionFunctions[]? tools = req?.tools;
        if tools is () {
            test:assertFail(NO_RELEVANT_RESPONSE_FROM_THE_LLM);
        }

        FunctionParameters? parameters = tools[0].parameters;
        test:assertEquals(parameters, getExpectedParameterSchema(contentStr));
        return {
            role: "assistant",
            toolCalls: [{
                name: GET_RESULTS_TOOL,
                arguments: getMockLLMResponse(contentStr)
            }]
        };
    }
}

isolated function getExpectedPrompt(string prompt) returns string {
    string trimmedPrompt = prompt.trim();

    if trimmedPrompt.startsWith("Which country") {
        return string `Which country is known as the pearl of the Indian Ocean?`;
    }

    if trimmedPrompt.startsWith("For each string value ") {
        return string `For each string value in the given array if the value can be parsed
    as an integer give an integer, if not give the same string value. Please preserve the order.
    Array value: ["foo","1","bar","2.3","4"]`;
    }

    if trimmedPrompt.startsWith("Who is a popular sportsperson") {
        return string `Who is a popular sportsperson that was born in the decade starting
    from 1990 with Simone in their name?`;
    }

    if trimmedPrompt.includes("Tell me about places in the specified country") && trimmedPrompt.includes("Sri Lanka") {
        return string `You have been given the following input:

country: 
${"```"}
Sri Lanka
${"```"}

interest: 
${"```"}
beach
${"```"}

count: 
${"```"}
3
${"```"}

    Tell me about places in the specified country that could be a good destination 
    to someone who has the specified interest.

    Include only the number of places specified by the count parameter.`;
    }

    if trimmedPrompt.includes("Tell me about places in the specified country") && trimmedPrompt.includes("UAE") {
        return string `You have been given the following input:

country: 
${"```"}
UAE
${"```"}

interest: 
${"```"}
skyscrapers
${"```"}

count: 
${"```"}
2
${"```"}

    Tell me about places in the specified country that could be a good destination 
    to someone who has the specified interest.

    Include only the number of places specified by the count parameter.`;
    }

    if trimmedPrompt.startsWith("What's the output of the Ballerina code below") {
        return string `What's the output of the Ballerina code below?

    ${"```"}ballerina
    import ballerina/io;

    public function main() {
        int x = 10;
        int y = 20;
        io:println(x + y);
    }
    ${"```"}`;
    }

    if trimmedPrompt.includes("What's the sum of these") {
        if trimmedPrompt.includes("[]") {
            return string `You have been given the following input:

a: 
${"```"}
1
${"```"}

b: 
${"```"}
2
${"```"}

c: 
${"```"}
[]
${"```"}

    What's the sum of these values?`;
        }

        if trimmedPrompt.includes("[40,50]") {
            return string `You have been given the following input:

a: 
${"```"}
20
${"```"}

b: 
${"```"}
30
${"```"}

c: 
${"```"}
[40,50]
${"```"}

    What's the sum of these values?`;
        }
    }

    if trimmedPrompt.includes("Give me the sum of these values") {
        if trimmedPrompt.includes("[]") {
            return string `You have been given the following input:

d: 
${"```"}
100
${"```"}

e: 
${"```"}
{"val":200}
${"```"}

f: 
${"```"}
[]
${"```"}

    Give me the sum of these values`;
        }

        if trimmedPrompt.includes("[500]") {
            return string `You have been given the following input:

d: 
${"```"}
300
${"```"}

e: 
${"```"}
{"val":400}
${"```"}

f: 
${"```"}
[500]
${"```"}

    Give me the sum of these values`;
        }
    }

    test:assertFail("Unexpected prompt: " + trimmedPrompt);
}

isolated function getExpectedParameterSchema(string prompt) returns map<json> {
    string trimmedPrompt = prompt.trim();

    if trimmedPrompt.startsWith("Which country") {
        return {"type": "object", "properties": {"result": {"type": "string"}}};
    }

    if trimmedPrompt.startsWith("For each string value ") {
        return {
            "type":"object",
            "properties":{
                "result":{
                    "type":"array",
                    "items":{
                        "anyOf":[
                            {"type":"string"},
                            {"type":"integer"}
                        ]
                    }
                }
            }
        };
    }

    if trimmedPrompt.startsWith("Who is a popular sportsperson") {
        return {
            "type": "object",
            "properties": {
                "result": {
                "anyOf": [
                    {
                    "type": "object",
                    "required": [
                        "firstName",
                        "lastName",
                        "sport",
                        "yearOfBirth"
                    ],
                    "properties": {
                        "firstName": {
                        "type": "string",
                        "description": "First name of the person"
                        },
                        "lastName": {
                        "type": "string",
                        "description": "Last name of the person"
                        },
                        "yearOfBirth": {
                        "type": "integer",
                        "format": "int64",
                        "description": "Year the person was born"
                        },
                        "sport": {
                        "type": "string",
                        "description": "Sport that the person plays"
                        }
                    }
                    }
                ],
                "nullable": true
                }
            }
        };
    }

    if trimmedPrompt.includes("Tell me about places in the specified country") && trimmedPrompt.includes("Sri Lanka") {
        return {
            "type": "object",
            "properties": {
                "result": {
                    "type": "array",
                    "items": {
                        "required": ["city", "country", "description", "name"],
                        "type": "object",
                        "properties": {
                            "name": {"type": "string", "description": "Name of the place."},
                            "city": {"type": "string", "description": "City in which the place is located."},
                            "country": {"type": "string", "description": "Country in which the place is located."},
                            "description": {"type": "string", "description": "One-liner description of the place."}
                        }
                    }
                }
            }
        };
    }

    if trimmedPrompt.includes("Tell me about places in the specified country") && trimmedPrompt.includes("UAE") {
        return {
            "type": "object",
            "properties": {
                "result": {
                    "type": "array",
                    "items": {
                        "required": ["city", "country", "description", "name"],
                        "type": "object",
                        "properties": {
                            "name": {"type": "string", "description": "Name of the place."},
                            "city": {
                                "type": "string",
                                "description": "City in which the place is located."
                            },
                            "country": {
                                "type": "string",
                                "description": "Country in which the place is located."
                            },
                            "description": {"type": "string", "description": "One-liner description of the place."}
                        }
                    }
                }
            }
        };
    }

    if trimmedPrompt.startsWith("What's the output of the Ballerina code below") {
        return {"type": "object", "properties": {"result": {"type": "integer"}}};
    }

    if trimmedPrompt.includes("What's the sum of these") {
        if trimmedPrompt.includes("[]") {
            return {"type": "object", "properties": {"result": {"type": "integer"}}};
        }

        if trimmedPrompt.includes("[40,50]") {
            return {"type": "object", "properties": {"result": {"type": "integer"}}};
        }
    }

    if trimmedPrompt.includes("Give me the sum of these values") {
        if trimmedPrompt.includes("[]") {
            return {"type": "object", "properties": {"result": {"type": "integer"}}};
        }

        if trimmedPrompt.includes("[500]") {
            return {"type": "object", "properties": {"result": {"type": "integer"}}};
        }
    }

    test:assertFail("Unexpected prompt: " + trimmedPrompt);
}

isolated function getMockLLMResponse(string message) returns string {
    if message.startsWith("Which country") {
        return "{\"result\": \"Sri Lanka\"}";
    }

    if message.startsWith("For each string value ") {
        return "{\"result\": [\"foo\", 1, \"bar\", \"2.3\", 4]}";
    }

    if message.startsWith("Who is a popular sportsperson") {
        return "{\"result\": {\"firstName\": \"Simone\", \"lastName\": \"Biles\", \"yearOfBirth\": 1997, " +
            "\"sport\": \"Gymnastics\"}}";
    }

    if message.includes("Tell me about places in the specified country") && message.includes("Sri Lanka") {
        return "{\"result\": [{\"name\": \"Unawatuna Beach\", \"city\": \"Galle\", " +
            "\"country\": \"Sri Lanka\", \"description\": \"A popular beach known for its golden sands and vibrant" +
                " nightlife.\"}, {\"name\": \"Mirissa Beach\", \"city\": \"Mirissa\", \"country\": \"Sri Lanka\"," +
                " \"description\": \"Famous for its stunning sunsets and opportunities for whale watching.\"}," +
                " {\"name\": \"Hikkaduwa Beach\", \"city\": \"Hikkaduwa\", \"country\": \"Sri Lanka\"," +
                " \"description\": \"A great destination for snorkeling and surfing, " +
                "lined with lively restaurants.\"}]}";
    }

    if message.includes("Tell me about places in the specified country") && message.includes("UAE") {
        return "{\"result\": [{\"name\": \"Burj Khalifa\", \"city\": \"Dubai\", " +
            "\"country\": \"UAE\", \"description\": \"The tallest building in the world, offering panoramic views" +
                " of the city.\"}, {\"name\": \"Ain Dubai\", \"city\": \"Dubai\", \"country\": \"UAE\"," +
                " \"description\": \"The world's tallest observation wheel, providing breathtaking " +
                "views of the Dubai skyline.\"}]}";
    }

    if message.startsWith("What's the output of the Ballerina code below?") {
        return "{\"result\": 30}";
    }

    if message.includes("What's the sum of these") {
        if message.includes("[]") {
            return "{\"result\": 3}";
        }

        if message.includes("[40,50]") {
            return "{\"result\": 140}";
        }
    }

    if message.includes("Give me the sum of these values") {
        if message.includes("[]") {
            return "{\"result\": 300}";
        }

        if message.includes("[500]") {
            return "{\"result\": 1200}";
        }
    }

    test:assertFail("Unexpected prompt");
}
