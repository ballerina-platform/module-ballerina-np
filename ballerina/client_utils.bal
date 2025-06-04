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
import ballerina/url;

# Provides settings related to HTTP/1.x protocol.
type ClientHttp1Settings record {|
    # Specifies whether to reuse a connection for multiple requests
    http:KeepAlive keepAlive = http:KEEPALIVE_AUTO;
    # The chunking behaviour of the request
    http:Chunking chunking = http:CHUNKING_AUTO;
    # Proxy server related options
    ProxyConfig proxy?;
|};

# Proxy server configurations to be used with the HTTP client endpoint.
type ProxyConfig record {|
    # Host name of the proxy server
    string host = "";
    # Proxy server port
    int port = 0;
    # Proxy server username
    string userName = "";
    # Proxy server password
    @display {label: "", kind: "password"}
    string password = "";
|};

# Connection configuration for OpenAI.
type OpenAIConnectionConfig record {|
    # Configurations related to client authentication
    http:BearerTokenConfig auth;
    # The HTTP version understood by the client
    http:HttpVersion httpVersion = http:HTTP_2_0;
    # Configurations related to HTTP/1.x protocol
    ClientHttp1Settings http1Settings?;
    # Configurations related to HTTP/2 protocol
    http:ClientHttp2Settings http2Settings?;
    # The maximum time to wait (in seconds) for a response before closing the connection
    decimal timeout = 60;
    # The choice of setting `forwarded`/`x-forwarded` header
    string forwarded = "disable";
    # Configurations associated with request pooling
    http:PoolConfiguration poolConfig?;
    # HTTP caching related configurations
    http:CacheConfig cache?;
    # Specifies the way of handling compression (`accept-encoding`) header
    http:Compression compression = http:COMPRESSION_AUTO;
    # Configurations associated with the behaviour of the Circuit Breaker
    http:CircuitBreakerConfig circuitBreaker?;
    # Configurations associated with retrying
    http:RetryConfig retryConfig?;
    # Configurations associated with inbound response size limits
    http:ResponseLimitConfigs responseLimits?;
    # SSL/TLS-related options
    http:ClientSecureSocket secureSocket?;
    # Proxy server related options
    http:ProxyConfig proxy?;
    # Enables the inbound payload validation functionality which provided by the constraint package. Enabled by default
    boolean validation = true;
|};

type ApiKeysConfig record {|
    # The API key to use. This is the same as your subscription key.
    @display {label: "", kind: "password"}
    string apiKey;
|};

type FunctionParameters map<json>;

type FunctionObject record {|
    string description?;
    string name;
    FunctionParameters parameters?;
|};

type AssistantsNamedToolChoiceFunction record {|
    string name;
|};

type ChatCompletionNamedToolChoice record {|
    FUNCTION 'type = FUNCTION;
    AssistantsNamedToolChoiceFunction 'function;
|};

type ChatCompletionToolChoiceOption ChatCompletionNamedToolChoice;

type ChatCompletionTool record {|
    FUNCTION 'type = FUNCTION;
    FunctionObject 'function;
|};

type OpenAIChatCompletionRequestUserMessage record {|
    string content;
    "user" role;
    string name?;
|};

type OpenAICreateChatCompletionRequest record {|
    OpenAIChatCompletionRequestUserMessage[1] messages;
    string model;
    ChatCompletionTool[] tools?;
    ChatCompletionToolChoiceOption tool_choice?;
|};

type ChatCompletionMessageToolCall_function record {|
    string name;
    string arguments;
|};

type ChatCompletionMessageToolCalls ChatCompletionMessageToolCall[];

type OpenAIChatCompletionResponseMessage record {|
    string? content;
    ChatCompletionMessageToolCalls tool_calls?;
|};

type ChatCompletionMessageToolCall record {|
    string id?;
    FUNCTION 'type = FUNCTION;
    ChatCompletionMessageToolCall_function 'function;
|};

type OpenAICreateChatCompletionResponse_choices record {|
    OpenAIChatCompletionResponseMessage message;
|};

type OpenAICreateChatCompletionResponse record {|
    OpenAICreateChatCompletionResponse_choices[] choices;
|};

# Connection configuration for Azure OpenAI.
type AzureOpenAIConnectionConfig record {|
    # Configurations related to client authentication
    http:BearerTokenConfig|ApiKeysConfig auth;
    # The HTTP version understood by the client
    http:HttpVersion httpVersion = http:HTTP_2_0;
    # Configurations related to HTTP/1.x protocol
    ClientHttp1Settings http1Settings?;
    # Configurations related to HTTP/2 protocol
    http:ClientHttp2Settings http2Settings?;
    # The maximum time to wait (in seconds) for a response before closing the connection
    decimal timeout = 60;
    # The choice of setting `forwarded`/`x-forwarded` header
    string forwarded = "disable";
    # Configurations associated with request pooling
    http:PoolConfiguration poolConfig?;
    # HTTP caching related configurations
    http:CacheConfig cache?;
    # Specifies the way of handling compression (`accept-encoding`) header
    http:Compression compression = http:COMPRESSION_AUTO;
    # Configurations associated with the behaviour of the Circuit Breaker
    http:CircuitBreakerConfig circuitBreaker?;
    # Configurations associated with retrying
    http:RetryConfig retryConfig?;
    # Configurations associated with inbound response size limits
    http:ResponseLimitConfigs responseLimits?;
    # SSL/TLS-related options
    http:ClientSecureSocket secureSocket?;
    # Proxy server related options
    http:ProxyConfig proxy?;
    # Enables the inbound payload validation functionality which provided by the constraint package. Enabled by default
    boolean validation = true;
|};

type AzureOpenAICreateChatCompletionRequestUserMessage record {|
    string content;
    "user" role;
    string name?;
|};

type AzureOpenAICreateChatCompletionRequest record {|
    AzureOpenAICreateChatCompletionRequestUserMessage[1] messages;
    ChatCompletionTool[] tools;
    ChatCompletionToolChoiceOption? tool_choice = ();
|};

type AzureOpenAIChatCompletionResponseMessage record {
    string? content?;
    ChatCompletionMessageToolCalls tool_calls?;
};

type AzureOpenAICreateChatCompletionResponse record {
    record {
        AzureOpenAIChatCompletionResponseMessage message?;
    }[] choices?;
};

isolated function buildHttpClientConfig(AzureOpenAIConnectionConfig config) returns http:ClientConfiguration {
    http:ClientConfiguration httpClientConfig = {
        httpVersion: config.httpVersion,
        timeout: config.timeout,
        forwarded: config.forwarded,
        poolConfig: config.poolConfig,
        compression: config.compression,
        circuitBreaker: config.circuitBreaker,
        retryConfig: config.retryConfig,
        validation: config.validation
    };

    ClientHttp1Settings? http1Settings = config.http1Settings;
    if http1Settings is ClientHttp1Settings {
        httpClientConfig.http1Settings = {...http1Settings};
    }
    http:ClientHttp2Settings? http2Settings = config.http2Settings;
    if http2Settings is http:ClientHttp2Settings {
        httpClientConfig.http2Settings = {...http2Settings};
    }
    http:CacheConfig? cache = config.cache;
    if cache is http:CacheConfig {
        httpClientConfig.cache = cache;
    }
    http:ResponseLimitConfigs? responseLimits = config.responseLimits;
    if responseLimits is http:ResponseLimitConfigs {
        httpClientConfig.responseLimits = responseLimits;
    }
    http:ClientSecureSocket? secureSocket = config.secureSocket;
    if secureSocket is http:ClientSecureSocket {
        httpClientConfig.secureSocket = secureSocket;
    }
    http:ProxyConfig? proxy = config.proxy;
    if proxy is http:ProxyConfig {
        httpClientConfig.proxy = proxy;
    }
    return httpClientConfig;
}

isolated function getEncodedUri(anydata value) returns string|error => url:encode(value.toString(), "UTF8");
