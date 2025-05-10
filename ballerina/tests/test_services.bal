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
    resource function post azureopenai/deployments/gpt4onew/chat/completions(
            string api\-version, AzureOpenAICreateChatCompletionRequest payload)
                returns json|error {
        test:assertEquals(api\-version, "2023-08-01-preview");
        string content = payload.messages[0].content;
        AzureOpenAIChatCompletionRequestMessage[]? messages = payload.messages;
        if messages is () {
            test:assertFail("Expected messages in the payload");
        }
        AzureOpenAIChatCompletionRequestMessage message = messages[0];
        test:assertEquals(content, getExpectedPrompt(content));
        test:assertEquals(message.role, "user");
        ChatCompletionTool[]? tools = payload?.tools;
        if tools is () {
            test:assertFail("No tools in the payload");
        }

        FunctionParameters? parameters = tools[0].'function.parameters;
        if parameters is () {
            test:assertFail("No tools in the payload");
        }

        test:assertEquals(parameters, getExpectedParameterSchema(content));
        return getTestServiceResponse(content);
    }

    resource function post openai/chat/completions(OpenAICreateChatCompletionRequest payload)
            returns json|error {

        OpenAIChatCompletionRequestUserMessage message = payload.messages[0];
        anydata content = message["content"];
        string contentStr = content.toString();
        test:assertEquals(message.role, "user");
        test:assertEquals(content, getExpectedPrompt(contentStr));
        ChatCompletionTool[]? tools = payload?.tools;
        if tools is () {
            test:assertFail("No tools in the payload");
        }

        FunctionParameters? parameters = tools[0].'function.parameters;
        if parameters is () {
            test:assertFail("No tools in the payload");
        }
        test:assertEquals(parameters, getExpectedParameterSchema(contentStr));

        test:assertEquals(payload.model, "gpt4o");
        return getTestServiceResponse(contentStr);
    }
}
