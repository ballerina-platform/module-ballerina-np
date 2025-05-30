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

# Configuration for Azure OpenAI model.
type AzureOpenAIModelConfig record {|
    # Connection configuration for the Azure OpenAI model.
    AzureOpenAIConnectionConfig connectionConfig;
    # Service URL for the Azure OpenAI model.
    string serviceUrl;
|};

# Azure OpenAI model chat completion client.
isolated distinct client class AzureOpenAIModel {
    *ai:ModelProvider;

    private final http:Client cl;
    private final string deploymentId;
    private final string apiVersion;
    private final readonly & map<string> headers;

    isolated function init(AzureOpenAIModelConfig azureOpenAIModelConfig,
            string deploymentId,
            string apiVersion) returns error? {
        AzureOpenAIConnectionConfig connectionConfig = azureOpenAIModelConfig.connectionConfig;
        http:ClientConfiguration httpClientConfig = buildHttpClientConfig(connectionConfig);

        http:BearerTokenConfig|ApiKeysConfig auth = connectionConfig.auth;
        self.headers = auth is ApiKeysConfig ? {"api-key": auth?.apiKey} : {};

        self.cl = check new (azureOpenAIModelConfig.serviceUrl, httpClientConfig);

        self.deploymentId = deploymentId;
        self.apiVersion = apiVersion;
    }

    isolated remote function call(AzureOpenAICreateChatCompletionRequest chatBody)
            returns AzureOpenAICreateChatCompletionResponse|error {
        string resourcePath = string `/deployments/${check getEncodedUri(self.deploymentId)}/chat/completions`;
        resourcePath = string `${resourcePath}?${check getEncodedUri("api-version")}=${self.apiVersion}`;
        return self.cl->post(resourcePath, chatBody, self.headers);
    }

    isolated remote function chat(ai:ChatMessage[] messages, ai:ChatCompletionFunctions[] tools = [], string? stop = ())
            returns ai:ChatAssistantMessage|ai:LlmError {
        AzureOpenAICreateChatCompletionRequest chatBody = {
            messages,
            tools: generateOpenAIChatCompletionTools(tools),
            tool_choice: getToolChoiceToGenerateLlmResult()
        };

        AzureOpenAICreateChatCompletionResponse|error chatResult = self->call(chatBody);
        if chatResult is error {
            return error ai:LlmError("LLM call failed: " + chatResult.message());
        }
        
        record {
            AzureOpenAIChatCompletionResponseMessage message?;
        }[]? choices = chatResult.choices;

        if choices is () {
            return error ai:LlmError("No completion choices");
        }

        ChatCompletionMessageToolCall[]? toolCalls = choices[0].message?.tool_calls;

        if toolCalls is () {
            return error ai:LlmError(NO_RELEVANT_RESPONSE_FROM_THE_LLM);
        }

        return {
            role: ai:ASSISTANT,
            name: GET_RESULTS_TOOL,
            toolCalls: <ai:FunctionCall[]>from ChatCompletionMessageToolCall tool in toolCalls 
                select {name: tool.'function.name, arguments: tool.'function.arguments}
        };
    }
}
