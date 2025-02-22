/*
 *  Copyright (c) 2025, WSO2 Inc. (http://www.wso2.org).
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.lib.np.compilerplugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Stream;

/**
 * Methods to generate code at compile-time.
 *
 * @since 0.3.0
 */
public class CodeGenerationUtils {

    static String generateCode(String copilotUri, String diagnosticsServiceUri, String originalFuncName,
                               String generatedFuncName, String prompt, HttpClient client, JsonArray sourceFiles) {
        try {
            String generatedPrompt = generatePrompt(originalFuncName, generatedFuncName, prompt);
            GeneratedCode generatedCode = generatedCode(copilotUri, client, sourceFiles, generatedPrompt);
            JsonArray diagnostics = getDiagnostics(diagnosticsServiceUri, client, sourceFiles);
            String repairResponse = repairCode(copilotUri, generatedFuncName, client, sourceFiles, generatedPrompt,
                    generatedCode, diagnostics);

            String generatedFunctionSrc;
            if (hasBallerinaCodeSnippet(repairResponse)) {
                generatedFunctionSrc = extractBallerinaCodeSnippet(repairResponse);
                sourceFiles.get(sourceFiles.size() - 1).getAsJsonObject()
                        .addProperty("content", generatedFunctionSrc);
            } else {
                generatedFunctionSrc = generatedCode.code;
            }
            return generatedFunctionSrc;
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to generate code, invalid URI for Copilot");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to generate code: " + e.getMessage());
        }
    }

    private static GeneratedCode generatedCode(String copilotUri, HttpClient client, JsonArray sourceFiles,
                                               String generatedPrompt)
            throws URISyntaxException, IOException, InterruptedException {
        JsonObject codeGenerationPayload = constructCodeGenerationPayload(generatedPrompt, sourceFiles);
        HttpRequest codeGenerationRequest = HttpRequest.newBuilder()
                .uri(new URI(copilotUri + "/code"))
                .POST(HttpRequest.BodyPublishers.ofString(codeGenerationPayload.toString())).build();
        Stream<String> lines = client.send(codeGenerationRequest, HttpResponse.BodyHandlers.ofLines()).body();
        return extractGeneratedFunctionCode(lines);
    }

    private static String repairCode(String copilotUri, String generatedFuncName, HttpClient client,
                                     JsonArray sourceFiles, String generatedPrompt, GeneratedCode generatedCode,
                                     JsonArray diagnostics)
            throws URISyntaxException, IOException, InterruptedException {
        JsonObject codeReparationPayload =
                constructCodeReparationPayload(generatedPrompt, generatedFuncName, generatedCode, sourceFiles,
                        diagnostics);
        HttpRequest codeReparationRequest = HttpRequest.newBuilder()
                .uri(new URI(copilotUri + "/code/repair"))
                .POST(HttpRequest.BodyPublishers.ofString(codeReparationPayload.toString())).build();
        String body = client.send(codeReparationRequest, HttpResponse.BodyHandlers.ofString()).body();
        return JsonParser.parseString(body).getAsJsonObject()
                .getAsJsonPrimitive("repairResponse").getAsString();
    }

    private static JsonArray getDiagnostics(String diagnosticsServiceUri, HttpClient client, JsonArray sourceFiles)
            throws URISyntaxException, IOException, InterruptedException {
        JsonObject diagnosticsIdentificationPayload = constructDiagnosticsIdentificationPayload(sourceFiles);
        HttpRequest diagnosticsIdentificationRequest = HttpRequest.newBuilder()
                .uri(new URI(diagnosticsServiceUri + "/project/diagnostics"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(diagnosticsIdentificationPayload.toString())).build();
        String body = client.send(diagnosticsIdentificationRequest, HttpResponse.BodyHandlers.ofString()).body();
        return JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("diagnostics");
    }

    private static GeneratedCode extractGeneratedFunctionCode(Stream<String> lines) {
        String[] linesArr = lines.toArray(String[]::new);
        StringBuilder responseBody = new StringBuilder();
        JsonArray functions = null;

        int index = 0;
        while (index < linesArr.length) {
            String line = linesArr[index];

            if (line.isBlank()) {
                index++;
                continue;
            }

            if ("event: content_block_delta".equals(line)) {
                line = linesArr[++index].substring(6);
                responseBody.append(JsonParser.parseString(line).getAsJsonObject()
                        .getAsJsonPrimitive("text").getAsString());
                continue;
            }

            if ("event: functions".equals(line)) {
                line = linesArr[++index].substring(6);
                functions = JsonParser.parseString(line).getAsJsonArray();
                continue;
            }

            index++;
        }

        String responseBodyString = responseBody.toString();
        String code = extractBallerinaCodeSnippet(responseBodyString);
        return new GeneratedCode(code, functions);
    }

    private static boolean hasBallerinaCodeSnippet(String responseBodyString) {
        return responseBodyString.contains("```ballerina") && responseBodyString.contains("```");
    }

    private static String extractBallerinaCodeSnippet(String responseBodyString) {
        return responseBodyString.substring(responseBodyString.indexOf("```ballerina") + 12,
                responseBodyString.lastIndexOf("```"));
    }

    private record GeneratedCode(String code, JsonArray functions) { }

    private static JsonObject constructCodeGenerationPayload(String prompt, JsonArray sourceFiles) {
        JsonObject payload = new JsonObject();
        payload.addProperty("usecase", prompt);
        payload.add("sourceFiles", sourceFiles);
        return payload;
    }

    private static String generatePrompt(String originalFuncName, String generatedFuncName, String prompt) {
        return String.format("""
                        Generate a function named '%s' with the code that needs \
                        to go in the '%s' function to satisfy the following user prompt:
                        ${"```"}   \s
                        %s
                        ${"```"}   \s
                        The '%s' function should have exactly the same signature as the '%s' function.
                        Use only the parameters passed to the function and module-level clients that are clients \
                        from the ballerina and ballerinax module in the generated code. Respond with only the \
                        generated code, nothing else. Ensure that there are NO compile-time errors.
                        Where possible, use query expressions over verbose foreach loops.""",
                generatedFuncName, originalFuncName, prompt, generatedFuncName, originalFuncName);
    }

    private static JsonObject constructCodeReparationPayload(String generatedPrompt, String generatedFuncName,
                                                             GeneratedCode generatedCode, JsonArray sourceFiles,
                                                             JsonArray diagnostics) {
        JsonObject payload = new JsonObject();

        payload.addProperty(
                "usecase", String.format("Fix issues in the generated '%s' function. " +
                        "Do not change anything other than the function body", generatedFuncName));

        JsonObject sourceFile = new JsonObject();
        sourceFile.addProperty("filePath", String.format("generated/functions_%s.bal", generatedFuncName));
        sourceFile.addProperty("content", generatedCode.code);
        sourceFiles.add(sourceFile);
        payload.add("sourceFiles", sourceFiles);

        JsonObject chatHistoryMember = new JsonObject();
        chatHistoryMember.addProperty("actor", "user");
        chatHistoryMember.addProperty("message", generatedPrompt);
        JsonArray chatHistory = new JsonArray();
        chatHistory.add(chatHistoryMember);
        payload.add("chatHistory", chatHistory);

        payload.add("functions", generatedCode.functions);

        payload.add("diagnostics", diagnostics);

        return payload;
    }

    private static JsonObject constructDiagnosticsIdentificationPayload(JsonArray sourceFiles) {
        JsonObject projectSource = new JsonObject();
        projectSource.add("sourceFiles", sourceFiles);
        JsonObject payload = new JsonObject();
        payload.add("projectSource", projectSource);
        return payload;
    }
}
