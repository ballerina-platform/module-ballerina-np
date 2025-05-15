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
import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.AnnotationAttachmentSymbol;
import io.ballerina.compiler.api.symbols.AnnotationSymbol;
import io.ballerina.compiler.api.symbols.ExternalFunctionSymbol;
import io.ballerina.compiler.api.symbols.ModuleSymbol;
import io.ballerina.compiler.api.values.ConstantValue;
import io.ballerina.compiler.syntax.tree.BaseNodeModifier;
import io.ballerina.compiler.syntax.tree.DefaultableParameterNode;
import io.ballerina.compiler.syntax.tree.ExpressionFunctionBodyNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.ExternalFunctionBodyNode;
import io.ballerina.compiler.syntax.tree.FunctionBodyNode;
import io.ballerina.compiler.syntax.tree.FunctionCallExpressionNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.IdentifierToken;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.ImportOrgNameNode;
import io.ballerina.compiler.syntax.tree.ImportPrefixNode;
import io.ballerina.compiler.syntax.tree.IncludedRecordParameterNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.NaturalExpressionNode;
import io.ballerina.compiler.syntax.tree.NodeFactory;
import io.ballerina.compiler.syntax.tree.NodeParser;
import io.ballerina.compiler.syntax.tree.ParameterNode;
import io.ballerina.compiler.syntax.tree.RequiredParameterNode;
import io.ballerina.compiler.syntax.tree.RestParameterNode;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.TreeModifier;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleId;
import io.ballerina.projects.Package;
import io.ballerina.projects.ProjectKind;
import io.ballerina.projects.plugins.ModifierTask;
import io.ballerina.projects.plugins.SourceModifierContext;
import io.ballerina.tools.text.TextDocument;
import io.ballerina.tools.text.TextDocuments;
import org.ballerinalang.formatter.core.Formatter;
import org.ballerinalang.formatter.core.FormatterException;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static io.ballerina.lib.np.compilerplugin.CodeGenerationUtils.generateCode;
import static io.ballerina.lib.np.compilerplugin.Utils.MODULE_NAME;
import static io.ballerina.lib.np.compilerplugin.Utils.ORG_NAME;
import static io.ballerina.lib.np.compilerplugin.Utils.PROMPT;
import static io.ballerina.lib.np.compilerplugin.Utils.RIGHT_DOUBLE_ARROW;
import static io.ballerina.lib.np.compilerplugin.Utils.SEMICOLON;
import static io.ballerina.lib.np.compilerplugin.Utils.hasAnnotation;

/**
 * Code modification task to replace generate code based on a prompt and replace.
 *
 * @since 0.3.0
 */
public class CompileTimePromptAsCodeCodeModificationTask implements ModifierTask<SourceModifierContext> {

    private static final String PROMPT_TYPE = "Prompt";
    private static final String GENERATE_CODE_ANNOT = "GenerateCode";

    private static final String BAL_EXT = ".bal";
    private static final String GENERATED_FUNCTION_SUFFIX = "NPGenerated";
    private static final String GENERATED_DIRECTORY = "generated";
    private static final String GENERATED_FUNC_FILE_NAME_SUFFIX = "_np_generated" + BAL_EXT;
    private static final String FILE_PATH = "filePath";

    private static final String copilotUri;
    private static final String diagnosticsServiceUri;

    static {
        copilotUri = System.getenv(BAL_COPILOT_URI);
        diagnosticsServiceUri = System.getenv(BAL_DIAGNOSTICS_SERVER_URI);
    }

    @Override
    public void modify(SourceModifierContext modifierContext) {
        Package currentPackage = modifierContext.currentPackage();
        boolean isSingleBalFileMode = currentPackage.project().kind() == ProjectKind.SINGLE_FILE_PROJECT;
        Path sourceRoot = currentPackage.project().sourceRoot();

        for (ModuleId moduleId : currentPackage.moduleIds()) {
            Module module = currentPackage.module(moduleId);
            SemanticModel semanticModel = currentPackage.getCompilation().getSemanticModel(moduleId);

            for (DocumentId documentId: module.documentIds()) {
                Document document = module.document(documentId);
                if (npGeneratedFile(document)) {
                    modifierContext.modifySourceFile(TextDocuments.from(""), documentId);
                    continue;
                }

                modifierContext.modifySourceFile(
                        modifyDocument(document, semanticModel, module, isSingleBalFileMode, sourceRoot), documentId);
            }

            for (DocumentId documentId: module.testDocumentIds()) {
                Document document = module.document(documentId);
                modifierContext.modifyTestSourceFile(
                        modifyDocument(document, semanticModel, module, isSingleBalFileMode, sourceRoot), documentId);
            }
        }
    }

    private static TextDocument modifyDocument(Document document, SemanticModel semanticModel, Module module,
                                               boolean isSingleBalFileMode, Path sourceRoot) {
        ModulePartNode modulePartNode = document.syntaxTree().rootNode();
        List<ImportDeclarationNode> newImports = new ArrayList<>();
        List<ModuleMemberDeclarationNode> newMembers = new ArrayList<>();
        CodeGenerator codeGenerator = new CodeGenerator(semanticModel, module, newImports,
                newMembers, isSingleBalFileMode, sourceRoot, document);
        ModulePartNode newRoot = (ModulePartNode) modulePartNode.apply(codeGenerator);
        newRoot = newRoot.modify(
                newRoot.imports().addAll(newImports), newRoot.members().addAll(newMembers), newRoot.eofToken());
        return document.syntaxTree().modifyWith(newRoot).textDocument();
    }

    private static class CodeGenerator extends BaseNodeModifier {
        private final SemanticModel semanticModel;
        private final Module module;
        private final List<ImportDeclarationNode> newImports;
        private final List<ModuleMemberDeclarationNode> newMembers;
        private final boolean isSingleBalFileMode;
        private final Path sourceRoot;
        private final Document document;

        private HttpClient client = null;
        private JsonArray sourceFiles = null;

        public CodeGenerator(SemanticModel semanticModel, Module module,
                             List<ImportDeclarationNode> newImports, List<ModuleMemberDeclarationNode> newMembers,
                             boolean isSingleBalFileMode,
                             Path sourceRoot, Document document) {
            this.semanticModel = semanticModel;
            this.module = module;
            this.newImports = newImports;
            this.newMembers = newMembers;
            this.isSingleBalFileMode = isSingleBalFileMode;
            this.sourceRoot = sourceRoot;
            this.document = document;
        }

        @Override
        public ImportDeclarationNode transform(ImportDeclarationNode importDeclarationNode) {
            Optional<ImportOrgNameNode> importOrgNameNode = importDeclarationNode.orgName();
            // Allow the not present case for module tests.
            if (importOrgNameNode.isPresent() && !ORG_NAME.equals(importOrgNameNode.get().orgName().text())) {
                return importDeclarationNode;
            }

            SeparatedNodeList<IdentifierToken> moduleName = importDeclarationNode.moduleName();
            if (moduleName.size() > 1 || !MODULE_NAME.equals(moduleName.iterator().next().text())) {
                return importDeclarationNode;
            }

            Optional<ImportPrefixNode> prefix = importDeclarationNode.prefix();
            this.npPrefixIfImported = Optional.of(prefix.isEmpty() ? MODULE_NAME : prefix.get().prefix().text());
            return importDeclarationNode;
        }

        @Override
        public FunctionDefinitionNode transform(FunctionDefinitionNode functionDefinition) {
            FunctionBodyNode functionBodyNode = functionDefinition.functionBody();

            if (!(functionBodyNode instanceof ExternalFunctionBodyNode functionBody)) {
                return (FunctionDefinitionNode) super.transform(functionDefinition);
            }

            if (hasAnnotation(functionBody, npPrefix, GENERATE_CODE_ANNOT)) {
                String funcName = functionDefinition.functionName().text();
                String generatedFuncName = funcName.concat(GENERATED_FUNCTION_SUFFIX);
                String prompt = getPrompt(functionDefinition, semanticModel);
                String generatedCode = generateCode(copilotUri, diagnosticsServiceUri, funcName, generatedFuncName,
                        prompt, getHttpClient(),
                        this.getSourceFilesWithoutFileGeneratedForCurrentFunc(generatedFuncName));
                handleGeneratedCode(funcName, generatedCode);
                ExpressionFunctionBodyNode expressionFunctionBody =
                        NodeFactory.createExpressionFunctionBodyNode(
                                RIGHT_DOUBLE_ARROW,
                                createGeneratedFunctionCallExpression(functionDefinition, generatedFuncName),
                                SEMICOLON);
                return functionDefinition.modify().withFunctionBody(expressionFunctionBody).apply();
            }

            if (this.isSingleBalFileMode) {
                // Validator logs an error for this.
                return (FunctionDefinitionNode) super.transform(functionDefinition);
            }

            String funcName = functionDefinition.functionName().text();
            String generatedFuncName = funcName.concat(GENERATED_FUNCTION_SUFFIX);
            String prompt = getPrompt(functionDefinition, semanticModel);
            String generatedCode = generateCodeForFunction(copilotUri, diagnosticsServiceUri, funcName,
                    generatedFuncName, prompt, getHttpClient(),
                    this.getSourceFilesWithoutFileGeneratedForCurrentFunc(generatedFuncName));
            handleGeneratedCode(funcName, generatedCode);
            ExpressionFunctionBodyNode expressionFunctionBody =
                    NodeFactory.createExpressionFunctionBodyNode(
                            RIGHT_DOUBLE_ARROW,
                            createGeneratedFunctionCallExpression(functionDefinition, generatedFuncName),
                            SEMICOLON);
            return functionDefinition.modify().withFunctionBody(expressionFunctionBody).apply();
        }

        @Override
        public ExpressionNode transform(NaturalExpressionNode naturalExpressionNode) {
            if (isRuntimeNaturalExpression(naturalExpressionNode)) {
                return naturalExpressionNode;
            }
            String generatedCode = generateCodeForNaturalExpression(copilotUri,
                    semanticModel.expectedType(document, naturalExpressionNode.lineRange().startLine()).get(),
                    naturalExpressionNode, getHttpClient(), this.getSourceFiles(), semanticModel);
            return NodeParser.parseExpression(generatedCode);
        }

        private void handleGeneratedCode(String originalFuncName, String generatedCode) {
            ModulePartNode modulePartNode = NodeParser.parseModulePart(generatedCode);
            persistInGeneratedDirectory(originalFuncName, generatedCode);
            this.newImports.addAll(modulePartNode.imports().stream().toList());
            this.newMembers.addAll(modulePartNode.members().stream().toList());
        }

        private HttpClient getHttpClient() {
            if (this.client != null) {
                return this.client;
            }

            this.client = HttpClient.newHttpClient();
            return this.client;
        }

        private JsonArray getSourceFilesWithoutFileGeneratedForCurrentFunc(String generatedFuncName) {
            JsonArray sourceFiles = this.getSourceFiles();
            JsonArray filteredSourceFiles = new JsonArray(sourceFiles.size());
            for (JsonElement sourceFile : sourceFiles) {
                if (!getGeneratedBalFileName(generatedFuncName).equals(
                        sourceFile.getAsJsonObject().get(FILE_PATH).getAsString())) {
                    filteredSourceFiles.add(sourceFile);
                }
            }
            return filteredSourceFiles;
        }

        private JsonArray getSourceFiles() {
            if (this.sourceFiles != null) {
                return this.sourceFiles;
            }

            this.sourceFiles = getSourceFiles(this.module);
            return this.sourceFiles;
        }

        private static JsonArray getSourceFiles(Module module) {
            JsonArray sourceFiles = new JsonArray();
            for (DocumentId documentId : module.documentIds()) {
                Document document = module.document(documentId);
                JsonObject sourceFile = new JsonObject();
                sourceFile.addProperty(FILE_PATH, document.name());
                sourceFile.addProperty("content", String.join("", document.textDocument().textLines()));
                sourceFiles.add(sourceFile);
            }
            return sourceFiles;
        }

        private void persistInGeneratedDirectory(String originalFuncName,
                                                 String generatedCode) {
            Path generatedDirPath = Paths.get(this.sourceRoot.toString(), GENERATED_DIRECTORY);
            if (!Files.exists(generatedDirPath)) {
                try {
                    Files.createDirectories(generatedDirPath);
                } catch (IOException e) {
                    // Shouldn't be a showstopper?
                    return;
                }
            }

            try (PrintWriter writer = new PrintWriter(
                    Paths.get(generatedDirPath.toString(), getGeneratedBalFileName(originalFuncName)).toString(),
                    StandardCharsets.UTF_8)) {
                writer.println(Formatter.format(generatedCode));
            } catch (IOException | FormatterException e) {
                // Shouldn't be a showstopper?
                return;
            }
        }
    }

    private static String getGeneratedBalFileName(String originalFuncName) {
        return originalFuncName + GENERATED_FUNC_FILE_NAME_SUFFIX;
    }

    private static String getPrompt(FunctionDefinitionNode functionDefinition, SemanticModel semanticModel) {
        for (AnnotationAttachmentSymbol annotationAttachmentSymbol :
                ((ExternalFunctionSymbol) semanticModel.symbol(functionDefinition).get())
                        .annotAttachmentsOnExternal()) {
            AnnotationSymbol annotationSymbol = annotationAttachmentSymbol.typeDescriptor();
            Optional<ModuleSymbol> module = annotationSymbol.getModule();
            if (module.isEmpty() || !LANG_ANNOTATIONS_MODULE.equals(module.get().getName().get())) {
                continue;
            }

            if (GENERATE_CODE_ANNOT.equals(annotationSymbol.getName().get())) {
                return (String) ((ConstantValue) (
                        (LinkedHashMap) annotationAttachmentSymbol.attachmentValue().get().value())
                        .get(PROMPT)).value();
            }
        }
        throw new RuntimeException("cannot find the annotation");
    }

    private static FunctionCallExpressionNode createGeneratedFunctionCallExpression(
            FunctionDefinitionNode functionDefinition, String generatedFunctionName) {
        SeparatedNodeList<ParameterNode> parameters = functionDefinition.functionSignature().parameters();
        int size = parameters.size();
        String[] arguments = new String[size];

        for (int index = 0; index < size; index++) {
            ParameterNode parameter = parameters.get(index);
            arguments[index] = switch (parameter.kind()) {
                case REQUIRED_PARAM: yield ((RequiredParameterNode) parameter).paramName().get().text();
                case DEFAULTABLE_PARAM: yield ((DefaultableParameterNode) parameter).paramName().get().text();
                case INCLUDED_RECORD_PARAM: yield ((IncludedRecordParameterNode) parameter).paramName().get().text();
                default: yield "..." + ((RestParameterNode) parameter).paramName().get().text();
            };
        }

        return (FunctionCallExpressionNode) NodeParser.parseExpression(
                String.format("%s(%s)", generatedFunctionName, String.join(", ", arguments)));
    }

    private static boolean npGeneratedFile(Document document) {
        return document.name().endsWith(GENERATED_FUNC_FILE_NAME_SUFFIX);
    }
}
