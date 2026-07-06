package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Recipe(
    val title: String,
    val servings: String,
    val ingredients: List<String>,
    val steps: List<String>
)

data class SuggestedProduct(
    val productId: Long,
    val reason: String
)

data class AssistantResponse(
    val message: String,
    val intent: String,
    val recipe: Recipe?,
    val suggestedProducts: List<SuggestedProduct>,
    val quickReplies: List<String>
)

object GeminiService {
    private const val TAG = "GeminiService"
    
    // Using gemini-3.5-flash for maximum REST compatibility and structured output
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/${MODEL_NAME}:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getShoppingAdvice(
        userPrompt: String,
        catalogProducts: List<ProductEntity>,
        shopsMap: Map<Long, ShopEntity>,
        language: String, // "English" or "Hindi"
        ageBracket: String, // To adapt text style if needed
        chatHistory: List<Pair<String, Boolean>> = emptyList()
    ): AssistantResponse = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is missing! Please configure GEMINI_API_KEY in the Secrets panel.")
            return@withContext getLocalFallback(userPrompt, catalogProducts)
        }

        // Format the local product catalog as a concise list for the model context
        val catalogText = catalogProducts.joinToString("\n") { product ->
            val shopName = shopsMap[product.shopId]?.name ?: "Local Shop"
            val id = product.id
            val name = product.name
            val category = product.subCategory
            val price = product.price
            val shopId = product.shopId
            val inStock = product.inStock
            "{\"product_id\": $id, \"name\": \"$name\", \"category\": \"$category\", \"price\": $price, \"store_id\": \"$shopId\", \"store_name\": \"$shopName\", \"in_stock\": $inStock}"
        }

                val systemInstruction = """
You are Local Store AI, an intelligent shopping assistant inside the Local Store app.

The Local Store app connects customers with nearby local shops including:

• Grocery/Kirana
• Fruits & Vegetables
• Sweet Shops
• Gift Shops
• Bakeries
• Flower Shops
• Cosmetics
• Electronics
• Medical Stores
• Clothing
• Stationery
• Home Decor
• Toys
• Restaurants
• Dairy
• Pet Stores
• Hardware
• Furniture

Your job is NOT only to answer questions.

Your main goal is to help users discover products sold by nearby local stores and increase successful purchases.

----------------------------------------------------
BEHAVIOR
----------------------------------------------------

Understand the user's intention.

Possible intents include

- Buy product
- Gift recommendation
- Recipe
- Festival shopping
- Birthday
- Anniversary
- Grocery shopping
- Daily needs
- Party planning
- Baby products
- Electronics
- Medicine
- Fashion
- Home decoration
- Wedding
- Travel essentials

Always think:

"What products from nearby stores can help this user?"

----------------------------------------------------
IF USER WANTS TO BUY SOMETHING
----------------------------------------------------

Suggest products.

Example

User:
"I need a birthday gift for my girlfriend"

Recommend categories like

Flowers
Chocolate
Soft Toys
Perfume
Jewelry
Handbag
Greeting Card
Cosmetics
Photo Frame

Return matching products.

----------------------------------------------------
IF USER ASKS A RECIPE
----------------------------------------------------

Provide

1. Recipe
2. Cooking steps
3. Ingredients
4. Suggest products required

Example

"How to make Shahi Paneer"

Return

Recipe

PLUS

Paneer
Cream
Butter
Tomatoes
Onions
Spices
Oil
Coriander

Every ingredient should become a product recommendation.

----------------------------------------------------
IF USER ASKS FOR FESTIVAL
----------------------------------------------------

Example

"I am celebrating Diwali"

Suggest

Sweets
Dry Fruits
Decorations
Lights
Gift Boxes
Candles

----------------------------------------------------
IF USER ASKS FOR BABY PRODUCTS
----------------------------------------------------

Suggest

Diapers
Baby Powder
Baby Soap
Baby Wipes
Baby Lotion

----------------------------------------------------
IF USER ASKS FOR TRAVEL
----------------------------------------------------

Suggest

Water Bottles
Snacks
Power Bank
Medicine
Backpack
Sunglasses

----------------------------------------------------
IF USER ASKS FOR PARTY
----------------------------------------------------

Suggest

Cake
Cold Drinks
Chips
Ice Cream
Decorations
Disposable Plates
Candles

----------------------------------------------------
IMPORTANT

Never invent products.

Only recommend products that exist in the nearby stores provided by the app.

If no product exists

Return

{
"products":[]
}

and politely explain.

----------------------------------------------------
RESPONSE FORMAT

Always respond ONLY in JSON.

Schema

{
  "message":"Friendly AI response",
  "intent":"gift",
  "products":[
      {
        "id":"123",
        "name":"Ferrero Rocher 16 Pieces",
        "price":899,
        "discount":10,
        "store":"Raj Gift House",
        "distance":"1.2 km",
        "image":"IMAGE_URL",
        "rating":4.8,
        "category":"Chocolate",
        "available":true
      }
  ],
  "recipe":{
  },
  "actions":[
      {
         "type":"add_to_cart",
         "product_id":"123"
      }
  ]
}

Never return markdown.
Never use code blocks.
Return valid JSON only.

CURRENT_CATALOG:
[
$catalogText
]
        """.trimIndent()

        val requestBodyJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                chatHistory.forEach { (text, isUser) ->
                    if (text.isNotBlank()) {
                        put(JSONObject().apply {
                            put("role", if (isUser) "user" else "model")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", text)
                                })
                            })
                        })
                    }
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", userPrompt)
                        })
                    })
                })
            }
            put("contents", contentsArray)
            
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstruction)
                    })
                })
            })
            
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.3)
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(API_URL)
            .header("x-goog-api-key", apiKey)
            .post(requestBodyJson.toString().toRequestBody(mediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "API Request failed with code: ${response.code}. Using local fallback.")
                    return@withContext getLocalFallback(userPrompt, catalogProducts)
                }
                val responseBodyStr = response.body?.string() ?: ""
                Log.d(TAG, "Raw Gemini Response: $responseBodyStr")
                
                val responseJson = JSONObject(responseBodyStr)
                val candidates = responseJson.getJSONArray("candidates")
                val textResponse = candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                val cleanJsonStr = textResponse.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val parsedObj = JSONObject(cleanJsonStr)
                
                val message = parsedObj.optString("message", parsedObj.optString("reply_text", "Here are some recommendations."))
                val intent = parsedObj.optString("intent", "other")
                
                var recipeObj: Recipe? = null
                if (!parsedObj.isNull("recipe")) {
                    val rJson = parsedObj.optJSONObject("recipe")
                    if (rJson != null) {
                        val ingredientsList = mutableListOf<String>()
                        val iArray = rJson.optJSONArray("ingredients")
                        if (iArray != null) {
                            for (i in 0 until iArray.length()) {
                                ingredientsList.add(iArray.getString(i))
                            }
                        }
                        val stepsList = mutableListOf<String>()
                        val sArray = rJson.optJSONArray("steps")
                        if (sArray != null) {
                            for (i in 0 until sArray.length()) {
                                stepsList.add(sArray.getString(i))
                            }
                        }
                        recipeObj = Recipe(
                            title = rJson.optString("title", ""),
                            servings = rJson.optString("servings", ""),
                            ingredients = ingredientsList,
                            steps = stepsList
                        )
                    }
                }
                
                                val suggestedProducts = mutableListOf<SuggestedProduct>()
                val productsArray = parsedObj.optJSONArray("products")
                if (productsArray != null) {
                    for (i in 0 until productsArray.length()) {
                        val pObj = productsArray.getJSONObject(i)
                        val idStr = pObj.optString("id", pObj.optString("product_id"))
                        val id = idStr.toLongOrNull()
                        if (id != null) {
                            suggestedProducts.add(SuggestedProduct(id, pObj.optString("reason", "")))
                        }
                    }
                }
                
                val actionsArray = parsedObj.optJSONArray("actions")
                if (actionsArray != null) {
                    for (i in 0 until actionsArray.length()) {
                        val actionObj = actionsArray.getJSONObject(i)
                        if (actionObj.optString("type") == "add_to_cart") {
                            val idStr = actionObj.optString("product_id", actionObj.optString("id"))
                            val id = idStr.toLongOrNull()
                            if (id != null && suggestedProducts.none { it.productId == id }) {
                                suggestedProducts.add(SuggestedProduct(id, "Recommended"))
                            }
                        }
                    }
                }
                val quickRepliesList = mutableListOf<String>()
                val qrArray = parsedObj.optJSONArray("quick_replies")
                if (qrArray != null) {
                    for (i in 0 until qrArray.length()) {
                        quickRepliesList.add(qrArray.getString(i))
                    }
                }

                AssistantResponse(
                    message = message,
                    intent = intent,
                    recipe = recipeObj,
                    suggestedProducts = suggestedProducts,
                    quickReplies = quickRepliesList
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Gemini API call: ${e.message}", e)
            getLocalFallback(userPrompt, catalogProducts)
        }
    }

    private fun getLocalFallback(userPrompt: String, catalogProducts: List<ProductEntity>): AssistantResponse {
        val promptLower = userPrompt.lowercase()
        
        // Simple language detection for fallback responses
        val isHindi = promptLower.contains("क्या") || promptLower.contains("है") || 
                      promptLower.contains("मदद") || promptLower.contains("चाहिए") || 
                      promptLower.contains("दुकान") || promptLower.contains("उपलब्ध") || 
                      promptLower.contains("सब्जी") || promptLower.contains("पनीर") || 
                      promptLower.contains("गिफ्ट") || promptLower.contains("माँ") || 
                      promptLower.contains("गर्लफ्रेंड") || promptLower.contains("बनाना")

        // 1. DYNAMIC SEARCH MATCHING: Check if the user is asking for specific products or words
        val searchMatches = catalogProducts.filter { prod ->
            val nameLower = prod.name.lowercase()
            val catLower = prod.subCategory.lowercase()
            promptLower.contains(nameLower) || nameLower.contains(promptLower) ||
            promptLower.split(" ", "-", "/").any { word ->
                word.length > 3 && (nameLower.contains(word) || catLower.contains(word))
            }
        }

        // 2. GIRLFRIEND / WIFE GIFT FLOW
        if (promptLower.contains("girlfriend") || promptLower.contains("girl") || 
            promptLower.contains("gf") || promptLower.contains("wife") || 
            promptLower.contains("love") || promptLower.contains("fiance") ||
            promptLower.contains("गर्लफ्रेंड") || promptLower.contains("पत्नी")) {
            
            val giftProducts = catalogProducts.filter { prod ->
                prod.name.contains("Teddy", ignoreCase = true) ||
                prod.name.contains("Chocolate", ignoreCase = true) ||
                prod.name.contains("Saree", ignoreCase = true) ||
                prod.name.contains("Frame", ignoreCase = true) ||
                prod.name.contains("Candle", ignoreCase = true)
            }
            val matchIds = giftProducts.map { it.id }.take(5)
            
            val msg = if (isHindi) {
                "अपनी गर्लफ्रेंड/पत्नी के लिए उपहार ढूंढ रहे हैं? यहाँ हमारे स्थानीय स्टोर पर कुछ बेहतरीन विकल्प उपलब्ध हैं:\n\n" +
                "- **राजू गिफ्ट कॉर्नर**: एक प्यारा सा सॉफ्ट पिंक टेडी बियर (Rs 220) या खुशबूदार मोमबत्ती का सेट (Rs 180).\n" +
                "- **मिलन गारमेंट्स**: एक खूबसूरत लाल और सुनहरी सूती साड़ी (Rs 450).\n" +
                "- **राजू गिफ्ट कॉर्नर**: शानदार प्रीमियम चॉकलेट बॉक्स (Rs 300) जो उनके चेहरे पर मुस्कान ला देगा!"
            } else {
                "Looking for a gift for your girlfriend or wife? Here are some lovely choices available locally in your area right now:\n\n" +
                "- **Raju Gift Corner**: A soft pink Teddy Bear (Rs 220) or a premium Luxury Chocolate Box (Rs 300).\n" +
                "- **Raju Gift Corner**: A beautiful Scented Candle Set (Rs 180) to add warmth.\n" +
                "- **Milan Garments**: A gorgeous red and gold Cotton Saree (Rs 450) for a traditional touch."
            }
            val clarifyingQuestion = if (isHindi) "क्या आप उनके लिए गुलाब जामुन या काजू कतली जैसी कुछ मिठाइयां भी जोड़ना चाहते हैं?" else "Would you also like to add some premium sweets like Kaju Katli for her?"
            return AssistantResponse(
                message = msg,
                intent = "other",
                recipe = null,
                suggestedProducts = matchIds.map { SuggestedProduct(it, "") },
                quickReplies = if (clarifyingQuestion.isNotEmpty()) listOf(clarifyingQuestion) else emptyList()
            )
        }

        // 3. MOTHER / MOM FLOW (explicitly only when mom/mother is asked and not girlfriend)
        if (promptLower.contains("mom") || promptLower.contains("mother") || 
            promptLower.contains("माँ") || promptLower.contains("माता") ||
            promptLower.contains("mummy") || promptLower.contains("mother's")) {
            
            val motherProducts = catalogProducts.filter { prod ->
                prod.name.contains("Saree", ignoreCase = true) ||
                prod.name.contains("Mug", ignoreCase = true) ||
                prod.name.contains("Frame", ignoreCase = true) ||
                prod.name.contains("Thali", ignoreCase = true) ||
                prod.name.contains("Katli", ignoreCase = true)
            }
            val matchIds = motherProducts.map { it.id }.take(5)
            
            val msg = if (isHindi) {
                "आपकी आदरणीय माता जी के लिए जन्मदिन या विशेष अवसर पर उपहार के कुछ सुंदर विकल्प हमारे स्थानीय बाजार में उपलब्ध हैं:\n\n" +
                "- **मिलन गारमेंट्स**: एक सुंदर पारंपरिक कॉटन साड़ी (Rs 450).\n" +
                "- **राजू गिफ्ट कॉर्नर**: विशेष 'Best Mom Ever' प्रिंटेड कॉफ़ी मग (Rs 120) या सुंदर फोटो फ्रेम (Rs 150).\n" +
                "- **वर्मा मिठाई भंडार**: शुद्ध देशी घी की स्पेशल काजू कतली (Rs 250) जो उन्हें बेहद पसंद आएगी!"
            } else {
                "For your respected mother's birthday or special occasion, here are some beautiful gift choices available in our local market:\n\n" +
                "- **Milan Garments**: A beautiful traditional Cotton Saree (Rs 450).\n" +
                "- **Raju Gift Corner**: A special 'Best Mom Ever' printed Coffee Mug (Rs 120) or an elegant Photo Frame (Rs 150).\n" +
                "- **Verma Mithai Bhandar**: Pure Desi Ghee special Kaju Katli (Rs 250) that she will absolutely love!"
            }
            val clarifyingQuestion = if (isHindi) "क्या आप उनके लिए कोई विशेष पूजा की थाली भी देखना चाहेंगे?" else "Would you also like to see any special Pooja Thali for her?"
            return AssistantResponse(
                message = msg,
                intent = "other",
                recipe = null,
                suggestedProducts = matchIds.map { SuggestedProduct(it, "") },
                quickReplies = if (clarifyingQuestion.isNotEmpty()) listOf(clarifyingQuestion) else emptyList()
            )
        }

        // 4. GENERAL GIFT FLOW
        if (promptLower.contains("gift") || promptLower.contains("present") || 
            promptLower.contains("birthday") || promptLower.contains("उपहार") || 
            promptLower.contains("गिफ्ट") || promptLower.contains("जन्मदिन")) {
            
            val giftProducts = catalogProducts.filter { prod ->
                prod.name.contains("Teddy", ignoreCase = true) ||
                prod.name.contains("Chocolate", ignoreCase = true) ||
                prod.name.contains("Frame", ignoreCase = true) ||
                prod.name.contains("Candle", ignoreCase = true) ||
                prod.name.contains("Mug", ignoreCase = true)
            }
            val matchIds = giftProducts.map { it.id }.take(5)
            
            val msg = if (isHindi) {
                "यहाँ हमारे आस-पास के गिफ्ट कॉर्नर पर स्थानीय रूप से उपलब्ध कुछ बेहतरीन और लोकप्रिय उपहार आइटम दिए गए हैं:\n\n" +
                "- **राजू गिफ्ट कॉर्नर**: एक प्रीमियम लक्ज़री चॉकलेट बॉक्स (Rs 300) और एक सॉफ्ट पिंक टेडी बियर (Rs 220).\n" +
                "- **राजू गिफ्ट कॉर्नर**: सुगंधित मोमबत्ती सेट (Rs 180) या एक सुरुचिपूर्ण मध्यम फोटो फ्रेम (Rs 150) किसी भी अवसर के लिए उपयुक्त है।"
            } else {
                "Here are some excellent and popular gift items available locally at our nearby Gift Corner:\n\n" +
                "- **Raju Gift Corner**: A premium Luxury Chocolate Box (Rs 300) and a soft pink Teddy Bear (Rs 220).\n" +
                "- **Raju Gift Corner**: Scented Candle Set (Rs 180) or an elegant medium Photo Frame (Rs 150) suitable for any occasion."
            }
            
            val clarifyingQuestion = if (isHindi) "क्या आप इसके साथ कुछ स्थानीय ताजी मिठाइयाँ भी जोड़ना चाहते हैं?" else "Would you also like to browse special sweets or chocolates from the shop?"
            return AssistantResponse(
                message = msg,
                intent = "other",
                recipe = null,
                suggestedProducts = matchIds.map { SuggestedProduct(it, "") },
                quickReplies = if (clarifyingQuestion.isNotEmpty()) listOf(clarifyingQuestion) else emptyList()
            )
        }

        // 5. SPECIFIC PRODUCT WORD MATCHING (Dynamic search fallback)
        if (searchMatches.isNotEmpty()) {
            val matchIds = searchMatches.map { it.id }.take(5)
            val namesList = searchMatches.take(3).joinToString(", ") { it.name }
            val msg = if (isHindi) {
                "मुझे आपके प्रश्न से संबंधित कुछ उत्पाद मिले हैं जो पास के स्टोर्स में उपलब्ध हैं! मैंने नीचे आपके लिए $namesList इत्यादि सूचीबद्ध किए हैं। आप सीधे अपने कार्ट में जोड़ सकते हैं।"
            } else {
                "I found some products matching your query at nearby local shops! I have listed $namesList and other matching items below for you. You can add them straight to your cart."
            }
            val clarifyingQuestion = if (isHindi) "क्या आप इस श्रेणी के अन्य सामान भी देखना चाहते हैं?" else "Do you need any other items from this category?"
            return AssistantResponse(
                message = msg,
                intent = "other",
                recipe = null,
                suggestedProducts = matchIds.map { SuggestedProduct(it, "") },
                quickReplies = if (clarifyingQuestion.isNotEmpty()) listOf(clarifyingQuestion) else emptyList()
            )
        }

        // 6. COOKING / RECIPE FLOW
        if (promptLower.contains("paneer") || promptLower.contains("matar") || 
            promptLower.contains("recipe") || promptLower.contains("cook") || 
            promptLower.contains("sabji") || promptLower.contains("dinner") || 
            promptLower.contains("lunch") || promptLower.contains("पनीर") || 
            promptLower.contains("सब्जी") || promptLower.contains("रसोई")) {
            
            val matchIds = catalogProducts.filter { prod ->
                prod.name.contains("Paneer", ignoreCase = true) ||
                prod.name.contains("Peas", ignoreCase = true) ||
                prod.name.contains("Tomato", ignoreCase = true) ||
                prod.name.contains("Onion", ignoreCase = true) ||
                prod.name.contains("Potato", ignoreCase = true) ||
                prod.name.contains("Ginger", ignoreCase = true) ||
                prod.name.contains("Oil", ignoreCase = true)
            }.map { it.id }.take(5)

            val msg = if (isHindi) {
                "मटर पनीर बनाने की सोच रहे हैं? बहुत बढ़िया चुनाव! यहाँ एक आसान 3-चरण रेसिपी है:\n\n1. कटे हुए प्याज, टमाटर की प्यूरी और अदरक-लहसुन के पेस्ट को सरसों के तेल में अच्छी तरह भूनें。\n2. हरी मटर, ताजे पनीर के टुकड़े, हल्दी और मिर्च पाउडर डालकर 1 कप पानी मिलाएं。\n3. 10 मिनट तक धीमी आंच पर पकाएं। गरमागरम रोटियों के साथ परोसें!\n\nमैंने स्थानीय दुकानों से सभी आवश्यक सामग्रियां नीचे सूचीबद्ध कर दी हैं।"
            } else {
                "Making Matar Paneer? Great choice! Here is a simple 3-step recipe:\n\n1. Sauté chopped onions, tomato puree, and ginger-garlic paste in 2 spoons of mustard oil until cooked.\n2. Add green peas (matar), soft paneer cubes, turmeric, and chili powder with 1 cup of water.\n3. Simmer for 10 minutes. Serve hot with warm rotis!\n\nI have matched the essential ingredients with active products in your local shops below."
            }
            val clarifyingQuestion = if (isHindi) "क्या आपको रोटियों के लिए ताजा हरा धनिया या गेहूं का आटा भी चाहिए?" else "Do you also need fresh coriander leaves or wheat flour (atta) for rotis?"
            return AssistantResponse(
                message = msg,
                intent = "other",
                recipe = null,
                suggestedProducts = matchIds.map { SuggestedProduct(it, "") },
                quickReplies = if (clarifyingQuestion.isNotEmpty()) listOf(clarifyingQuestion) else emptyList()
            )
        }

        // 7. GROCERY / KIRANA FLOW
        if (promptLower.contains("grocery") || promptLower.contains("kirana") || 
            promptLower.contains("oil") || promptLower.contains("salt") || 
            promptLower.contains("atta") || promptLower.contains("tea") || 
            promptLower.contains("sugar") || promptLower.contains("किराना") || 
            promptLower.contains("तेल") || promptLower.contains("आटा") || 
            promptLower.contains("चाय")) {
            
            val matchIds = catalogProducts.filter { prod ->
                prod.name.contains("Atta", ignoreCase = true) ||
                prod.name.contains("Oil", ignoreCase = true) ||
                prod.name.contains("Salt", ignoreCase = true) ||
                prod.name.contains("Tea", ignoreCase = true) ||
                prod.name.contains("Sugar", ignoreCase = true)
            }.map { it.id }.take(5)

            val msg = if (isHindi) {
                "मुझे गुप्ता किराना स्टोर पर ये दैनिक आवश्यक सामग्रियां मिल गई हैं। वे 15 मिनट में त्वरित डिलीवरी के लिए तैयार हैं!"
            } else {
                "I found these daily essentials in stock at Gupta Kirana Store. They are ready for quick delivery in 15 minutes!"
            }
            return AssistantResponse(
                message = msg,
                intent = "other",
                recipe = null,
                suggestedProducts = matchIds.map { SuggestedProduct(it, "") },
                quickReplies = emptyList()
            )
        }

        // 8. DEFAULT FALLBACK
        val matchIds = catalogProducts.shuffled().take(4).map { it.id }
        val msg = if (isHindi) {
            "नमस्ते! मैं आपका स्थानीय स्टोर AI सहायक हूँ। मैं आपके आस-पास की शीर्ष दुकानों से सामान ढूंढने में आपकी मदद कर सकता हूँ। मुझसे पूछें:\n\n- *'मटर पनीर बनाने की सामग्री'* \n- *'गर्लफ्रेंड के लिए गिफ्ट आइडियाज'* \n- *'माँ के लिए जन्मदिन का उपहार'* \n- *'घर का किराना सामान दिखाओ'*"
        } else {
            "Hello! I am your My Local Store AI Assistant. I can help you find products from top local shops. Try asking me:\n\n- *'I want to make matar paneer'* \n- *'What can I gift my girlfriend?'* \n- *'What can I gift my mom for her birthday?'* \n- *'Show me available groceries nearby'*"
        }
        val clarifyingQuestion = if (isHindi) "आज आप किस श्रेणी की दुकानें देखना चाहते हैं?" else "What category of shops are you looking for today?"
        return AssistantResponse(
            message = msg,
            intent = "other",
            recipe = null,
            suggestedProducts = matchIds.map { SuggestedProduct(it, "") },
            quickReplies = if (clarifyingQuestion.isNotEmpty()) listOf(clarifyingQuestion) else emptyList()
        )
    }
}
