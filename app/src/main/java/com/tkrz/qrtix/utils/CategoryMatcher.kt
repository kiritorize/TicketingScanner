package com.tkrz.qrtix.utils
    
    data class CategoryMappingResult(
        val formCategory: String,
        val mappedDbCategory: String?,
        val isExactMatch: Boolean,
        val needsManualReview: Boolean
    )
    
    object CategoryMatcher {
    
        /**
         * Maps a list of category values from a Google Form to the existing database categories.
         *
         * @param formCategories Unique list of category strings found in the Google Form
         * @param dbCategories List of existing category strings in the QRTix database
         * @return A list of CategoryMappingResult describing the suggested mapping for each form category
         */
        fun mapCategories(formCategories: List<String>, dbCategories: List<String>): List<CategoryMappingResult> {
            // Sort DB categories by length descending to prevent shorter strings from matching first
            // e.g. "VVIP" should be checked before "VIP"
            val sortedDbCategories = dbCategories.sortedByDescending { it.length }
    
            return formCategories.map { formCategory ->
                val match = findBestMatch(formCategory, sortedDbCategories)
                
                CategoryMappingResult(
                    formCategory = formCategory,
                    mappedDbCategory = match,
                    isExactMatch = match != null && match.equals(formCategory, ignoreCase = true),
                    needsManualReview = match == null
                )
            }
        }
    
        private fun findBestMatch(formCategory: String, sortedDbCategories: List<String>): String? {
            val normalizedForm = formCategory.trim().lowercase()
    
            // Priority 1: Exact Match
            sortedDbCategories.find { it.trim().lowercase() == normalizedForm }?.let { return it }
    
            // Priority 2: DB category is substring of Form value
            sortedDbCategories.find { dbCat ->
                normalizedForm.contains(dbCat.trim().lowercase())
            }?.let { return it }
    
            // Extract words from the form category for further checks
            val formWords = normalizedForm.split(Regex("\\W+")).filter { it.isNotBlank() }
    
            // Priority 3: Form word is substring of DB category
            // e.g., Form has "Regular (50k)", DB has "REGULER". Form words: "regular", "50k".
            // Since "regular" is not a substring of "reguler", we need fuzzy match for this specific case, 
            // but if DB had "REGULER TIKET", and form had "TIKET", it would match.
            for (dbCat in sortedDbCategories) {
                val normalizedDb = dbCat.trim().lowercase()
                for (word in formWords) {
                    // Only consider words that are somewhat descriptive (length > 2)
                    if (word.length > 2 && normalizedDb.contains(word)) {
                        return dbCat
                    }
                }
            }
    
            // Priority 4: Fuzzy Match (Levenshtein Distance)
            for (dbCat in sortedDbCategories) {
                val normalizedDb = dbCat.trim().lowercase()
                for (word in formWords) {
                    if (word.length > 3) {
                        val distance = levenshtein(word, normalizedDb)
                        // Allow up to 2 edits for a match (e.g. "Reguler" vs "Regular" is 1 edit)
                        if (distance <= 2) {
                            return dbCat
                        }
                    }
                }
            }
    
            return null
        }
    
        /**
         * Calculates the Levenshtein distance between two strings.
         */
        private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
            if (lhs == rhs) {
                return 0
            }
            if (lhs.isEmpty()) {
                return rhs.length
            }
            if (rhs.isEmpty()) {
                return lhs.length
            }
    
            val lhsLength = lhs.length + 1
            val rhsLength = rhs.length + 1
    
            var cost = Array(lhsLength) { it }
            var newCost = Array(lhsLength) { 0 }
    
            for (i in 1 until rhsLength) {
                newCost[0] = i
    
                for (j in 1 until lhsLength) {
                    val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
    
                    val costReplace = cost[j - 1] + match
                    val costInsert = cost[j] + 1
                    val costDelete = newCost[j - 1] + 1
    
                    newCost[j] = minOf(costInsert, costDelete, costReplace)
                }
    
                val swap = cost
                cost = newCost
                newCost = swap
            }
    
            return cost[lhsLength - 1]
        }
    }
