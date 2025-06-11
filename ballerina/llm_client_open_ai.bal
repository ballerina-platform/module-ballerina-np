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

# Configuration for OpenAI model.
type OpenAIModelConfig record {|
    # Connection configuration for the OpenAI model.
    OpenAIConnectionConfig connectionConfig;
    # Service URL for the OpenAI model.
    string serviceUrl?;
|};

# OpenAI model chat completion client.
isolated distinct client class OpenAIModel {
    *ai:ModelProvider;

    private final http:Client cl;
    private final string model;

    isolated function init(OpenAIModelConfig openAIModelConfig, string model) returns error? {
        OpenAIConnectionConfig connectionConfig = openAIModelConfig.connectionConfig;
        http:ClientConfiguration httpClientConfig = buildHttpClientConfig(connectionConfig);
        httpClientConfig.auth = connectionConfig.auth;
        self.cl = check new (openAIModelConfig.serviceUrl ?: "https://api.openai.com/v1", httpClientConfig);
        self.model = model;
    }

    isolated remote function chat(ai:ChatMessage[] messages, ai:ChatCompletionFunctions[] tools = [], string? stop = ())
            returns ai:ChatAssistantMessage|ai:LlmError {
        ChatCompletionTool[]|error chatCompletionTools = generateOpenAIChatCompletionTools(tools);
        if chatCompletionTools is error {
            return error ai:LlmError(
                "Failed to generate OpenAI chat completion tools: " + chatCompletionTools.message());
        }

        OpenAICreateChatCompletionRequest chatBody = {
            messages: [{role: ai:USER, content: <string>messages[0].content}],
            model: self.model,
            tools: chatCompletionTools,
            tool_choice: getGetResultsToolChoice()
        };

        OpenAICreateChatCompletionResponse|error chatResult = self.cl->/chat/completions.post(chatBody);
        if chatResult is error {
            return error ai:LlmError("LLM call failed: " + chatResult.message());
        }
        
        record {
            OpenAIChatCompletionResponseMessage message?;
        }[]? choices = chatResult.choices;

        if choices is () {
            return error ai:LlmError("No completion choices");
        }

        ChatCompletionMessageToolCall[]? toolCalls = choices[0].message?.tool_calls;

        if toolCalls is () {
            return error ai:LlmError(NO_RELEVANT_RESPONSE_FROM_THE_LLM);
        }

        ChatCompletionMessageToolCall tool = toolCalls[0];
        map<json>|error arguments = tool.'function.arguments.fromJsonStringWithType();
        if arguments is error {
            return error ai:LlmError(NO_RELEVANT_RESPONSE_FROM_THE_LLM);
        }

        return {
            role: ai:ASSISTANT,
            name: GET_RESULTS_TOOL,
            toolCalls: [{name: tool.'function.name, arguments}]
        };
    }
}
