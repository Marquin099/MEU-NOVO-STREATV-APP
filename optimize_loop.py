import os

file_path = "app/src/main/java/com/example/ui/screens/MainScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Let's find the categories.forEachIndexed block and replace it
target = """                                         categories.forEachIndexed { idx, category ->
                                             // If Filmes or Series, show ONLY the focused category row (idx == focusedCategoryIndex)
                                             val isVisible = if (targetState == "Filmes" || targetState == "Series") {
                                                 idx == focusedCategoryIndex
                                             } else {
                                                 idx >= focusedCategoryIndex
                                             }

                                             // Animated hide/collapse block
                                             AnimatedVisibility(
                                                 visible = isVisible,
                                                 enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                                                 exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
                                             ) {
                                                 val catItems = remember(groupedSource, category) {
                                                     groupedSource[category] ?: emptyList()
                                                 }
                                                 if (catItems.isNotEmpty()) {
                                                     CategoryRow(
                                                         title = category,
                                                         items = catItems,
                                                         isSelectedHighlight = (idx == focusedCategoryIndex),
                                                         onPlay = onItemPlayOrDetail,
                                                         onItemFocused = { item ->
                                                             activeFocusedItem = item
                                                             if (item.type != "live") {
                                                                 viewModel.enrichItemOnDemand(item)
                                                             }
                                                         },
                                                         firstItemFocusRequester = if (idx == focusedCategoryIndex) contentFocusRequester else null,
                                                         onItemRendered = { item ->
                                                             if (item.type != "live") {
                                                                 viewModel.enrichItemOnDemand(item)
                                                             }
                                                         }
                                                     )
                                                     Spacer(modifier = Modifier.height(16.dp))
                                                 }
                                             }
                                         }"""

replacement = """                                         categories.forEachIndexed { idx, category ->
                                             // Optimize rendering: Only compose categories that are actually visible!
                                             // For Filmes and Series, only the focused category row is shown.
                                             // For other tabs (like Canais), we show the focused row and preload the next 4 rows for ultra-smooth scrolling.
                                             val isVisible = if (targetState == "Filmes" || targetState == "Series") {
                                                 idx == focusedCategoryIndex
                                             } else {
                                                 idx >= focusedCategoryIndex && idx <= focusedCategoryIndex + 4
                                             }

                                             if (isVisible) {
                                                 val catItems = remember(groupedSource, category) {
                                                     groupedSource[category] ?: emptyList()
                                                 }
                                                 if (catItems.isNotEmpty()) {
                                                     CategoryRow(
                                                         title = category,
                                                         items = catItems,
                                                         isSelectedHighlight = (idx == focusedCategoryIndex),
                                                         onPlay = onItemPlayOrDetail,
                                                         onItemFocused = { item ->
                                                             activeFocusedItem = item
                                                             if (item.type != "live") {
                                                                 viewModel.enrichItemOnDemand(item)
                                                             }
                                                         },
                                                         firstItemFocusRequester = if (idx == focusedCategoryIndex) contentFocusRequester else null,
                                                         onItemRendered = { item ->
                                                             if (item.type != "live") {
                                                                 viewModel.enrichItemOnDemand(item)
                                                             }
                                                         }
                                                     )
                                                     Spacer(modifier = Modifier.height(16.dp))
                                                 }
                                             }
                                         }"""

# Normalizing carriage returns
normalized_content = content.replace("\r\n", "\n")
normalized_target = target.replace("\r\n", "\n")
normalized_replacement = replacement.replace("\r\n", "\n")

if normalized_target in normalized_content:
    new_content = normalized_content.replace(normalized_target, normalized_replacement)
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(new_content)
    print("Success: Replacement applied successfully!")
else:
    # Try finding with dynamic whitespace
    import re
    # Convert target into regex
    regex_pattern = re.escape(normalized_target)
    # Allow variable spaces and line endings
    regex_pattern = regex_pattern.replace(r"\ ", r"\s*").replace(r"\n", r"\s*\n\s*")
    match = re.search(regex_pattern, normalized_content)
    if match:
        new_content = normalized_content[:match.start()] + normalized_replacement + normalized_content[match.end():]
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(new_content)
        print("Success: Replacement applied via regex!")
    else:
        print("Error: Target pattern not found in file!")
