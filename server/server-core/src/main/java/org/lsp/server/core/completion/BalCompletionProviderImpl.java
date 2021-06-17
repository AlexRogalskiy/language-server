package org.lsp.server.core.completion;

import io.ballerina.compiler.api.symbols.AnnotationSymbol;
import io.ballerina.compiler.api.symbols.ClassSymbol;
import io.ballerina.compiler.api.symbols.Documentation;
import io.ballerina.compiler.api.symbols.FunctionSymbol;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.SymbolKind;
import io.ballerina.compiler.api.symbols.TypeDefinitionSymbol;
import io.ballerina.compiler.syntax.tree.Node;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemCapabilities;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemTag;
import org.eclipse.lsp4j.CompletionItemTagSupportCapabilities;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.lsp.server.api.context.BalCompletionContext;
import org.lsp.server.api.context.BalCompletionProvider;
import org.lsp.server.core.utils.CommonUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public abstract class BalCompletionProviderImpl<T extends Node> implements BalCompletionProvider<T> {
    @Override
    public boolean enabled() {
        return false;
    }
    
    protected List<CompletionItem>
    getTypeCompletionItems(BalCompletionContext context) {
        List<CompletionItem> completionItems = new ArrayList<>();
        for (Symbol symbol : context.visibleSymbols()) {
            if (symbol.kind() == SymbolKind.TYPE_DEFINITION) {
                TypeDefinitionSymbol tDesc = (TypeDefinitionSymbol) symbol;
                CompletionItem item = new CompletionItem();
                
                item.setKind(CompletionItemKind.TypeParameter);
                item.setLabel(symbol.getName().get());
                item.setInsertText(symbol.getName().get());
                // Set the type signature as the detail
                item.setDetail(tDesc.typeDescriptor().signature());
                
                List<AnnotationSymbol> annotations = tDesc.annotations();
                boolean deprecated = annotations.stream()
                        .anyMatch(annot ->annot.getName()
                                .orElse("").equals("deprecated"));
                if (deprecated) {
                    item.setTags(Collections
                            .singletonList(CompletionItemTag.Deprecated));
                }
                completionItems.add(item);
            }
        }
        
        return completionItems;
    }

    /**
     * Convert the symbols to the completion items.
     *
     * @param symbols symbols to be convert
     * @param context Completion context
     * @return {@link List} of completions
     */
    protected List<CompletionItem> convert(List<? extends Symbol> symbols, BalCompletionContext context) {
        List<CompletionItem> completionItems = new ArrayList<>();
        for (Symbol symbol : symbols) {
            if (symbol.getName().isEmpty()) {
                continue;
            }
            CompletionItem cItem = new CompletionItem();
            // Set the insert text and the label
            cItem.setInsertText(symbol.getName().get());
            cItem.setLabel(symbol.getName().get());
            this.setDocumentation(symbol, context, cItem);
            this.setTags(symbol, context, cItem);
            completionItems.add(cItem);
        }

        return completionItems;
    }

    private void setTags(Symbol symbol, BalCompletionContext context, CompletionItem cItem) {
        List<AnnotationSymbol> annotations;
        switch (symbol.kind()) {
            case CLASS:
                annotations = ((ClassSymbol) symbol).annotations();
                break;
            case FUNCTION:
                annotations = ((FunctionSymbol) symbol).annotations();
                break;
            case TYPE_DEFINITION:
                annotations = ((TypeDefinitionSymbol) symbol).annotations();
                break;
            default:
                annotations = Collections.emptyList();
                break;
        }
        CompletionItemCapabilities itemCapabilities = context.clientCapabilities().getCompletionItem();
        CompletionItemTagSupportCapabilities tagSupport = itemCapabilities.getTagSupport();
        List<CompletionItemTag> supportedTags = tagSupport.getValueSet();

        Optional<AnnotationSymbol> deprecatedAnnotation = annotations.stream()
                .filter(annot -> annot.getName().orElse("").equals("deprecated"))
                .findAny();
        
        if (deprecatedAnnotation.isPresent() && supportedTags.contains(CompletionItemTag.Deprecated)) {
            cItem.setTags(Collections.singletonList(CompletionItemTag.Deprecated));
        }
    }

    private void setDocumentation(Symbol symbol, BalCompletionContext context,
                                                           CompletionItem cItem) {
        Optional<Documentation> documentation;
        
        switch (symbol.kind()) {
            case CLASS:
                documentation = ((ClassSymbol) symbol).documentation();
                break;
            case FUNCTION:
                documentation = ((FunctionSymbol) symbol).documentation();
                break;
            case TYPE_DEFINITION:
                documentation = ((TypeDefinitionSymbol) symbol).documentation();
                break;
            default:
                documentation = Optional.empty();
                break;

        }
        if (documentation.isEmpty() || documentation.get().description().isEmpty()) {
            return;
        }
        CompletionItemCapabilities capabilities = context.clientCapabilities().getCompletionItem();
        String description = documentation.get().description().get();
        List<String> docFormat = capabilities.getDocumentationFormat();
        Either<String, MarkupContent> itemDocs;
        if (docFormat.contains(MarkupKind.MARKDOWN)) {
            MarkupContent markupContent = new MarkupContent();
            markupContent.setKind(MarkupKind.MARKDOWN);
            markupContent.setValue("## Description " + CommonUtils.MD_LINE_SEPARATOR + description);
            
            itemDocs = Either.forRight(markupContent);
        } else {
            itemDocs = Either.forLeft(description);
        }
        
        cItem.setDocumentation(itemDocs);
    }
}
