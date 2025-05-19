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

type Blog record {
    string title;
    string content;
};

type Review record {|
    int rating;
    string comment;
|};

final readonly & Blog blog1 = {
    // Generated.
    title: "Tips for Growing a Beautiful Garden",
    content: string `Spring is the perfect time to start your garden. 
        Begin by preparing your soil with organic compost and ensure proper drainage. 
        Choose plants suitable for your climate zone, and remember to water them regularly. 
        Don't forget to mulch to retain moisture and prevent weeds.`
};

final readonly & Blog blog2 = {
    // Generated.
    title: "Essential Tips for Sports Performance",
    content: string `Success in sports requires dedicated preparation and training.
        Begin by establishing a proper warm-up routine and maintaining good form.
        Choose the right equipment for your sport, and stay consistent with training.
        Don't forget to maintain proper hydration and nutrition for optimal performance.`
};

final string review = "{\"rating\": 8, \"comment\": \"Talks about essential aspects of sports performance including warm-up, form, equipment, and nutrition.\"}";

final string expectedPromptStringForRateBlog = string `Rate this blog out of 10.
        Title: ${blog1.title}
        Content: ${blog1.content}`;

final string expectedPromptStringForRateBlog2 = string `Please rate this blog out of 10.
        Title: ${blog2.title}
        Content: ${blog2.content}`;

final string expectedPromptStringForRateBlog3 = string `What is 1 + 1?`;

final string expectedPromptStringForRateBlog4 = string `Tell me name and the age of the top 10 world class cricketers`;

final string expectedPromptStringForBalProgram = string `What's the output of the Ballerina code below?

    ${"```"}ballerina
    import ballerina/io;

    public function main() {
        int x = 10;
        int y = 20;
        io:println(x + y);
    }
    ${"```"}`;

final string expectedPromptStringForCountry = string `Which country is known as the pearl of the Indian Ocean?`;

final readonly & FunctionParameters expectedParamterSchemaStringForRateBlog =
    {"type": "object", "properties": {"result": {"type": "integer"}}};

final readonly & FunctionParameters expectedParamterSchemaStringForRateBlog2 =
    {"$schema": "https://json-schema.org/draft/2020-12/schema", "type": "object", "properties": {"rating": {"type": "integer"}, "comment": {"type": "string"}}, "required": ["rating", "comment"]};

final readonly & FunctionParameters expectedParamterSchemaStringForRateBlog3 =
    {"type": "object", "properties": {"result": {"type": "boolean"}}};

final readonly & FunctionParameters expectedParamterSchemaStringForRateBlog4 =
    {"$schema": "https://json-schema.org/draft/2020-12/schema", "type": "object", "properties": {"result": {"type": "array", "items": {"$schema": "https://json-schema.org/draft/2020-12/schema", "type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]}}}};

final readonly & FunctionParameters expectedParamterSchemaStringForBalProgram =
    {"type": "object", "properties": {"result": {"type": "integer"}}};

final readonly & FunctionParameters expectedParamterSchemaStringForCountry =
    {"type": "object", "properties": {"result": {"type": "string"}}};
