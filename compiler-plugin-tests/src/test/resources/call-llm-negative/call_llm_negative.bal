// Copyright (c) 2025 WSO2 LLC. (http://www.wso2.org).
//
// WSO2 LLC. licenses this file to you under the Apache License,
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
import ballerina/np;

anydata|error m = check np:callLlm(`What day is it today?`);

function whichDay(string date, ai:ModelProvider m) returns string|xml|error
    => check np:callLlm(`What day was ${date}?`, {model: m});

type Record record {
    xml x;
};

anydata n = check np:callLlm(`Give me a stock quote as an XML value`, {}, Record);
anydata o = check np:callLlm(`Give me a stock quote as an XML value`, expectedResponseTypedesc = Record);

anydata a = check np:callLlm(`Give me a stock quote as an XML value`, expectedResponseTypedesc = string); // OK
