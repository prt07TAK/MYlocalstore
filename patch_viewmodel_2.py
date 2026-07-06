import re

with open("app/src/main/java/com/example/ui/StoreViewModel.kt", "r") as f:
    content = f.read()

old_msg = """                val assistantMsg = ChatMessage(
                    text = aiResponse.message,
                    isUser = false,
                    clarifyingQuestion = aiResponse.clarifyingQuestion,
                    matchedProducts = matchedEntities
                )"""

new_msg = """                val assistantMsg = ChatMessage(
                    text = aiResponse.message,
                    isUser = false,
                    intent = aiResponse.intent,
                    recipe = aiResponse.recipe,
                    quickReplies = aiResponse.quickReplies,
                    matchedProducts = matchedEntities.distinctBy { it.id },
                    productReasons = reasonsMap
                )"""

content = content.replace(old_msg, new_msg)

with open("app/src/main/java/com/example/ui/StoreViewModel.kt", "w") as f:
    f.write(content)

