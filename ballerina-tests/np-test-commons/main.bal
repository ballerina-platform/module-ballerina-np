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

import ballerina/http;
import ballerina/test;

service /llm on new http:Listener(8080) {
    resource function post openai/chat/completions(OpenAICreateChatCompletionRequest payload)
            returns json|error {
        OpenAIChatCompletionRequestUserMessage message = payload.messages[0];
        anydata content = message["content"];
        string contentStr = content.toString();
        test:assertEquals(message.role, "user");
        test:assertEquals(content, getExpectedPrompt(content.toString()));

        test:assertEquals(payload.model, "gpt-4o-mini");
        return {
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
                        role: "assistant",
                        content: getMockLLMResponse(contentStr),
                        refusal: ()
                    }
                }
            ]
        };
    }

    resource function post 'default/chat/complete(@http:Payload string contentStr)
            returns json|error {
        test:assertEquals(contentStr, getExpectedPrompt(contentStr.toString()));
        return {
            content: [getMockLLMResponse(contentStr)]
        };
    }
}

isolated function getExpectedPrompt(string prompt) returns string {
    string trimmedPrompt = prompt.trim();

    if trimmedPrompt.startsWith("Which country") {
        return  string `Which country is known as the pearl of the Indian Ocean?
        ---

        The output should be a JSON value that satisfies the following JSON schema, 
        returned within a markdown snippet enclosed within ${"```json"} and ${"```"}
        
        Schema:
        {"type":"string"}`;
    }

    if trimmedPrompt.startsWith("For each string value ") {
        return string `For each string value in the given array if the value can be parsed
    as an integer give an integer, if not give the same string value. Please preserve the order.
    Array value: ["foo","1","bar","2.3","4"]
        ---

        The output should be a JSON value that satisfies the following JSON schema, 
        returned within a markdown snippet enclosed within ${"```json"} and ${"```"}
        
        Schema:
        {"type":"array", "items":{"type":"object", "anyOf":[{"type":"string"}, {"type":"integer"}]}}`;
    }

    if trimmedPrompt.startsWith("Who is a popular sportsperson") {
        return string `Who is a popular sportsperson that was born in the decade starting
    from 1990 with Simone in their name?
        ---

        The output should be a JSON value that satisfies the following JSON schema, 
        returned within a markdown snippet enclosed within ${"```json"} and ${"```"}
        
        Schema:
        {"type":"object", "anyOf":[{"required":["firstName", "lastName", "sport", "yearOfBirth"], "type":"object", "properties":{"firstName":{"type":"string", "description":"First name of the person"}, "lastName":{"type":"string", "description":"Last name of the person"}, "yearOfBirth":{"type":"integer", "description":"Year the person was born", "format":"int64"}, "sport":{"type":"string", "description":"Sport that the person plays"}}}, {"type":null}]}`;
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

    Include only the number of places specified by the count parameter.
        ---

        The output should be a JSON value that satisfies the following JSON schema, 
        returned within a markdown snippet enclosed within ${"```"}json and ${"```"}
        
        Schema:
        {"type":"array", "items":{"required":["city", "country", "description", "name"], "type":"object", "properties":{"name":{"type":"string", "description":"Name of the place."}, "city":{"type":"string", "description":"City in which the place is located."}, "country":{"type":"string", "description":"Country in which the place is located."}, "description":{"type":"string", "description":"One-liner description of the place."}}}}`;
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

    Include only the number of places specified by the count parameter.
        ---

        The output should be a JSON value that satisfies the following JSON schema, 
        returned within a markdown snippet enclosed within ${"```"}json and ${"```"}
        
        Schema:
        {"type":"array", "items":{"required":["city", "country", "description", "name"], "type":"object", "properties":{"name":{"type":"string", "description":"Name of the place."}, "city":{"type":"string", "description":"City in which the place is located."}, "country":{"type":"string", "description":"Country in which the place is located."}, "description":{"type":"string", "description":"One-liner description of the place."}}}}`;
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
    ${"```"}
        ---

        The output should be a JSON value that satisfies the following JSON schema, 
        returned within a markdown snippet enclosed within ${"```json"} and ${"```"}
        
        Schema:
        {"type":"integer"}`;
    }

    test:assertFail("Unexpected prompt: " + trimmedPrompt);
}

isolated function getMockLLMResponse(string message) returns string? {
    if message.startsWith("Which country") {
        return "```\n\"Sri Lanka\"\n```";
    }

    if message.startsWith("For each string value ") {
        return "```\n[\"foo\", 1, \"bar\", \"2.3\", 4]\n```";
    }

    if message.startsWith("Who is a popular sportsperson") {
        return "```\n{\"firstName\":\"Simone\",\"lastName\":\"Biles\",\"yearOfBirth\":1997,\"sport\":\"Gymnastics\"}\n```";
    }

    if message.includes("Tell me about places in the specified country") && message.includes("Sri Lanka") {
        return "```\n[{\"name\":\"Unawatuna Beach\",\"city\":\"Galle\",\"country\":\"Sri Lanka\",\"description\":\"A popular beach known for its golden sands and vibrant nightlife.\"},{\"name\":\"Mirissa Beach\",\"city\":\"Mirissa\",\"country\":\"Sri Lanka\",\"description\":\"Famous for its stunning sunsets and opportunities for whale watching.\"},{\"name\":\"Hikkaduwa Beach\",\"city\":\"Hikkaduwa\",\"country\":\"Sri Lanka\",\"description\":\"A great destination for snorkeling and surfing, lined with lively restaurants.\"}]\n```";
    }

    if message.includes("Tell me about places in the specified country") && message.includes("UAE") {
        return "```\n[{\"name\":\"Burj Khalifa\",\"city\":\"Dubai\",\"country\":\"UAE\",\"description\":\"The tallest building in the world, offering panoramic views of the city.\"},{\"name\":\"Ain Dubai\",\"city\":\"Dubai\",\"country\":\"UAE\",\"description\":\"The world's tallest observation wheel, providing breathtaking views of the Dubai skyline.\"}]\n```";
    }

    if message.startsWith("What's the output of the Ballerina code below?") {
        return string `The output of the provided Ballerina code calculates the sum of ${"`"}x${"`"} and ${"`"}y${"`"}, which is ${"`"}10 + 20${"`"}. Therefore, the result will be ${"`"}30${"`"}. \n\nHere is the output formatted as a JSON value that satisfies your specified schema:${"\n\n```"}json${"\n"}30${"\n```"}`;
    }

    test:assertFail("Unexpected prompt");
}
