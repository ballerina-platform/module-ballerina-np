# Overview

The natural programming library module provides seamless integration with Large Language Models (LLMs). It offers a first-class approach to integrate LLM calls with automatic detection of expected response formats and parsing of responses to corresponding Ballerina types.

This simplifies working with AI models by handling the communication and data conversion automatically.

## The `natural` expression

A natural expression becomes an LLM call with the content specified within the natural expression becoming the prompt. The JSON schema generated from the expected type for the expression is incorporated into the LLM call and the response from the LLM is automatically parsed to the type used as the expected type.

```ballerina
import ballerina/io;

final readonly & string[] categories = [
    "Tech Innovations & Software Development",
    "Programming Languages & Frameworks",
    "Open Source & Community-Driven Development"
];

public type Blog record {|
    string title;
    string content;
|};

type Review record {|
    string? suggestedCategory;
    int rating;
|};

public function main() returns error? {
    Blog blog = {
        title: "The Future of Ballerina",
        content: "Ballerina is an open source, cloud-native programming language optimized for integration."
    };

    Review review = check natural {
        You are an expert content reviewer for a blog site that 
        categorizes posts under the following categories: ${categories}

        Your tasks are:
        1. Suggest a suitable category for the blog from exactly the specified categories. 
           If there is no match, use null.

        2. Rate the blog post on a scale of 1 to 10 based on the following criteria:
        - **Relevance**: How well the content aligns with the chosen category.
        - **Depth**: The level of detail and insight in the content.
        - **Clarity**: How easy it is to read and understand.
        - **Originality**: Whether the content introduces fresh perspectives or ideas.
        - **Language Quality**: Grammar, spelling, and overall writing quality.

        Here is the blog post content:

        Title: ${blog.title}
        Content: ${blog.content}
    };
    io:println(review.suggestedCategory);
    io:println(review.rating);
}
```

An expression-bodied function in which the expression is a natural function is identified as a natural function. These functions  become a type-safe approach to share and reuse prompts.

The function can be used in the code similar to any other function.

```ballerina
public isolated function reviewBlog(Blog blog) returns Review|error => natural {
    You are an expert content reviewer for a blog site that 
    categorizes posts under the following categories: ${categories}

    Your tasks are:
    1. Suggest a suitable category for the blog from exactly the specified categories. 
        If there is no match, use null.

    2. Rate the blog post on a scale of 1 to 10 based on the following criteria:
    - **Relevance**: How well the content aligns with the chosen category.
    - **Depth**: The level of detail and insight in the content.
    - **Clarity**: How easy it is to read and understand.
    - **Originality**: Whether the content introduces fresh perspectives or ideas.
    - **Language Quality**: Grammar, spelling, and overall writing quality.

    Here is the blog post content:

    Title: ${blog.title}
    Content: ${blog.content}
};

public function main() returns error? {
    Blog blog = {
        title: "The Future of Ballerina",
        content: "Ballerina is an open source, cloud-native programming language optimized for integration."
    };

    Review review = check reviewBlog(blog);
    io:println(review.suggestedCategory);
    io:println(review.rating);
}
```

### Configuring the model

The model to use can be set either by configuration or by specifying the model as an argument in the natural expression.

1. Configuration

   Values need to be provided for the `defaultModelConfig` configurable value. E.g., add the relevant configuration in the Config.toml file as follows for Azure OpenAI:

    ```toml
    [ballerina.np.defaultModelConfig]
    serviceUrl = "<SERVICE_URL>"
    deploymentId = "<DEPLOYMENT_ID>"
    apiVersion = "<API_VERSION>"
    connectionConfig.auth.apiKey = "<YOUR_API_KEY>"
    ```

2. Model in a parameter

   Alternatively, to have more control over the model for each expression, the model can be specified in the natural expression itself.

    ```ballerina
    import ballerina/np;

    public isolated function reviewBlog(Blog blog, ai:ModelProvider model) 
            returns Review|error => natural (model) {
        You are an expert content reviewer for a blog site that 
            categorizes posts under the following categories: ${categories}

        ...

        Here is the blog post content:

        Title: ${blog.title}
        Content: ${blog.content}
    };
    ```


The `ballerinax/np` package provides implementations of `ai:ModelProvider` for different LLM providers:

- `np.openai:ModelProvider` for Open AI
- `np.azure.openai:ModelProvider` for Azure Open AI

A model of these types can be initialized and provided as an argument for the `model` parameter.

```ballerina
import ballerinax/np.azure.openai as azureOpenAI;

configurable string apiKey = ?;
configurable string serviceUrl = ?;
configurable string deploymentId = ?;
configurable string apiVersion = ?;

final ai:ModelProvider azureOpenAIModel = check new azureOpenAI:Model({
       serviceUrl, connectionConfig: {auth: {apiKey}}}, deploymentId, apiVersion);

Review review = check reviewBlog(blog, azureOpenAIModel);
```

## The `np:callLlm` function

The `np:callLlm` function is an alternative to using a natural expression. The compiler transforms natural expressions to `np:callLlm` calls internally.

The function accepts a prompt of type `np:Prompt` and optionally, an `np:Conext` value with the `model` field of type `ai:ModelProvider`. If the model is not specified, it has to be configured via the `defaultModelConfig` configurable variable. The function is dependently-typed and uses the inferred typedesc parameter to construct the JSON schema for the required response format and bind the response data to the expected type.

```ballerina
// Where `blog` is in scope.
Review review = check np:callLlm(`You are an expert content reviewer for a blog site that 
            categorizes posts under the following categories: ${categories}

            ...

            Here is the blog post content:

            Title: ${blog.title}
            Content: ${blog.content}`, {model: azureOpenAIModel});
```
