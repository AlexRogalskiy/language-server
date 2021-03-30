/*
 * Copyright (c) 2021, Nadeeshaan Gunasinghe, Nipuna Marcus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lsp.server.core;

import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectKind;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpContext;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WillSaveTextDocumentParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.lsp.server.api.BaseOperationContext;
import org.lsp.server.api.DiagnosticsPublisher;
import org.lsp.server.api.LSContext;
import org.lsp.server.api.completion.BalCompletionContext;
import org.lsp.server.api.completion.BalCompletionResolveContext;
import org.lsp.server.ballerina.compiler.workspace.CompilerManager;
import org.lsp.server.core.completion.BalCompletionRouter;
import org.lsp.server.core.completion.CompletionItemResolver;
import org.lsp.server.core.contexts.ContextBuilder;
import org.lsp.server.core.docsync.BaseDocumentSyncHandler;
import org.lsp.server.core.docsync.DocumentSyncHandler;
import org.lsp.server.core.utils.CommonUtils;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of the {@link TextDocumentService}.
 *
 * @since 1.0.0
 */
public class BalTextDocumentService implements TextDocumentService {
    private final DocumentSyncHandler documentSyncHandler;
    private final LSContext serverContext;

    public BalTextDocumentService(LSContext serverContext) {
        this.serverContext = serverContext;
        this.documentSyncHandler = new BaseDocumentSyncHandler(serverContext);
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        Path uriPath = CommonUtils.uriToPath(params.getTextDocument().getUri());
        BaseOperationContext context = ContextBuilder.baseContext(this.serverContext);
        CompilerManager compilerManager = context.compilerManager();
        Optional<Project> projectForPath = compilerManager.getProject(uriPath);
        /*
        If the project already exists in the compiler manager that means
        we have sent the diagnostics for the project earlier.
        Hence we do not need to publish the diagnostics again.
        This will save a significant number of `publishDiagnostic` calls
        for projects with a many files
         */
        if (projectForPath.isEmpty()) {
            Optional<Project> project = this.documentSyncHandler.didOpen(params, context);
            project.ifPresent(prj -> context.diagnosticPublisher().publish(context, uriPath));
        }
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        BaseOperationContext context = ContextBuilder.baseContext(this.serverContext);
        Optional<Project> project = this.documentSyncHandler.didChange(params, context);
        DiagnosticsPublisher diagnosticsPublisher = context.diagnosticPublisher();
        Path pathUri = CommonUtils.uriToPath(params.getTextDocument().getUri());
        /*
         Publish the diagnostics upon the changes of the document.
         Even this is a single file change, the semantics can 
         affect the whole project. Therefore we have to publish the 
         diagnostics for the whole project.
         */
        project.ifPresent(prj -> diagnosticsPublisher.publish(context, pathUri));
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        Path path = CommonUtils.uriToPath(uri);
        BaseOperationContext context = ContextBuilder.baseContext(this.serverContext);
        CompilerManager compilerManager = context.compilerManager();
        Project project = compilerManager.getProject(path).orElseThrow();

        if (project.kind() == ProjectKind.SINGLE_FILE_PROJECT) {
            this.documentSyncHandler.didClose(params, context);
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {

    }

    @Override
    public CompletableFuture<List<TextEdit>>
    willSaveWaitUntil(WillSaveTextDocumentParams params) {
        ClientCapabilities clientCapabilities =
                this.serverContext.getClientCapabilities().orElseThrow();
        return CompletableFuture.supplyAsync(() -> {
            if (!clientCapabilities.getTextDocument()
                    .getSynchronization().getWillSaveWaitUntil()) {
                return null;
            }
            // Here we do not consider the reason property here
            String uri = params.getTextDocument().getUri();
            Path path = CommonUtils.uriToPath(uri);
//            TextEdit textEdit = TextModifierUtil.withEndingNewLine(path);

//            return Collections.singletonList(textEdit);
            return Collections.emptyList();
        });
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            BalCompletionContext context = ContextBuilder.completionContext(this.serverContext, params);
            return Either.forLeft(BalCompletionRouter.compute(context));
        });
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
        return CompletableFuture.supplyAsync(() -> {
            BalCompletionResolveContext context = ContextBuilder
                    .completionResolveContext(this.serverContext,
                            unresolved);
            
            return CompletionItemResolver.resolve(context);
        });
    }

    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params) {
        SignatureHelpContext context = params.getContext();
        return null;
    }
}
