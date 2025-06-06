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

# Configuration for OpenAI model.
type OpenAIModelConfig record {|
    # Connection configuration for the OpenAI model.
    OpenAIConnectionConfig connectionConfig;
    # Service URL for the OpenAI model.
    string serviceUrl?;
|};

# OpenAI model chat completion client.
isolated distinct client class OpenAIModel {
    *ModelProvider;

    private final http:Client cl;
    private final string model;

    isolated function init(OpenAIModelConfig openAIModelConfig, string model) returns error? {
        OpenAIConnectionConfig connectionConfig = openAIModelConfig.connectionConfig;
        http:ClientConfiguration httpClientConfig = buildHttpClientConfig(connectionConfig);
        httpClientConfig.auth = connectionConfig.auth;
        self.cl = check new (openAIModelConfig.serviceUrl ?: "https://api.openai.com/v1", httpClientConfig);
        self.model = model;        
    }

    isolated remote function chat(OpenAICreateChatCompletionRequest chatBody) 
            returns OpenAICreateChatCompletionResponse|error {
        return self.cl->/chat/completions.post(chatBody);
    }

    isolated remote function call(Prompt prompt, typedesc<anydata> expectedResponseTypedesc) returns anydata|error {
        OpenAICreateChatCompletionRequest chatBody = {
            messages: [{role: "user", "content": getPromptWithExpectedResponseSchema(prompt, expectedResponseTypedesc)}],
            model: self.model
        };

        OpenAICreateChatCompletionResponse chatResult = check self->chat(chatBody);
        OpenAICreateChatCompletionResponse_choices[] choices = chatResult.choices;

        string? resp = choices[0].message?.content;
        if resp is () {
            return error("No completion message");
        }
        return parseResponseAsType(resp, expectedResponseTypedesc);
    }
}
