package org.lsp.server.core.completion.utils;

import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.InsertTextFormat;
import org.lsp.server.api.context.BalCompletionContext;

public class SnippetBlock {
    private final String label;
    private final String snippet;
    private final String plainText;

    public SnippetBlock(String label, String snippet, String plainText) {
        this.label = label;
        this.snippet = snippet;
        this.plainText = plainText;
    }

    /**
     * Create a completion item for the snippet
     *
     * @param ctx completion context
     * @return modified Completion Item
     */
    public CompletionItem build(BalCompletionContext ctx) {
        CompletionItem item = new CompletionItem();
        String insertText = this.snippet;
        CompletionCapabilities capabilities = ctx.clientCapabilities();
        Boolean snippetSupport =
                capabilities.getCompletionItem().getSnippetSupport();
        if (snippetSupport) {
            item.setInsertText(this.snippet);
            item.setInsertTextFormat(InsertTextFormat.Snippet);
        } else {
            item.setInsertText(this.plainText);
            item.setInsertTextFormat(InsertTextFormat.PlainText);
        }
        item.setLabel(label);

        return item;
    }
}