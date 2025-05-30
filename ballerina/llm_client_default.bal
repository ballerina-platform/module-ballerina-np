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

const UNAUTHORIZED = 401;

# Configuration for the default Ballerina model.
type DefaultBallerinaModelConfig record {|
    # LLM service URL
    string url;
    # Access token
    string accessToken;
|};

# Default Ballerina model chat completion client.
isolated distinct client class DefaultBallerinaModel {
    *ai:ModelProvider;

    private final http:Client cl;

    isolated function init(DefaultBallerinaModelConfig config) returns error? {
        var {url, accessToken} = config;

        self.cl = check new (url, {
            auth: {
                token: accessToken
            }
        });
    }

    isolated remote function chat(ai:ChatMessage[] messages, ai:ChatCompletionFunctions[] tools = [], string? stop = ())
            returns ai:ChatAssistantMessage|ai:LlmError {
        http:Client cl = self.cl;
        http:Response|error chatResponse = cl->/chat/complete.post({
            messages,
            tools: generateOpenAIChatCompletionTools(tools),
            tool_choice: getToolChoiceToGenerateLlmResult()
        });

        if chatResponse is error {
            return error("LLM call failed: " + chatResponse.message());
        }

        int statusCode = chatResponse.statusCode;
        if statusCode == UNAUTHORIZED {
            return error("The default Ballerina model is being used. " 
                + "The token has expired and needs to be regenerated.");
        }

        if !(statusCode >= 200 && statusCode < 300) {
            string|error textPayload = chatResponse.getTextPayload();
            if textPayload is error {
                return error("LLM call failed: " + textPayload.message());
            }

            return error(string `LLM call failed: ${textPayload}`);
        }

        json|error jsonPayload = chatResponse.getJsonPayload();
        if jsonPayload is error {
            return error("LLM call failed: " + jsonPayload.message());
        }

        ai:ChatAssistantMessage|error payload = (jsonPayload).cloneWithType();
        if payload is ai:ChatAssistantMessage {
            return payload;
        }

        return error ai:LlmError("LLM call failed: " + payload.message());
    }
}
