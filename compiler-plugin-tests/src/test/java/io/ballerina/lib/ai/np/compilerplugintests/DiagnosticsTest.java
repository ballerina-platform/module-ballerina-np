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

package io.ballerina.lib.ai.np.compilerplugintests;

import io.ballerina.projects.BuildOptions;
import io.ballerina.projects.DiagnosticResult;
import io.ballerina.projects.Package;
import io.ballerina.projects.ProjectEnvironmentBuilder;
import io.ballerina.projects.directory.BuildProject;
import io.ballerina.projects.directory.SingleFileProject;
import io.ballerina.projects.environment.Environment;
import io.ballerina.projects.environment.EnvironmentBuilder;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.text.LinePosition;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * This class includes tests for the Ballerina natural programming compiler plugin.
 *
 * @since 0.1.0
 */
public class DiagnosticsTest {

    private static final Path RESOURCE_DIRECTORY = Paths.get("src", "test", "resources").toAbsolutePath();
    private static final Path DISTRIBUTION_PATH = Paths.get("../", "target", "ballerina-runtime").toAbsolutePath();

    @Test
    public void testNaturalExpressionsNegative() {
        int index = 0;
        Package naturalExprNegativePackage = loadPackage("natural-expressions-negative");
        DiagnosticResult diagnosticResult = naturalExprNegativePackage.runCodeGenAndModifyPlugins();
        List<Diagnostic> errorDiagnosticsList = diagnosticResult.diagnostics().stream().toList();
        assertDiagnostic(errorDiagnosticsList, index++,
                "subtypes of 'anydata' that are not subtypes of 'json' are not yet supported as the" +
                        " expected type for natural expressions",
                19, 59);
        Assert.assertEquals(index, errorDiagnosticsList.size());
    }

    @Test
    public void testCodeGenWithCodeAnnotationDisallowingFile() {
        Package codeAnnotationNegativeFile =
                loadSingleFileProject();
        List<Diagnostic> fileDiagnosticsList =
                codeAnnotationNegativeFile.runCodeGenAndModifyPlugins().diagnostics().stream().toList();
        int index = 0;
        assertDiagnostic(fileDiagnosticsList, index++,
                "code generation with the 'code' annotation is not supported in single bal file mode",
                22, 52);
        Assert.assertEquals(index, fileDiagnosticsList.size());
    }

    private static void assertDiagnostic(List<Diagnostic> errorDiagnosticsList, int index, String expectedErrorMessage,
                                         int expectedStartLine, int expectedStartOffset) {
        Diagnostic diagnostic = errorDiagnosticsList.get(index);
        Assert.assertEquals(diagnostic.message(), expectedErrorMessage);
        LinePosition startLine = diagnostic.location().lineRange().startLine();
        Assert.assertEquals(startLine.line(), expectedStartLine - 1);
        Assert.assertEquals(startLine.offset(), expectedStartOffset - 1);
    }

    private static Package loadPackage(String path) {
        return loadPackage(RESOURCE_DIRECTORY.resolve(path));
    }

    private static Package loadPackage(Path projectDirPath) {
        Environment environment = EnvironmentBuilder.getBuilder().setBallerinaHome(DISTRIBUTION_PATH).build();
        ProjectEnvironmentBuilder projectEnvironmentBuilder = ProjectEnvironmentBuilder.getBuilder(environment);
        BuildOptions buildOptions = BuildOptions.builder().setExperimental(true).build();
        BuildProject project = BuildProject.load(projectEnvironmentBuilder, projectDirPath, buildOptions);
        return project.currentPackage();
    }

    private static Package loadSingleFileProject() {
        Path projectDirPath = RESOURCE_DIRECTORY.resolve("code-annotation-negative")
                .resolve("code_annotation_negative_file.bal");
        Environment environment = EnvironmentBuilder.getBuilder().setBallerinaHome(DISTRIBUTION_PATH).build();
        ProjectEnvironmentBuilder projectEnvironmentBuilder = ProjectEnvironmentBuilder.getBuilder(environment);
        BuildOptions buildOptions = BuildOptions.builder().setExperimental(true).build();
        SingleFileProject project = SingleFileProject.load(projectEnvironmentBuilder, projectDirPath, buildOptions);
        return project.currentPackage();
    }
}
