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

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.ArrayTypeSymbol;
import io.ballerina.compiler.api.symbols.RecordTypeSymbol;
import io.ballerina.compiler.api.symbols.TupleTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeReferenceTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import io.ballerina.compiler.syntax.tree.AbstractNodeFactory;
import io.ballerina.compiler.syntax.tree.AnnotationNode;
import io.ballerina.compiler.syntax.tree.BaseNodeModifier;
import io.ballerina.compiler.syntax.tree.ChildNodeList;
import io.ballerina.compiler.syntax.tree.ExpressionFunctionBodyNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.FunctionArgumentNode;
import io.ballerina.compiler.syntax.tree.FunctionCallExpressionNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.FunctionSignatureNode;
import io.ballerina.compiler.syntax.tree.IdentifierToken;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.ImportOrgNameNode;
import io.ballerina.compiler.syntax.tree.ImportPrefixNode;
import io.ballerina.compiler.syntax.tree.LiteralValueToken;
import io.ballerina.compiler.syntax.tree.MappingConstructorExpressionNode;
import io.ballerina.compiler.syntax.tree.MetadataNode;
import io.ballerina.compiler.syntax.tree.MinutiaeList;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.NaturalExpressionNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeFactory;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.NodeParser;
import io.ballerina.compiler.syntax.tree.NonTerminalNode;
import io.ballerina.compiler.syntax.tree.ParameterNode;
import io.ballerina.compiler.syntax.tree.ParenthesizedArgList;
import io.ballerina.compiler.syntax.tree.PositionalArgumentNode;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.ReturnTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.compiler.syntax.tree.TemplateExpressionNode;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.compiler.syntax.tree.TreeModifier;
import io.ballerina.compiler.syntax.tree.TypeDefinitionNode;
import io.ballerina.openapi.service.mapper.type.TypeMapper;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleId;
import io.ballerina.projects.Package;
import io.ballerina.projects.plugins.ModifierTask;
import io.ballerina.projects.plugins.SourceModifierContext;
import io.ballerina.tools.text.TextDocument;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.OpenAPISchema2JsonSchema;
import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.ballerina.compiler.syntax.tree.AbstractNodeFactory.createIdentifierToken;
import static io.ballerina.compiler.syntax.tree.AbstractNodeFactory.createToken;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.CLOSE_BRACE_TOKEN;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.CLOSE_PAREN_TOKEN;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.OPEN_BRACE_TOKEN;
import static io.ballerina.compiler.syntax.tree.SyntaxKind.OPEN_PAREN_TOKEN;
import static io.ballerina.lib.np.compilerplugin.Commons.CALL_LLM;
import static io.ballerina.lib.np.compilerplugin.Commons.MODULE_NAME;
import static io.ballerina.lib.np.compilerplugin.Commons.ORG_NAME;
import static io.ballerina.lib.np.compilerplugin.Commons.getParameterName;
import static io.ballerina.lib.np.compilerplugin.Commons.getParameterType;
import static io.ballerina.lib.np.compilerplugin.Commons.isNotNPCallCall;
import static io.ballerina.lib.np.compilerplugin.Commons.isRuntimeNaturalExpression;
import static io.ballerina.projects.util.ProjectConstants.EMPTY_STRING;

/**
 * Code modification task to replace runtime prompt as code external functions with np:call.
 *
 * @since 0.3.0
 */
public class RuntimePromptAsCodeCodeModificationTask implements ModifierTask<SourceModifierContext> {

    private static final Token OPEN_PAREN = createToken(OPEN_PAREN_TOKEN);
    private static final Token CLOSE_PAREN = createToken(CLOSE_PAREN_TOKEN);
    private static final Token OPEN_BRACE = createToken(OPEN_BRACE_TOKEN);
    private static final Token CLOSE_BRACE = createToken(CLOSE_BRACE_TOKEN);
    private static final Token COLON = createToken(SyntaxKind.COLON_TOKEN);
    private static final Token COMMA = createToken(SyntaxKind.COMMA_TOKEN);
    private static final Token BACKTICK = createToken(SyntaxKind.BACKTICK_TOKEN);
    private static final Token INTERPOLATION_START = createToken(SyntaxKind.INTERPOLATION_START_TOKEN);
    private static final Token MODEL = createIdentifierToken("model");
    private static final String SCHEMA_ANNOTATION_IDENTIFIER = "JsonSchema";
    private static final String STRING = "string";
    private static final String BYTE = "byte";
    private static final String NUMBER = "number";
    private static final String ESCAPED_BACKTICK = "\"`\"";
    private static final String LINE_SEPARATOR = System.lineSeparator();

    private final ModifierData modifierData;
    private final CodeModifier.AnalysisData analysisData;

    RuntimePromptAsCodeCodeModificationTask(CodeModifier.AnalysisData analysisData) {
        this.modifierData = new ModifierData();
        this.analysisData = analysisData;
    }

    @Override
    public void modify(SourceModifierContext modifierContext) {
        Package currentPackage = modifierContext.currentPackage();

        if (// https://github.com/ballerina-platform/ballerina-lang/issues/44020
            // this.analysisData.analysisTaskErrored ||
                modifierContext.compilation().diagnosticResult().errorCount() > 0) {
            return;
        }

        for (ModuleId moduleId : currentPackage.moduleIds()) {
            Module module = currentPackage.module(moduleId);

            for (DocumentId documentId: module.documentIds()) {
                Document document = module.document(documentId);
                processImportDeclarations(document, modifierData);
                processExternalFunctions(document, module, modifierData, modifierContext);
            }

            for (DocumentId documentId: module.documentIds()) {
                Document document = module.document(documentId);
                modifierContext.modifySourceFile(modifyDocument(document, modifierData, modifierContext, moduleId,
                                                                this.analysisData.typeMapper), documentId);
            }

            for (DocumentId documentId: module.testDocumentIds()) {
                Document document = module.document(documentId);
                processImportDeclarations(document, modifierData);
                processExternalFunctions(document, module, modifierData, modifierContext);
            }

            for (DocumentId documentId: module.testDocumentIds()) {
                Document document = module.document(documentId);
                modifierContext.modifyTestSourceFile(modifyDocument(document, modifierData, modifierContext, moduleId,
                                                                    this.analysisData.typeMapper), documentId);
            }
        }
    }

    private void processExternalFunctions(Document document, Module module, ModifierData modifierData,
                                          SourceModifierContext modifierContext) {
        SyntaxTree syntaxTree = document.syntaxTree();
        ModulePartNode rootNode = syntaxTree.rootNode();
        SemanticModel semanticModel = modifierContext.compilation().getSemanticModel(module.moduleId());
        for (ModuleMemberDeclarationNode memberNode : rootNode.members()) {
            if (!isNaturalFunction(memberNode)) {
                continue;
            }

            modifierData.documentsRequiringNPImport.add(document);
            FunctionDefinitionNode functionDefinition = (FunctionDefinitionNode) memberNode;
            extractAndStoreSchemas(semanticModel, functionDefinition, modifierData.typeSchemas,
                                   this.analysisData.typeMapper);
        }
    }

    private static void processImportDeclarations(Document document, ModifierData modifierData) {
        ModulePartNode modulePartNode = document.syntaxTree().rootNode();
        ImportDeclarationModifier importDeclarationModifier = new ImportDeclarationModifier(modifierData);
        modulePartNode.apply(importDeclarationModifier);
    }

    private static TextDocument modifyDocument(Document document, ModifierData modifierData,
                                               SourceModifierContext modifierContext, ModuleId moduleId,
                                               TypeMapper typeMapper) {
        ModulePartNode modulePartNode = document.syntaxTree().rootNode();

        NaturalProgrammingCodeModifier naturalProgrammingCodeModifier =
                new NaturalProgrammingCodeModifier(
                        modifierData, modifierContext, moduleId, document, typeMapper);
        TypeDefinitionModifier typeDefinitionModifier =
                new TypeDefinitionModifier(modifierData.typeSchemas, modifierData, document);

        ModulePartNode modifiedRoot = (ModulePartNode) modulePartNode.apply(naturalProgrammingCodeModifier);
        modifiedRoot = modifiedRoot.modify(modifiedRoot.imports(), modifiedRoot.members(), modifiedRoot.eofToken());

        ModulePartNode finalRoot = (ModulePartNode) modifiedRoot.apply(typeDefinitionModifier);
        finalRoot = finalRoot.modify(
                updateImports(document, finalRoot, modifierData), finalRoot.members(), finalRoot.eofToken());

        return document.syntaxTree().modifyWith(finalRoot).textDocument();
    }

    private static class ImportDeclarationModifier extends TreeModifier {

        private final ModifierData modifierData;

        ImportDeclarationModifier(ModifierData modifierData) {
            this.modifierData = modifierData;
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
            modifierData.npPrefixIfImported = Optional.of(
                    prefix.isEmpty() ? MODULE_NAME : prefix.get().prefix().text());
            return importDeclarationNode;
        }
    }

    private static class NaturalProgrammingCodeModifier extends BaseNodeModifier {

        private final ModifierData modifierData;
        private final SemanticModel semanticModel;
        private final Document document;
        private final TypeMapper typeMapper;

        NaturalProgrammingCodeModifier(ModifierData modifierData, SourceModifierContext modifierContext,
                                       ModuleId moduleId, Document document, TypeMapper typeMapper) {
            this.modifierData = modifierData;
            this.semanticModel = modifierContext.compilation().getSemanticModel(moduleId);
            this.document = document;
            this.typeMapper = typeMapper;
        }

        @Override
        public ExpressionNode transform(NaturalExpressionNode naturalExpressionNode) {
            if (!isRuntimeNaturalExpression(naturalExpressionNode)) {
                return naturalExpressionNode;
            }

            Optional<String> npPrefixIfImported = modifierData.npPrefixIfImported;
            String npPrefix;

            if (npPrefixIfImported.isPresent()) {
                npPrefix = npPrefixIfImported.get();
            } else {
                modifierData.documentsRequiringNPImport.add(document);
                npPrefix = MODULE_NAME;
            }
            return createNPCallFunctionCallExpression(npPrefix, naturalExpressionNode, this.semanticModel);
        }

        @Override
        public FunctionCallExpressionNode transform(FunctionCallExpressionNode functionCallExpressionNode) {
            if (isNotNPCallCall(functionCallExpressionNode, this.semanticModel)) {
                return functionCallExpressionNode;
            }

            Optional<TypeSymbol> typeSymbol =
                              semanticModel.expectedType(document, functionCallExpressionNode.lineRange().startLine());
            if (typeSymbol.isEmpty()) {
                return functionCallExpressionNode;
            }
            populateTypeSchema(typeSymbol.get(), this.typeMapper, this.modifierData.typeSchemas);
            return functionCallExpressionNode;
        }
    }

    private static FunctionCallExpressionNode createNPCallFunctionCallExpression(
            String npPrefix, NaturalExpressionNode naturalExpressionNode, SemanticModel semanticModel) {
        NodeList<Node> prompt = getRawTemplateContent(naturalExpressionNode, semanticModel);
        TemplateExpressionNode promptRawTemplate = NodeFactory.createTemplateExpressionNode(
                SyntaxKind.RAW_TEMPLATE_EXPRESSION, null, BACKTICK, prompt, BACKTICK);

        Optional<ExpressionNode> naturalModelArg = Optional.empty();

        Optional<ParenthesizedArgList> parenthesizedArgListOptional = naturalExpressionNode.parenthesizedArgList();
        if (parenthesizedArgListOptional.isPresent()) {
            ParenthesizedArgList parenthesizedArgList = parenthesizedArgListOptional.get();
            SeparatedNodeList<FunctionArgumentNode> arguments = parenthesizedArgList.arguments();
            if (!arguments.isEmpty()) {
                if (arguments.get(0) instanceof PositionalArgumentNode positionalArgumentNode) {
                    naturalModelArg = Optional.of(positionalArgumentNode.expression());
                }
            }
        }

        SeparatedNodeList<FunctionArgumentNode> arguments =
                naturalModelArg.isPresent() ?
                        NodeFactory.createSeparatedNodeList(
                                promptRawTemplate,
                                COMMA,
                                createContextMappingExpressionNode(naturalModelArg.get())
                        ) :
                        NodeFactory.createSeparatedNodeList(
                                promptRawTemplate
                        );
        return NodeFactory.createFunctionCallExpressionNode(
                createNPCallQualifiedNameReferenceNode(npPrefix),
                OPEN_PAREN,
                arguments,
                CLOSE_PAREN
        );
    }

    private static NodeList<Node> getRawTemplateContent(NaturalExpressionNode naturalExpressionNode,
                                                        SemanticModel semanticModel) {
        NodeList<Node> prompt = naturalExpressionNode.prompt();
        List<Node> modifiedNodes = new ArrayList<>();
        boolean modified = false;
        MinutiaeList emptyMinutiaeList = AbstractNodeFactory.createEmptyMinutiaeList();

        boolean hasInsertions = false;
        for (Node node : prompt) {
            if (!(node instanceof LiteralValueToken literalValueToken)) {
                modifiedNodes.add(node);
                hasInsertions = true;
                continue;
            }

            SyntaxKind kind = literalValueToken.kind();
            String text = literalValueToken.text();

            if (text.isEmpty()) {
                modifiedNodes.add(node);
                continue;
            }

            String updatedText = text.replace("\\}", "}");
            updatedText = updatedText.replace("`", ESCAPED_BACKTICK);
            if (!text.equals(updatedText)) {
                modified = true;
            }

            String[] split = updatedText.split(ESCAPED_BACKTICK);

            int length = split.length;
            for (int i = 0; i < length - 1; i++) {
                String part = split[i];
                modifiedNodes.add(
                        NodeFactory.createLiteralValueToken(kind, part, emptyMinutiaeList, emptyMinutiaeList));
                modifiedNodes.add(
                        NodeFactory.createInterpolationNode(
                                INTERPOLATION_START, NodeParser.parseExpression(ESCAPED_BACKTICK), CLOSE_BRACE)
                );
            }

            modifiedNodes.add(
                    NodeFactory.createLiteralValueToken(kind, split[length - 1], emptyMinutiaeList, emptyMinutiaeList));

            if (updatedText.endsWith(ESCAPED_BACKTICK)) {
                modifiedNodes.add(
                        NodeFactory.createInterpolationNode(
                                INTERPOLATION_START, NodeParser.parseExpression(ESCAPED_BACKTICK), CLOSE_BRACE)
                );
            }
        }

        if (!hasInsertions) {
            Optional<FunctionSignatureNode> functionSignatureIfInNaturalFunction =
                    getFunctionSignatureIfInNaturalFunction(naturalExpressionNode);
            if (functionSignatureIfInNaturalFunction.isPresent()) {
                Optional<List<Node>> parameterInjectionTemplateNodes =
                        getParameterInjectionTemplateNodes(semanticModel, functionSignatureIfInNaturalFunction.get());
                if (parameterInjectionTemplateNodes.isPresent()) {
                    modified = true;
                    modifiedNodes.addAll(0, parameterInjectionTemplateNodes.get());
                }
            }
        }

        if (modified) {
            return NodeFactory.createNodeList(modifiedNodes);
        }
        return prompt;
    }

    private static Optional<List<Node>> getParameterInjectionTemplateNodes(
            SemanticModel semanticModel, FunctionSignatureNode functionSignatureNode) {
        SeparatedNodeList<ParameterNode> parameters = functionSignatureNode.parameters();

        List<String> parametersToInclude = new ArrayList<>(parameters.size());
        for (ParameterNode parameter : parameters) {
            SyntaxKind kind = parameter.kind();
            Node parameterType = getParameterType(parameter, kind);
            Optional<TypeSymbol> symbol = semanticModel.type(parameterType.lineRange());
            if (symbol.isEmpty()) {
                continue;
            }

            if (symbol.get().subtypeOf(semanticModel.types().ANYDATA)) {
                parametersToInclude.add(getParameterName(parameter, kind));
            }
        }

        if (parametersToInclude.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder sb = new StringBuilder("`You have been given the following input:");
        sb.append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        for (String str : parametersToInclude) {
            sb.append(str)
                    .append(": ")
                    .append(LINE_SEPARATOR)
                    .append("${\"```\"}")
                    .append(LINE_SEPARATOR)
                    .append("${")
                    .append(str)
                    .append("}")
                    .append(LINE_SEPARATOR)
                    .append("${\"```\"}")
                    .append(LINE_SEPARATOR)
                    .append(LINE_SEPARATOR);
        }
        sb.append("`");

        ChildNodeList children = NodeParser.parseExpression(sb.toString()).children();
        int size = children.size();
        List<Node> parameterInjectionTemplateNodes = new ArrayList<>(size - 2);
        for (int i = 1; i < size - 1; i++) {
            parameterInjectionTemplateNodes.add(children.get(i));
        }
        return Optional.of(parameterInjectionTemplateNodes);
    }

    private static Optional<FunctionSignatureNode> getFunctionSignatureIfInNaturalFunction(
            NaturalExpressionNode naturalExpressionNode) {
        NonTerminalNode parent = naturalExpressionNode.parent();
        while (parent != null) {
            if (parent instanceof ExpressionNode) {
                parent = parent.parent();
                continue;
            }

            if (parent instanceof ExpressionFunctionBodyNode) {
                return Optional.of(((FunctionDefinitionNode) parent.parent()).functionSignature());
            }

            return Optional.empty();
        }
        return Optional.empty();
    }

    private static MappingConstructorExpressionNode createContextMappingExpressionNode(ExpressionNode model) {
        return NodeFactory.createMappingConstructorExpressionNode(
                OPEN_BRACE,
                NodeFactory.createSeparatedNodeList(
                        NodeFactory.createSpecificFieldNode(null, MODEL, COLON, model)
                ),
                CLOSE_BRACE);
    }

    private static QualifiedNameReferenceNode createNPCallQualifiedNameReferenceNode(String npPrefix) {
        return NodeFactory.createQualifiedNameReferenceNode(
                NodeFactory.createIdentifierToken(npPrefix),
                COLON,
                NodeFactory.createIdentifierToken(CALL_LLM)
        );
    }

    private static class TypeDefinitionModifier extends TreeModifier {

        private final Map<String, String> typeSchemas;
        private final ModifierData modifierData;
        private final Document document;

        TypeDefinitionModifier(Map<String, String> typeSchemas, ModifierData modifierData, Document document) {
            this.typeSchemas = typeSchemas;
            this.modifierData = modifierData;
            this.document = document;
        }

        @Override
        public TypeDefinitionNode transform(TypeDefinitionNode typeDefinitionNode) {
            if (modifierData.npPrefixIfImported.isEmpty() &&
                    !modifierData.documentsRequiringNPImport.contains(this.document)) {
                return typeDefinitionNode;
            }
            String typeName = typeDefinitionNode.typeName().text();

            if (!this.typeSchemas.containsKey(typeName)) {
                return typeDefinitionNode;
            }

            MetadataNode updatedMetadataNode =
                                updateMetadata(typeDefinitionNode, typeSchemas.get(typeName),
                                               modifierData.npPrefixIfImported.orElse(MODULE_NAME));
            return typeDefinitionNode.modify().withMetadata(updatedMetadataNode).apply();
        }

        private MetadataNode updateMetadata(TypeDefinitionNode typeDefinitionNode, String schema, String npPrefix) {
            MetadataNode metadataNode = getMetadataNode(typeDefinitionNode);
            NodeList<AnnotationNode> updatedAnnotations =
                                            updateAnnotations(metadataNode.annotations(), schema, npPrefix);
            return metadataNode.modify().withAnnotations(updatedAnnotations).apply();
        }
    }

    public static MetadataNode getMetadataNode(TypeDefinitionNode typeDefinitionNode) {
        return typeDefinitionNode.metadata().orElseGet(() -> {
            NodeList<AnnotationNode> annotations = NodeFactory.createNodeList();
            return NodeFactory.createMetadataNode(null, annotations);
        });
    }

    private static NodeList<AnnotationNode> updateAnnotations(NodeList<AnnotationNode> currentAnnotations,
                                                              String jsonSchema, String npPrefix) {
        NodeList<AnnotationNode> updatedAnnotations = NodeFactory.createNodeList();

        if (currentAnnotations.isEmpty()) {
            updatedAnnotations = updatedAnnotations.add(getSchemaAnnotation(jsonSchema, npPrefix));
        }

        return updatedAnnotations;
    }

    public static AnnotationNode getSchemaAnnotation(String jsonSchema, String npPrefix) {
        String configIdentifierString = npPrefix + COLON.text() + SCHEMA_ANNOTATION_IDENTIFIER;
        IdentifierToken identifierToken = NodeFactory.createIdentifierToken(configIdentifierString);

        return NodeFactory.createAnnotationNode(
                NodeFactory.createToken(SyntaxKind.AT_TOKEN),
                NodeFactory.createSimpleNameReferenceNode(identifierToken),
                getAnnotationExpression(jsonSchema)
        );
    }

    public static MappingConstructorExpressionNode getAnnotationExpression(String jsonSchema) {
        return (MappingConstructorExpressionNode) NodeParser.parseExpression(jsonSchema);
    }

    private static boolean containsBallerinaNPImport(NodeList<ImportDeclarationNode> imports) {
        for (ImportDeclarationNode importDeclarationNode : imports) {
            Optional<ImportOrgNameNode> importOrgNameNode = importDeclarationNode.orgName();
            if (importOrgNameNode.isPresent() && importOrgNameNode.get().orgName().text().equals(ORG_NAME)
                    && importDeclarationNode.moduleName().get(0).text().equals(MODULE_NAME)) {
                return true;
            }
        }
        return false;
    }

    private static NodeList<ImportDeclarationNode> updateImports(Document document, ModulePartNode modulePartNode,
                                                                 ModifierData modifierData) {
        NodeList<ImportDeclarationNode> imports = modulePartNode.imports();

        if (containsBallerinaNPImport(imports)) {
            return imports;
        }

        if (modifierData.documentsRequiringNPImport.contains(document)) {
            return imports.add(createImportDeclarationForNPModule());
        }
        return imports;
    }

    private static ImportDeclarationNode createImportDeclarationForNPModule() {
        return NodeParser.parseImportDeclaration(String.format("import %s/%s as np;", ORG_NAME, MODULE_NAME));
    }

    private boolean isNaturalFunction(ModuleMemberDeclarationNode memberNode) {
        if (!(memberNode instanceof FunctionDefinitionNode functionDefinition)) {
            return false;
        }
        return functionDefinition.functionBody() instanceof ExpressionFunctionBodyNode expressionFunctionBodyNode
                && isRuntimeNaturalExpression(expressionFunctionBodyNode.expression());
    }

    private void extractAndStoreSchemas(SemanticModel semanticModel, FunctionDefinitionNode functionDefinition,
                                        Map<String, String> typeSchemas, TypeMapper typeMapper) {
        Optional<ReturnTypeDescriptorNode> returnTypeNodeOpt = functionDefinition.functionSignature().returnTypeDesc();
        if (returnTypeNodeOpt.isEmpty()) {
            return;
        }

        ReturnTypeDescriptorNode returnTypeNode = returnTypeNodeOpt.get();
        Optional<TypeSymbol> typeSymbolOpt = semanticModel.type(returnTypeNode.type().lineRange());
        if (typeSymbolOpt.isEmpty()) {
            return;
        }

        TypeSymbol typeSymbol = typeSymbolOpt.get();
        if (typeSymbol instanceof UnionTypeSymbol unionTypeSymbol) {
            for (TypeSymbol memberType : unionTypeSymbol.memberTypeDescriptors()) {
                populateTypeSchema(memberType, typeMapper, typeSchemas);
            }
        }
    }

    private static void populateTypeSchema(TypeSymbol memberType, TypeMapper typeMapper,
                                           Map<String, String> typeSchemas) {
        switch (memberType) {
            case TypeReferenceTypeSymbol typeReference ->
                    typeSchemas.put(typeReference.definition().getName().get(),
                            getJsonSchema(typeMapper.getSchema(typeReference)));
            case ArrayTypeSymbol arrayType ->
                            populateTypeSchema(arrayType.memberTypeDescriptor(), typeMapper, typeSchemas);
            case TupleTypeSymbol tupleType ->
                    tupleType.members().forEach(member ->
                            populateTypeSchema(member.typeDescriptor(), typeMapper, typeSchemas));
            case RecordTypeSymbol recordType ->
                    recordType.fieldDescriptors().values().forEach(field ->
                            populateTypeSchema(field.typeDescriptor(), typeMapper, typeSchemas));
            case UnionTypeSymbol unionTypeSymbol -> unionTypeSymbol.memberTypeDescriptors().forEach(member ->
                            populateTypeSchema(member, typeMapper, typeSchemas));
            default -> { }
        }
    }


    @SuppressWarnings("rawtypes")
    private static String getJsonSchema(Schema schema) {
        modifySchema(schema);
        OpenAPISchema2JsonSchema openAPISchema2JsonSchema = new OpenAPISchema2JsonSchema();
        openAPISchema2JsonSchema.process(schema);
        String newLineRegex = "\\R";
        String jsonCompressionRegex = "\\s*([{}\\[\\]:,])\\s*";
        return Json.pretty(schema.getJsonSchema())
                .replaceAll(newLineRegex, EMPTY_STRING)
                .replaceAll(jsonCompressionRegex, "$1");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void modifySchema(Schema schema) {
        if (schema == null) {
            return;
        }
        modifySchema(schema.getItems());
        modifySchema(schema.getNot());

        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            properties.values().forEach(RuntimePromptAsCodeCodeModificationTask::modifySchema);
        }

        List<Schema> allOf = schema.getAllOf();
        if (allOf != null) {
            schema.setType(null);
            allOf.forEach(RuntimePromptAsCodeCodeModificationTask::modifySchema);
        }

        List<Schema> anyOf = schema.getAnyOf();
        if (anyOf != null) {
            schema.setType(null);
            anyOf.forEach(RuntimePromptAsCodeCodeModificationTask::modifySchema);
        }

        List<Schema> oneOf = schema.getOneOf();
        if (oneOf != null) {
            schema.setType(null);
            oneOf.forEach(RuntimePromptAsCodeCodeModificationTask::modifySchema);
        }

        // Override default ballerina byte to json schema mapping
        if (BYTE.equals(schema.getFormat()) && STRING.equals(schema.getType())) {
            schema.setFormat(null);
            schema.setType(NUMBER);
        }
        removeUnwantedFields(schema);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void removeUnwantedFields(Schema schema) {
        schema.setSpecVersion(null);
        schema.setSpecVersion(null);
        schema.setContains(null);
        schema.set$id(null);
        schema.set$schema(null);
        schema.set$anchor(null);
        schema.setExclusiveMaximumValue(null);
        schema.setExclusiveMinimumValue(null);
        schema.setDiscriminator(null);
        schema.setTitle(null);
        schema.setMaximum(null);
        schema.setExclusiveMaximum(null);
        schema.setMinimum(null);
        schema.setExclusiveMinimum(null);
        schema.setMaxLength(null);
        schema.setMinLength(null);
        schema.setMaxItems(null);
        schema.setMinItems(null);
        schema.setMaxProperties(null);
        schema.setMinProperties(null);
        schema.setAdditionalProperties(null);
        schema.setAdditionalProperties(null);
        schema.set$ref(null);
        schema.set$ref(null);
        schema.setReadOnly(null);
        schema.setWriteOnly(null);
        schema.setExample(null);
        schema.setExample(null);
        schema.setExternalDocs(null);
        schema.setDeprecated(null);
        schema.setPrefixItems(null);
        schema.setContentEncoding(null);
        schema.setContentMediaType(null);
        schema.setContentSchema(null);
        schema.setPropertyNames(null);
        schema.setUnevaluatedProperties(null);
        schema.setMaxContains(null);
        schema.setMinContains(null);
        schema.setAdditionalItems(null);
        schema.setUnevaluatedItems(null);
        schema.setIf(null);
        schema.setElse(null);
        schema.setThen(null);
        schema.setDependentSchemas(null);
        schema.set$comment(null);
        schema.setExamples(null);
        schema.setExtensions(null);
        schema.setConst(null);
    }

    static final class ModifierData {
        Optional<String> npPrefixIfImported = Optional.empty();
        Set<Document> documentsRequiringNPImport = new HashSet<>(0);
        Map<String, String> typeSchemas = new HashMap<>();
    }
}
