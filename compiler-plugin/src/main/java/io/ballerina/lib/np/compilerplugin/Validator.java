/*
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.lib.np.compilerplugin;

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.Types;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.TypeDefinitionSymbol;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.syntax.tree.AnnotationNode;
import io.ballerina.compiler.syntax.tree.FunctionArgumentNode;
import io.ballerina.compiler.syntax.tree.FunctionCallExpressionNode;
import io.ballerina.compiler.syntax.tree.NaturalExpressionNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.ParenthesizedArgList;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.projects.Document;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleId;
import io.ballerina.projects.Package;
import io.ballerina.projects.ProjectKind;
import io.ballerina.projects.plugins.AnalysisTask;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerina.tools.diagnostics.Location;

import java.util.Optional;

import static io.ballerina.lib.np.compilerplugin.Commons.MODULE_NAME;
import static io.ballerina.lib.np.compilerplugin.Commons.ORG_NAME;
import static io.ballerina.lib.np.compilerplugin.Commons.VERSION;
import static io.ballerina.lib.np.compilerplugin.Commons.isCodeAnnotation;
import static io.ballerina.lib.np.compilerplugin.Commons.isNotNPCallCall;
import static io.ballerina.lib.np.compilerplugin.Commons.isRuntimeNaturalExpression;
import static io.ballerina.lib.np.compilerplugin.DiagnosticLog.DiagnosticCode
        .CODE_GEN_WITH_CODE_ANNOT_NOT_SUPPORTED_IN_SINGLE_BAL_FILE_MODE;
import static io.ballerina.lib.np.compilerplugin.DiagnosticLog.DiagnosticCode.EXPECTED_A_SUBTYPE_OF_NP_MODEL;
import static io.ballerina.lib.np.compilerplugin.DiagnosticLog.DiagnosticCode.NON_JSON_EXPECTED_TYPE_NOT_YET_SUPPORTED;
import static io.ballerina.lib.np.compilerplugin.DiagnosticLog.DiagnosticCode
        .NON_JSON_TYPEDESC_ARGUMENT_NOT_YET_SUPPORTED;
import static io.ballerina.lib.np.compilerplugin.DiagnosticLog.DiagnosticCode.UNEXPECTED_ARGUMENTS;
import static io.ballerina.lib.np.compilerplugin.DiagnosticLog.reportError;

/**
 * Natural programming function signature validator.
 *
 * @since 0.3.0
 */
public class Validator implements AnalysisTask<SyntaxNodeAnalysisContext> {
    private static final String MODEL_PROVIDER_TYPE = "ModelProvider";

    private final CodeModifier.AnalysisData analysisData;
    private Optional<TypeSymbol> jsonOrErrorType = Optional.empty();

    Validator(CodeModifier.AnalysisData analysisData) {
        this.analysisData = analysisData;
    }

    @Override
    public void perform(SyntaxNodeAnalysisContext ctx) {
        SemanticModel semanticModel = ctx.semanticModel();
        Types types = semanticModel.types();
        Optional<Symbol> modelSymbol = types.getTypeByName(ORG_NAME, MODULE_NAME, VERSION, MODEL_PROVIDER_TYPE);

        Package currentPackage = ctx.currentPackage();
        ModuleId moduleId = ctx.moduleId();
        Module module = currentPackage.module(moduleId);
        Document document = module.document(ctx.documentId());

        Node node = ctx.node();
        if (node instanceof NaturalExpressionNode naturalExpressionNode) {
            validateNaturalExpression(semanticModel, types, document, naturalExpressionNode, modelSymbol, ctx);
            return;
        }

        if (node instanceof AnnotationNode annotationNode) {
            validateCompileTimeCodeGenAnnotation(semanticModel, annotationNode, ctx,
                    currentPackage.project().kind() == ProjectKind.SINGLE_FILE_PROJECT);
            return;
        }

        validateCallLlmExpression(semanticModel, types, document, (FunctionCallExpressionNode) node, ctx);
    }

    private void validateNaturalExpression(SemanticModel semanticModel,
                                           Types types, Document document,
                                           NaturalExpressionNode naturalExpressionNode,
                                           Optional<Symbol> modelSymbol,
                                           SyntaxNodeAnalysisContext ctx) {
        validateArguments(ctx, semanticModel, naturalExpressionNode.parenthesizedArgList(), modelSymbol);

        if (isRuntimeNaturalExpression(naturalExpressionNode)) {
            validateExpectedType(naturalExpressionNode.location(),
                    semanticModel.expectedType(document, naturalExpressionNode.lineRange().startLine()).get(),
                    types, ctx);

        }
    }

    private void validateArguments(SyntaxNodeAnalysisContext ctx,
                                   SemanticModel semanticModel,
                                   Optional<ParenthesizedArgList> parenthesizedArgListOptional,
                                   Optional<Symbol> modelSymbol) {
        if (parenthesizedArgListOptional.isEmpty()) {
            return;
        }

        ParenthesizedArgList parenthesizedArgList = parenthesizedArgListOptional.get();
        SeparatedNodeList<FunctionArgumentNode> argList = parenthesizedArgList.arguments();
        int argListSize = argList.size();

        if (argListSize == 0) {
            return;
        }

        if (argListSize > 1) {
            reportError(ctx, this.analysisData, parenthesizedArgList.location(), UNEXPECTED_ARGUMENTS, argListSize);
        }

        if (modelSymbol.isEmpty()) {
            return;
        }

        FunctionArgumentNode arg0 = argList.get(0);
        Optional<TypeSymbol> argType = semanticModel.typeOf(arg0.lineRange());
        if (argType.isEmpty()) {
            return;
        }

        TypeSymbol symbol = argType.get();
        if (!symbol.subtypeOf(((TypeDefinitionSymbol) modelSymbol.get()).typeDescriptor())) {
            reportError(ctx, this.analysisData, arg0.location(), EXPECTED_A_SUBTYPE_OF_NP_MODEL, symbol.signature());
        }
    }

    private void validateCompileTimeCodeGenAnnotation(SemanticModel semanticModel, AnnotationNode annotationNode,
                                                      SyntaxNodeAnalysisContext ctx, boolean isSingleBalFileMode) {
        if (isSingleBalFileMode && isCodeAnnotation(annotationNode, semanticModel)) {
            reportError(ctx, this.analysisData, annotationNode.location(),
                    CODE_GEN_WITH_CODE_ANNOT_NOT_SUPPORTED_IN_SINGLE_BAL_FILE_MODE);
        }
    }

    private void validateCallLlmExpression(SemanticModel semanticModel, Types types, Document document,
                                           FunctionCallExpressionNode functionCallExpressionNode,
                                           SyntaxNodeAnalysisContext ctx) {
        if (isNotNPCallCall(functionCallExpressionNode, semanticModel)) {
            return;
        }

        Optional<TypeSymbol> typeSymbol = semanticModel.typeOf(functionCallExpressionNode);
        if (typeSymbol.isEmpty()) {
            return;
        }

        if (!typeSymbol.get().subtypeOf(getJsonOrErrorType(types))) {
            reportError(ctx, this.analysisData, functionCallExpressionNode.location(),
                    NON_JSON_TYPEDESC_ARGUMENT_NOT_YET_SUPPORTED);
        }
    }

    private void validateExpectedType(Location location, TypeSymbol expectedType, Types types,
                                      SyntaxNodeAnalysisContext ctx) {
        if (!expectedType.subtypeOf(getJsonOrErrorType(types))) {
            reportError(ctx, this.analysisData, location, NON_JSON_EXPECTED_TYPE_NOT_YET_SUPPORTED);
        }
    }

    private TypeSymbol getJsonOrErrorType(Types types) {
        if (this.jsonOrErrorType.isPresent()) {
            return this.jsonOrErrorType.get();
        }

        TypeSymbol jsonOrErrorType = types.builder().UNION_TYPE.withMemberTypes(types.JSON, types.ERROR).build();
        this.jsonOrErrorType = Optional.of(jsonOrErrorType);
        return jsonOrErrorType;
    }
}
