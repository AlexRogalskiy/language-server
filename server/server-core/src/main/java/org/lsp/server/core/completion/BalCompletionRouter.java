package org.lsp.server.core.completion;

import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.NonTerminalNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import org.eclipse.lsp4j.CompletionItem;
import org.lsp.server.api.completion.BalCompletionContext;
import org.lsp.server.core.completion.utils.ContextEvaluator;

import java.util.Collections;
import java.util.List;

public class BalCompletionRouter {
    public static List<CompletionItem> compute(BalCompletionContext ctx) {
        ContextEvaluator.fillTokenInfoAtCursor(ctx);
        NonTerminalNode node = ctx.getNodeAtCursor();

        if (node.kind() == SyntaxKind.MODULE_PART) {
            return new ModulePartNodeContextProvider6_8()
                    .getCompletions((ModulePartNode) node, ctx);
        }
        
        return Collections.emptyList();
    }
}
