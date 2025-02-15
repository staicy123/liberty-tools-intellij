/*******************************************************************************
 * Copyright (c) 2021, 2024 IBM Corporation and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package io.openliberty.tools.intellij.lsp4jakarta.it.servlet;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import io.openliberty.tools.intellij.lsp4jakarta.it.core.BaseJakartaTest;
import io.openliberty.tools.intellij.lsp4jakarta.it.core.JakartaForJavaAssert;
import io.openliberty.tools.intellij.lsp4mp4ij.psi.core.utils.IPsiUtils;
import io.openliberty.tools.intellij.lsp4mp4ij.psi.internal.core.ls.PsiUtilsLSImpl;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4jakarta.commons.JakartaJavaDiagnosticsParams;
import org.eclipse.lsp4jakarta.commons.JakartaJavaCodeActionParams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.Arrays;

@RunWith(JUnit4.class)
public class JakartaServletTest extends BaseJakartaTest {

    @Test
    public void ExtendWebServlet() throws Exception {
        Module module = createMavenModule(new File("src/test/resources/projects/maven/jakarta-sample"));
        IPsiUtils utils = PsiUtilsLSImpl.getInstance(getProject());

        VirtualFile javaFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(ModuleUtilCore.getModuleDirPath(module)
                + "/src/main/java/io/openliberty/sample/jakarta/servlet/DontExtendHttpServlet.java");
        String uri = VfsUtilCore.virtualToIoFile(javaFile).toURI().toString();

        JakartaJavaDiagnosticsParams diagnosticsParams = new JakartaJavaDiagnosticsParams();
        diagnosticsParams.setUris(Arrays.asList(uri));

        // expected
        Diagnostic d = JakartaForJavaAssert.d(5, 13, 34, "Annotated classes with @WebServlet must extend the HttpServlet class.",
                DiagnosticSeverity.Error, "jakarta-servlet", "ExtendHttpServlet");

        JakartaForJavaAssert.assertJavaDiagnostics(diagnosticsParams, utils, d);

        // test associated quick-fix code action
        JakartaJavaCodeActionParams codeActionParams = JakartaForJavaAssert.createCodeActionParams(uri, d);
        String newText = "package io.openliberty.sample.jakarta.servlet;\n\n" +
                "import jakarta.servlet.annotation.WebServlet;\nimport jakarta.servlet.http.HttpServlet;\n\n" +
                "@WebServlet(name = \"demoServlet\", urlPatterns = {\"/demo\"})\n" +
                "public class DontExtendHttpServlet extends HttpServlet {\n\n}";

        TextEdit te = JakartaForJavaAssert.te(0, 0, 7, 1, newText);
        CodeAction ca = JakartaForJavaAssert.ca(uri, "Let 'DontExtendHttpServlet' extend 'HttpServlet'", d, te);
        JakartaForJavaAssert.assertJavaCodeAction(codeActionParams, utils, ca);
    }

    @Test
    public void CompleteWebServletAnnotation() throws Exception {
        Module module = createMavenModule(new File("src/test/resources/projects/maven/jakarta-sample"));
        IPsiUtils utils = PsiUtilsLSImpl.getInstance(getProject());

        VirtualFile javaFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(ModuleUtilCore.getModuleDirPath(module)
                + "/src/main/java/io/openliberty/sample/jakarta/servlet/InvalidWebServlet.java");
        String uri = VfsUtilCore.virtualToIoFile(javaFile).toURI().toString();

        JakartaJavaDiagnosticsParams diagnosticsParams = new JakartaJavaDiagnosticsParams();
        diagnosticsParams.setUris(Arrays.asList(uri));

        Diagnostic d = JakartaForJavaAssert.d(9, 0, 13,
                "The @WebServlet annotation must define the attribute 'urlPatterns' or 'value'.",
                DiagnosticSeverity.Error, "jakarta-servlet", "CompleteHttpServletAttributes");

        JakartaForJavaAssert.assertJavaDiagnostics(diagnosticsParams, utils, d);

        JakartaJavaCodeActionParams codeActionParams = JakartaForJavaAssert.createCodeActionParams(uri, d);
        String newText = "package io.openliberty.sample.jakarta.servlet;\n\nimport jakarta.servlet.ServletException;\n" +
                "import jakarta.servlet.annotation.WebServlet;\nimport jakarta.servlet.http.HttpServlet;\n" +
                "import jakarta.servlet.http.HttpServletRequest;\nimport jakarta.servlet.http.HttpServletResponse;\n" +
                "import java.io.IOException;\n\n@WebServlet(urlPatterns=\"\")\npublic class InvalidWebServlet extends HttpServlet {\n\t" +
                "@Override\n\tprotected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, " +
                "IOException {\n\t\tres.setContentType(\"text/html;charset=UTF-8\");\n\t\tres.getWriter().println(\"Hello Jakarta EE 9 + " +
                "Open Liberty!\");\n\t}\n}";
        TextEdit te1 = JakartaForJavaAssert.te(0, 0, 16, 1, newText);
        CodeAction ca1 = JakartaForJavaAssert.ca(uri, "Add the `urlPatterns` attribute to @WebServlet", d, te1);

        String newText1 = "package io.openliberty.sample.jakarta.servlet;\n\nimport jakarta.servlet.ServletException;\n" +
                "import jakarta.servlet.annotation.WebServlet;\nimport jakarta.servlet.http.HttpServlet;\n" +
                "import jakarta.servlet.http.HttpServletRequest;\nimport jakarta.servlet.http.HttpServletResponse;\n" +
                "import java.io.IOException;\n\n@WebServlet(\"\")\npublic class InvalidWebServlet extends HttpServlet {\n\t" +
                "@Override\n\tprotected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, " +
                "IOException {\n\t\tres.setContentType(\"text/html;charset=UTF-8\");\n\t\tres.getWriter().println(\"Hello Jakarta EE 9 + " +
                "Open Liberty!\");\n\t}\n}";
        TextEdit te2 = JakartaForJavaAssert.te(0, 0, 16, 1, newText1);
        CodeAction ca2 = JakartaForJavaAssert.ca(uri, "Add the `value` attribute to @WebServlet", d, te2);
        JakartaForJavaAssert.assertJavaCodeAction(codeActionParams, utils, ca1, ca2);
    }

    @Test
    public void RemoveDuplicateAttribute() throws Exception {
        Module module = createMavenModule(new File("src/test/resources/projects/maven/jakarta-sample"));
        IPsiUtils utils = PsiUtilsLSImpl.getInstance(getProject());

        VirtualFile javaFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(ModuleUtilCore.getModuleDirPath(module)
                + "/src/main/java/io/openliberty/sample/jakarta/servlet/DuplicateAttributeWebServlet.java");
        String uri = VfsUtilCore.virtualToIoFile(javaFile).toURI().toString();

        JakartaJavaDiagnosticsParams diagnosticsParams = new JakartaJavaDiagnosticsParams();
        diagnosticsParams.setUris(Arrays.asList(uri));

        Diagnostic d = JakartaForJavaAssert.d(5, 0, 41,
                "The @WebServlet annotation cannot have both 'value' and 'urlPatterns' attributes specified at once.",
                DiagnosticSeverity.Error, "jakarta-servlet", "InvalidHttpServletAttribute");

        JakartaForJavaAssert.assertJavaDiagnostics(diagnosticsParams, utils, d);
        String newText = "package io.openliberty.sample.jakarta.servlet;\n\n" +
                "import jakarta.servlet.annotation.WebServlet;\nimport jakarta.servlet.http.HttpServlet;\n\n@WebServlet( value = \"\")\n" +
                "public class DuplicateAttributeWebServlet extends HttpServlet {\n\n}\n\n";
        String newText1 = "package io.openliberty.sample.jakarta.servlet;\n\nimport jakarta.servlet.annotation.WebServlet;\n" +
                "import jakarta.servlet.http.HttpServlet;\n\n@WebServlet(urlPatterns = \"\" )\n" +
                "public class DuplicateAttributeWebServlet extends HttpServlet {\n\n}\n\n";

        JakartaJavaCodeActionParams codeActionParams = JakartaForJavaAssert.createCodeActionParams(uri, d);
        TextEdit te1 = JakartaForJavaAssert.te(0, 0, 10, 0, newText);
        CodeAction ca1 = JakartaForJavaAssert.ca(uri, "Remove the `urlPatterns` attribute from @WebServlet", d, te1);

        TextEdit te2 = JakartaForJavaAssert.te(0, 0, 10, 0, newText1);
        CodeAction ca2 = JakartaForJavaAssert.ca(uri, "Remove the `value` attribute from @WebServlet", d, te2);
        JakartaForJavaAssert.assertJavaCodeAction(codeActionParams, utils, ca1, ca2);
    }

    @Test
    public void CompleteWebFilterAnnotation() throws Exception {
        Module module = createMavenModule(new File("src/test/resources/projects/maven/jakarta-sample"));
        IPsiUtils utils = PsiUtilsLSImpl.getInstance(getProject());

        VirtualFile javaFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(ModuleUtilCore.getModuleDirPath(module)
                + "/src/main/java/io/openliberty/sample/jakarta/servlet/InvalidWebFilter.java");
        String uri = VfsUtilCore.virtualToIoFile(javaFile).toURI().toString();

        JakartaJavaDiagnosticsParams diagnosticsParams = new JakartaJavaDiagnosticsParams();
        diagnosticsParams.setUris(Arrays.asList(uri));

        Diagnostic d = JakartaForJavaAssert.d(5, 0, 12,
                "The annotation @WebFilter must define the attribute 'urlPatterns', 'servletNames' or 'value'.",
                DiagnosticSeverity.Error, "jakarta-servlet", "CompleteWebFilterAttributes");

        JakartaForJavaAssert.assertJavaDiagnostics(diagnosticsParams, utils, d);
        String newText = "package io.openliberty.sample.jakarta.servlet;\n\nimport jakarta.servlet.Filter;\n" +
                "import jakarta.servlet.annotation.WebFilter;\n\n@WebFilter(servletNames=\"\")\npublic abstract class InvalidWebFilter " +
                "implements Filter {\n\n}\n\n\n";
        String newText1 = "package io.openliberty.sample.jakarta.servlet;\n\nimport jakarta.servlet.Filter;\n" +
                "import jakarta.servlet.annotation.WebFilter;\n\n@WebFilter(urlPatterns=\"\")\npublic abstract class InvalidWebFilter " +
                "implements Filter {\n\n}\n\n\n";
        String newText2 = "package io.openliberty.sample.jakarta.servlet;\n\nimport jakarta.servlet.Filter;\n" +
                "import jakarta.servlet.annotation.WebFilter;\n\n@WebFilter(\"\")\npublic abstract class InvalidWebFilter " +
                "implements Filter {\n\n}\n\n\n";

        JakartaJavaCodeActionParams codeActionParams = JakartaForJavaAssert.createCodeActionParams(uri, d);
        TextEdit te1 = JakartaForJavaAssert.te(0, 0, 11, 0, newText);
        CodeAction ca1 = JakartaForJavaAssert.ca(uri, "Add the `servletNames` attribute to @WebFilter", d, te1);

        TextEdit te2 = JakartaForJavaAssert.te(0, 0, 11, 0, newText1);
        CodeAction ca2 = JakartaForJavaAssert.ca(uri, "Add the `urlPatterns` attribute to @WebFilter", d, te2);

        TextEdit te3 = JakartaForJavaAssert.te(0, 0, 11, 0, newText2);
        CodeAction ca3 = JakartaForJavaAssert.ca(uri, "Add the `value` attribute to @WebFilter", d, te3);
        JakartaForJavaAssert.assertJavaCodeAction(codeActionParams, utils, ca1, ca2, ca3);

    }

    @Test
    public void RemoveDuplicateWebFilterAttributes() throws Exception {
        Module module = createMavenModule(new File("src/test/resources/projects/maven/jakarta-sample"));
        IPsiUtils utils = PsiUtilsLSImpl.getInstance(getProject());

        VirtualFile javaFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(ModuleUtilCore.getModuleDirPath(module)
                + "/src/main/java/io/openliberty/sample/jakarta/servlet/DuplicateAttributeWebFilter.java");
        String uri = VfsUtilCore.virtualToIoFile(javaFile).toURI().toString();

        JakartaJavaDiagnosticsParams diagnosticsParams = new JakartaJavaDiagnosticsParams();
        diagnosticsParams.setUris(Arrays.asList(uri));

        Diagnostic d = JakartaForJavaAssert.d(5, 0, 40,
                "The annotation @WebFilter can not have both 'value' and 'urlPatterns' attributes specified at once.",
                DiagnosticSeverity.Error, "jakarta-servlet", "InvalidWebFilterAttribute");

        JakartaForJavaAssert.assertJavaDiagnostics(diagnosticsParams, utils, d);
        String newText1 = "package io.openliberty.sample.jakarta.servlet;\n\nimport jakarta.servlet.annotation.WebFilter;\n" +
                "import jakarta.servlet.Filter;\n\n@WebFilter( value = \"\")\n" +
                "public abstract class DuplicateAttributeWebFilter implements Filter {\n\n}\n\n";
        String newText2 = "package io.openliberty.sample.jakarta.servlet;\n\nimport jakarta.servlet.annotation.WebFilter;\n" +
                "import jakarta.servlet.Filter;\n\n@WebFilter(urlPatterns = \"\" )\n" +
                "public abstract class DuplicateAttributeWebFilter implements Filter {\n\n}\n\n";

        JakartaJavaCodeActionParams codeActionParams = JakartaForJavaAssert.createCodeActionParams(uri, d);

        TextEdit te1 = JakartaForJavaAssert.te(0, 0, 10, 0, newText1);
        CodeAction ca1 = JakartaForJavaAssert.ca(uri, "Remove the `urlPatterns` attribute from @WebFilter", d, te1);

        TextEdit te2 = JakartaForJavaAssert.te(0, 0, 10, 0, newText2);
        CodeAction ca2 = JakartaForJavaAssert.ca(uri, "Remove the `value` attribute from @WebFilter", d, te2);
        JakartaForJavaAssert.assertJavaCodeAction(codeActionParams, utils, ca1, ca2);
    }

    @Test
    public void implementFilter() throws Exception {
        Module module = createMavenModule(new File("src/test/resources/projects/maven/jakarta-sample"));
        IPsiUtils utils = PsiUtilsLSImpl.getInstance(getProject());

        VirtualFile javaFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(ModuleUtilCore.getModuleDirPath(module)
                + "/src/main/java/io/openliberty/sample/jakarta/servlet/DontImplementFilter.java");
        String uri = VfsUtilCore.virtualToIoFile(javaFile).toURI().toString();

        JakartaJavaDiagnosticsParams diagnosticsParams = new JakartaJavaDiagnosticsParams();
        diagnosticsParams.setUris(Arrays.asList(uri));

        Diagnostic d = JakartaForJavaAssert.d(5, 13, 32, "Annotated classes with @WebFilter must implement the Filter interface.",
                DiagnosticSeverity.Error, "jakarta-servlet", "ImplementFilter");

        JakartaForJavaAssert.assertJavaDiagnostics(diagnosticsParams, utils, d);

        // test associated quick-fix code action
        JakartaJavaCodeActionParams codeActionParams = JakartaForJavaAssert.createCodeActionParams(uri, d);
        String newText = "package io.openliberty.sample.jakarta.servlet;" +
                "\n\nimport jakarta.servlet.Filter;\nimport jakarta.servlet.annotation.WebFilter;" +
                "\n\n@WebFilter(urlPatterns = {\"/filter\"})\npublic class DontImplementFilter implements " +
                "Filter {\n\n}";
        TextEdit te = JakartaForJavaAssert.te(0, 0, 7, 1, newText);

        CodeAction ca = JakartaForJavaAssert.ca(uri, "Let 'DontImplementFilter' implement 'Filter'", d, te);
            JakartaForJavaAssert.assertJavaCodeAction(codeActionParams, utils, ca);
    }

    @Test
    public void implementListener() throws Exception {
        Module module = createMavenModule(new File("src/test/resources/projects/maven/jakarta-sample"));
        IPsiUtils utils = PsiUtilsLSImpl.getInstance(getProject());

        VirtualFile javaFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(ModuleUtilCore.getModuleDirPath(module)
                + "/src/main/java/io/openliberty/sample/jakarta/servlet/DontImplementListener.java");
        String uri = VfsUtilCore.virtualToIoFile(javaFile).toURI().toString();

        JakartaJavaDiagnosticsParams diagnosticsParams = new JakartaJavaDiagnosticsParams();
        diagnosticsParams.setUris(Arrays.asList(uri));

        Diagnostic d = JakartaForJavaAssert.d(5, 13, 34, "Annotated classes with @WebListener must implement one or more of the following interfaces: ServletContextListener, ServletContextAttributeListener, ServletRequestListener, ServletRequestAttributeListener, HttpSessionListener, HttpSessionAttributeListener, or HttpSessionIdListener.",
                DiagnosticSeverity.Error, "jakarta-servlet", "ImplementListener");

        JakartaForJavaAssert.assertJavaDiagnostics(diagnosticsParams, utils, d);

        // test associated quick-fix code action
        JakartaJavaCodeActionParams codeActionParams = JakartaForJavaAssert.createCodeActionParams(uri, d);

        String newText = "package io.openliberty.sample.jakarta.servlet;\n\nimport jakarta.servlet.annotation." +
                "WebListener;\nimport jakarta.servlet.http.HttpSessionAttributeListener;\n\n@WebListener\n" +
                "public class DontImplementListener implements HttpSessionAttributeListener {\n\n}";
        TextEdit te1 = JakartaForJavaAssert.te(0, 0, 7, 1, newText);
        CodeAction ca1 = JakartaForJavaAssert.ca(uri, "Let 'DontImplementListener' implement 'HttpSessionAttributeListener'", d, te1);

        newText = "package io.openliberty.sample.jakarta.servlet;\n\nimport jakarta.servlet.annotation." +
                "WebListener;\nimport jakarta.servlet.http.HttpSessionIdListener;\n\n@WebListener\n" +
                "public class DontImplementListener implements HttpSessionIdListener {\n\n}";
        TextEdit te2 = JakartaForJavaAssert.te(0, 0, 7, 1, newText);
        CodeAction ca2 = JakartaForJavaAssert.ca(uri, "Let 'DontImplementListener' implement 'HttpSessionIdListener'", d, te2);

        newText = "package io.openliberty.sample.jakarta.servlet;\n\nimport jakarta.servlet.annotation." +
                "WebListener;\nimport jakarta.servlet.http.HttpSessionListener;\n\n@WebListener\npublic" +
                " class DontImplementListener implements HttpSessionListener {\n\n}";
        TextEdit te3 = JakartaForJavaAssert.te(0, 0, 7, 1, newText);
        CodeAction ca3 = JakartaForJavaAssert.ca(uri, "Let 'DontImplementListener' implement 'HttpSessionListener'", d, te3);

        newText = "package io.openliberty.sample.jakarta.servlet;\n\nimport jakarta.servlet." +
                "ServletContextAttributeListener;\nimport jakarta.servlet.annotation.WebListener;\n\n" +
                "@WebListener\npublic class DontImplementListener implements ServletContextAttributeListener {\n\n}";
        TextEdit te4 = JakartaForJavaAssert.te(0, 0, 7, 1, newText);
        CodeAction ca4 = JakartaForJavaAssert.ca(uri, "Let 'DontImplementListener' implement 'ServletContextAttributeListener'", d, te4);

        newText = "package io.openliberty.sample.jakarta.servlet;\n\nimport jakarta.servlet.ServletContextListener;" +
                "\nimport jakarta.servlet.annotation.WebListener;\n\n@WebListener\npublic class DontImplementListener" +
                " implements ServletContextListener {\n\n}";
        TextEdit te5 = JakartaForJavaAssert.te(0, 0, 7, 1, newText);
        CodeAction ca5 = JakartaForJavaAssert.ca(uri, "Let 'DontImplementListener' implement 'ServletContextListener'", d, te5);

        newText = "package io.openliberty.sample.jakarta.servlet;\n\nimport jakarta.servlet." +
                "ServletRequestAttributeListener;\nimport jakarta.servlet.annotation.WebListener;\n\n" +
                "@WebListener\npublic class DontImplementListener implements ServletRequestAttributeListener {\n\n}";
        TextEdit te6 = JakartaForJavaAssert.te(0, 0, 7, 1, newText);
        CodeAction ca6 = JakartaForJavaAssert.ca(uri, "Let 'DontImplementListener' implement 'ServletRequestAttributeListener'", d, te6);

        newText = "package io.openliberty.sample.jakarta.servlet;\n\nimport jakarta.servlet.ServletRequestListener;" +
                "\nimport jakarta.servlet.annotation.WebListener;\n\n@WebListener\npublic class DontImplementListener" +
                " implements ServletRequestListener {\n\n}";
        TextEdit te7 = JakartaForJavaAssert.te(0, 0, 7, 1, newText);
        CodeAction ca7 = JakartaForJavaAssert.ca(uri, "Let 'DontImplementListener' implement 'ServletRequestListener'", d, te7);

        JakartaForJavaAssert.assertJavaCodeAction(codeActionParams, utils, ca1, ca2, ca3, ca4, ca5, ca6, ca7);
    }
}
