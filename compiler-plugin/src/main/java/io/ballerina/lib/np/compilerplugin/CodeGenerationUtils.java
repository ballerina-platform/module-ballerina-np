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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.ConstantSymbol;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.syntax.tree.InterpolationNode;
import io.ballerina.compiler.syntax.tree.LiteralValueToken;
import io.ballerina.compiler.syntax.tree.NaturalExpressionNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.projects.BuildOptions;
import io.ballerina.projects.DiagnosticResult;
import io.ballerina.projects.ModuleDescriptor;
import io.ballerina.projects.PackageCompilation;
import io.ballerina.projects.directory.BuildProject;
import io.ballerina.projects.util.ProjectUtils;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static io.ballerina.lib.np.compilerplugin.Commons.BAL_EXT;
import static io.ballerina.lib.np.compilerplugin.Commons.CONTENT;
import static io.ballerina.lib.np.compilerplugin.Commons.FILE_PATH;

/**
 * Methods to generate code at compile-time.
 *
 * @since 0.3.0
 */
public class CodeGenerationUtils {

    private static final String TEMP_DIR_PREFIX = "ballerina-np-codegen-diagnostics-dir-";
    private static final String BALLERINA_TOML_FILE = "Ballerina.toml";
    private static final String TRIPLE_BACKTICK_BALLERINA = "```ballerina";
    private static final String TRIPLE_BACKTICK = "```";

    static String generateCodeForFunction(String copilotUrl, String copilotAccessToken, String originalFuncName,
                                          String generatedFuncName, String prompt, HttpClient client,
                                          JsonArray sourceFiles, ModuleDescriptor moduleDescriptor) {
        try {
            String generatedPrompt = generatePrompt(originalFuncName, generatedFuncName, prompt);
            GeneratedCode generatedCode = generateCode(copilotUrl, copilotAccessToken, client, sourceFiles,
                    generatedPrompt);

            updateSourceFilesWithGeneratedContent(sourceFiles, generatedFuncName, generatedCode);
            return repairCode(copilotUrl, copilotAccessToken, generatedFuncName, client, sourceFiles, moduleDescriptor,
                    generatedPrompt, generatedCode);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to generate code, invalid URI for Copilot");
        } catch (ConnectException e) {
            throw new RuntimeException("Failed to connect to Copilot services");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to generate code: " + e.getMessage());
        }
    }

    static String generateCodeForNaturalExpression(String copilotUrl, String copilotAccessToken,
                                                   TypeSymbol expectedType, NaturalExpressionNode naturalExpressionNode,
                                                   HttpClient client, JsonArray sourceFiles,
                                                   SemanticModel semanticModel) {
        try {
            String generatedPrompt = generatePrompt(naturalExpressionNode, expectedType, semanticModel);
            GeneratedCode generatedCode = generateCode(copilotUrl, copilotAccessToken, client, sourceFiles,
                    generatedPrompt);
            // TODO: check if we need to call repair, could get complicated.
            // TODO: validate generated code to ensure only literals and constructors are present, regenerate if not.
            return generatedCode.code;
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to generate code, invalid URI for Copilot");
        } catch (ConnectException e) {
            throw new RuntimeException("Failed to connect to Copilot services");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to generate code: " + e.getMessage());
        }
    }

    private static GeneratedCode generateCode(String copilotUrl, String copilotAccessToken, HttpClient client,
                                              JsonArray sourceFiles, String generatedPrompt)
            throws URISyntaxException, IOException, InterruptedException {
        JsonObject codeGenerationPayload = constructCodeGenerationPayload(generatedPrompt, sourceFiles);
        HttpRequest codeGenerationRequest = HttpRequest.newBuilder()
                .uri(new URI(copilotUrl + "/code"))
                .header("Authorization", "Bearer " + copilotAccessToken)
                .POST(HttpRequest.BodyPublishers.ofString(codeGenerationPayload.toString())).build();
        Stream<String> lines = client.send(codeGenerationRequest, HttpResponse.BodyHandlers.ofLines()).body();
        return extractGeneratedFunctionCode(lines);
    }

    private static String repairCode(String copilotUrl, String copilotAccessToken, String generatedFuncName,
                                     HttpClient client, JsonArray sourceFiles, ModuleDescriptor moduleDescriptor,
                                     String generatedPrompt, GeneratedCode generatedCode)
            throws IOException, URISyntaxException, InterruptedException {
        String generatedFunctionSrc = repairIfDiagnosticsExist(copilotUrl, copilotAccessToken, client, sourceFiles,
                moduleDescriptor, generatedFuncName, generatedPrompt, generatedCode);
        return repairIfDiagnosticsExist(copilotUrl, copilotAccessToken, client, sourceFiles, moduleDescriptor,
                generatedFuncName, generatedPrompt,
                new GeneratedCode(generatedFunctionSrc, generatedCode.functions));
    }

    private static String repairIfDiagnosticsExist(String copilotUrl, String copilotAccessToken, HttpClient client,
                                                   JsonArray sourceFiles, ModuleDescriptor moduleDescriptor,
                                                   String generatedFuncName, String generatedPrompt,
                                                   GeneratedCode generatedCode)
            throws IOException, URISyntaxException, InterruptedException {
        Optional<JsonArray> diagnostics = getDiagnostics(sourceFiles, moduleDescriptor);
        if (diagnostics.isEmpty()) {
            return generatedCode.code;
        }

        String repairResponse = repairCode(copilotUrl, copilotAccessToken, generatedFuncName, client, sourceFiles,
                generatedPrompt, generatedCode, diagnostics.get());

        if (hasBallerinaCodeSnippet(repairResponse)) {
            String generatedFunctionSrc = extractBallerinaCodeSnippet(repairResponse);
            sourceFiles.get(sourceFiles.size() - 1).getAsJsonObject().addProperty(CONTENT, generatedFunctionSrc);
            return generatedFunctionSrc;
        }
        return generatedCode.code;
    }

    private static String repairCode(String copilotUrl, String copilotAccessToken, String generatedFuncName,
                                     HttpClient client, JsonArray updatedSourceFiles, String generatedPrompt,
                                     GeneratedCode generatedCode, JsonArray diagnostics)
            throws URISyntaxException, IOException, InterruptedException {
        JsonObject codeReparationPayload =
                constructCodeReparationPayload(generatedPrompt, generatedFuncName, generatedCode.functions,
                        updatedSourceFiles, diagnostics);
        HttpRequest codeReparationRequest = HttpRequest.newBuilder()
                .uri(new URI(copilotUrl + "/code/repair"))
                .header("Authorization", "Bearer " + copilotAccessToken)
                .POST(HttpRequest.BodyPublishers.ofString(codeReparationPayload.toString())).build();
        String body = client.send(codeReparationRequest, HttpResponse.BodyHandlers.ofString()).body();
        return JsonParser.parseString(body).getAsJsonObject()
                .getAsJsonPrimitive("repairResponse").getAsString();
    }

    private static Optional<JsonArray> getDiagnostics(JsonArray sourceFiles, ModuleDescriptor moduleDescriptor)
            throws IOException {
        BuildProject project = createProject(sourceFiles, moduleDescriptor);
        PackageCompilation compilation = project.currentPackage().getCompilation();
        DiagnosticResult diagnosticResult = compilation.diagnosticResult();

        if (diagnosticResult.errorCount() == 0) {
            return Optional.empty();
        }

        JsonArray diagnostics = new JsonArray();
        for (Diagnostic diagnostic : diagnosticResult.diagnostics()) {
            DiagnosticInfo diagnosticInfo = diagnostic.diagnosticInfo();
            if (diagnosticInfo.severity() != DiagnosticSeverity.ERROR) {
                continue;
            }
            diagnostics.add(diagnostic.toString());
        }

        return Optional.of(diagnostics);
    }

    private static BuildProject createProject(JsonArray sourceFiles, ModuleDescriptor moduleDescriptor)
            throws IOException {
        Path tempProjectDir = Files.createTempDirectory(TEMP_DIR_PREFIX + System.currentTimeMillis());
        tempProjectDir.toFile().deleteOnExit();

        for (JsonElement sourceFile : sourceFiles) {
            JsonObject sourceFileObj = sourceFile.getAsJsonObject();
            File file = File.createTempFile(sourceFileObj.get(FILE_PATH).getAsString(), BAL_EXT,
                    tempProjectDir.toFile());
            file.deleteOnExit();

            try (FileWriter fileWriter = new FileWriter(file, Charset.defaultCharset())) {
                fileWriter.write(sourceFileObj.get(CONTENT).getAsString());
            }
        }

        Path ballerinaTomlPath = tempProjectDir.resolve(BALLERINA_TOML_FILE);
        File balTomlFile = Files.createFile(ballerinaTomlPath).toFile();
        balTomlFile.deleteOnExit();

        try (FileWriter fileWriter = new FileWriter(balTomlFile, Charset.defaultCharset())) {
            fileWriter.write(String.format("""
                [package]
                org = "%s"
                name = "%s"
                name = "%s"
                """,
                    moduleDescriptor.org().value(),
                    moduleDescriptor.packageName().value(),
                    moduleDescriptor.version().value()));
        }

        BuildOptions buildOptions = BuildOptions.builder()
                .setExperimental(true)
                .targetDir(ProjectUtils.getTemporaryTargetPath())
                .build();
        return BuildProject.load(tempProjectDir, buildOptions);
    }

    private static GeneratedCode extractGeneratedFunctionCode(Stream<String> lines) {
        String[] linesArr = lines.toArray(String[]::new);
        int length = linesArr.length;

        if (length == 1) {
            JsonObject jsonObject = JsonParser.parseString(linesArr[0]).getAsJsonObject();
            if (jsonObject.has("error_message")) {
                throw new RuntimeException(jsonObject.get("error_message").getAsString());
            }
        }

        StringBuilder responseBody = new StringBuilder();
        JsonArray functions = null;

        int index = 0;
        while (index < length) {
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
        return new GeneratedCode(extractBallerinaCodeSnippet(responseBodyString), functions);
    }

    private static boolean hasBallerinaCodeSnippet(String responseBodyString) {
        return responseBodyString.contains(TRIPLE_BACKTICK_BALLERINA) && responseBodyString.contains(TRIPLE_BACKTICK);
    }

    private static String extractBallerinaCodeSnippet(String responseBodyString) {
        return responseBodyString.substring(responseBodyString.indexOf(TRIPLE_BACKTICK_BALLERINA) + 12,
                responseBodyString.lastIndexOf(TRIPLE_BACKTICK));
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
                        
                        Respond with ONLY THE GENERATED FUNCTION AND ANY IMPORTS REQUIRED BY THE GENERATED FUNCTION.
                        """,
                generatedFuncName, originalFuncName, prompt, generatedFuncName, originalFuncName);
    }

    private static String generatePrompt(NaturalExpressionNode naturalExpressionNode,
                                         TypeSymbol expectedType, SemanticModel semanticModel) {
        NodeList<Node> userPromptContent = naturalExpressionNode.prompt();
        StringBuilder sb = new StringBuilder(String.format("""
                Generate a value expression to satisfy the following requirement using only Ballerina literals and
                constructor expressions. The expression should be self-contained and should not have references.
                
                Ballerina literals:
                1. nil-literal :=  () | null
                2. boolean-literal := true | false
                3. numeric-literal - int, float, and decimal values (e.g., 1, 2.0, 3f, 4.5d)
                4. string-literal - double quoted strings (e.g., "foo") or
                    string-template literal without interpolations (e.g., string `foo`)
                
                Ballerina constructor expressions:
                1. List constructor expression - e.g., [1, 2]
                2. Mapping constructor expression - e.g., {a: 1, b: 2, "c": 3}
                3. Table constructor expression - e.g., table [{a: 1, b: 2}, {a: 2, b: 4}]
                
                The value should belong to the type '%s'. This value will be used in the code in place of the
                `const natural {...}` expression with the requirement.
                
                Respond with ONLY THE VALUE EXPRESSION.
                
                Requirement:
                """, expectedType.signature()));

        for (int i = 0; i < userPromptContent.size(); i++) {
            Node node = userPromptContent.get(i);
            if (node instanceof LiteralValueToken literalValueToken) {
                sb.append(literalValueToken.text());
                continue;
            }

            sb.append(((ConstantSymbol) semanticModel.symbol(((InterpolationNode) node).expression()).get())
                    .resolvedValue().get());
        }
        return sb.toString();
    }

    private static void updateSourceFilesWithGeneratedContent(JsonArray sourceFiles, String generatedFuncName,
                                                              GeneratedCode generatedCode) {
        JsonObject sourceFile = new JsonObject();
        sourceFile.addProperty(FILE_PATH, String.format("generated/functions_%s.bal", generatedFuncName));
        sourceFile.addProperty(CONTENT, generatedCode.code);
        sourceFiles.add(sourceFile);
    }

    private static JsonObject constructCodeReparationPayload(String generatedPrompt, String generatedFuncName,
                                                             JsonArray functions, JsonArray sourceFiles,
                                                             JsonArray diagnostics) {
        JsonObject payload = new JsonObject();

        payload.addProperty(
                "usecase", String.format("Fix issues in the generated '%s' function. " +
                        "Do not change anything other than the function body", generatedFuncName));

        payload.add("sourceFiles", sourceFiles);

        JsonObject chatHistoryMember = new JsonObject();
        chatHistoryMember.addProperty("actor", "user");
        chatHistoryMember.addProperty("message", generatedPrompt);
        JsonArray chatHistory = new JsonArray();
        chatHistory.add(chatHistoryMember);
        payload.add("chatHistory", chatHistory);

        payload.add("functions", functions);

        payload.add("diagnostics", diagnostics);

        return payload;
    }
}
