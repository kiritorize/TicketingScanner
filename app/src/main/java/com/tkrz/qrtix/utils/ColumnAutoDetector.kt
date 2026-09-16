package com.tkrz.qrtix.utils
    
    data class ColumnMapping(
        val nameColIndex: Int? = null,
        val nameHeaderName: String = "",
        val isNameAutoMatched: Boolean = false,
        
        val emailColIndex: Int? = null,
        val emailHeaderName: String = "",
        val isEmailAutoMatched: Boolean = false,
        
        val categoryColIndex: Int? = null,
        val categoryHeaderName: String = "",
        val isCategoryAutoMatched: Boolean = false,
        
        val quantityColIndex: Int? = null,
        val quantityHeaderName: String = "",
        val isQuantityAutoMatched: Boolean = false
    ) {
        val isAllMatched: Boolean
            get() = nameColIndex != null && emailColIndex != null && 
                    categoryColIndex != null && quantityColIndex != null
    }
    
    object ColumnAutoDetector {
    
        private val nameKeywords = listOf("nama", "name", "nama lengkap", "full name", "nama pembeli")
        private val emailKeywords = listOf("email", "e-mail", "alamat email", "email address")
        private val categoryKeywords = listOf("kategori", "category", "jenis", "tipe", "type", "tiket", "ticket")
        private val quantityKeywords = listOf("jumlah", "qty", "quantity", "banyak", "berapa")
    
        /**
         * Detects the mapping of columns to Name, Email, Category, and Quantity.
         *
         * @param headers The list of column headers from the first row of the sheet
         * @param dataRows The first few data rows (e.g., next 5 rows) for pattern analysis fallback
         * @return A ColumnMapping describing the detected column indices
         */
        fun detectMapping(headers: List<String>, dataRows: List<List<String>>): ColumnMapping {
            var mapping = ColumnMapping()
            val availableIndices = headers.indices.toMutableSet()
    
            // 1. Header Keyword Matching Phase
            
            // Find Email
            mapping = matchKeyword(headers, availableIndices, emailKeywords)?.let { idx ->
                availableIndices.remove(idx)
                mapping.copy(emailColIndex = idx, emailHeaderName = headers[idx], isEmailAutoMatched = true)
            } ?: mapping
    
            // Find Quantity
            mapping = matchKeyword(headers, availableIndices, quantityKeywords)?.let { idx ->
                availableIndices.remove(idx)
                mapping.copy(quantityColIndex = idx, quantityHeaderName = headers[idx], isQuantityAutoMatched = true)
            } ?: mapping
    
            // Find Category
            mapping = matchKeyword(headers, availableIndices, categoryKeywords)?.let { idx ->
                availableIndices.remove(idx)
                mapping.copy(categoryColIndex = idx, categoryHeaderName = headers[idx], isCategoryAutoMatched = true)
            } ?: mapping
    
            // Find Name
            mapping = matchKeyword(headers, availableIndices, nameKeywords)?.let { idx ->
                availableIndices.remove(idx)
                mapping.copy(nameColIndex = idx, nameHeaderName = headers[idx], isNameAutoMatched = true)
            } ?: mapping
    
    
            // 2. Data Pattern Analysis Phase (Fallback for unmatched columns)
            if (dataRows.isNotEmpty() && !mapping.isAllMatched) {
                
                // Fallback for Email: Contains '@' and '.'
                if (mapping.emailColIndex == null) {
                    val emailIdx = findByDataPattern(headers, dataRows, availableIndices) { value ->
                        value.contains("@") && value.contains(".") && !value.contains(" ")
                    }
                    if (emailIdx != null) {
                        availableIndices.remove(emailIdx)
                        mapping = mapping.copy(emailColIndex = emailIdx, emailHeaderName = headers[emailIdx], isEmailAutoMatched = true)
                    }
                }
    
                // Fallback for Quantity: Small integer (e.g., 1-10)
                if (mapping.quantityColIndex == null) {
                    val qtyIdx = findByDataPattern(headers, dataRows, availableIndices) { value ->
                        val intVal = value.trim().toIntOrNull()
                        intVal != null && intVal in 1..20
                    }
                    if (qtyIdx != null) {
                        availableIndices.remove(qtyIdx)
                        mapping = mapping.copy(quantityColIndex = qtyIdx, quantityHeaderName = headers[qtyIdx], isQuantityAutoMatched = true)
                    }
                }
                
                // Fallback for Category: Column with few distinct values across the sample
                if (mapping.categoryColIndex == null && dataRows.size > 2) {
                    var bestCatIdx: Int? = null
                    var minDistinctCount = Int.MAX_VALUE
                    
                    for (idx in availableIndices) {
                        val distinctValues = dataRows.mapNotNull { it.getOrNull(idx) }.filter { it.isNotBlank() }.toSet()
                        if (distinctValues.isNotEmpty() && distinctValues.size <= dataRows.size / 2) {
                            if (distinctValues.size < minDistinctCount) {
                                minDistinctCount = distinctValues.size
                                bestCatIdx = idx
                            }
                        }
                    }
                    
                    if (bestCatIdx != null) {
                        availableIndices.remove(bestCatIdx)
                        mapping = mapping.copy(categoryColIndex = bestCatIdx, categoryHeaderName = headers[bestCatIdx], isCategoryAutoMatched = true)
                    }
                }
                
                // Fallback for Name: Longest average string length among remaining text columns
                if (mapping.nameColIndex == null) {
                    var bestNameIdx: Int? = null
                    var maxAvgLength = 0f
                    
                    for (idx in availableIndices) {
                        val validValues = dataRows.mapNotNull { it.getOrNull(idx) }.filter { it.isNotBlank() }
                        if (validValues.isNotEmpty()) {
                            val avgLen = validValues.sumOf { it.length }.toFloat() / validValues.size
                            if (avgLen > maxAvgLength) {
                                maxAvgLength = avgLen
                                bestNameIdx = idx
                            }
                        }
                    }
                    
                    if (bestNameIdx != null) {
                        availableIndices.remove(bestNameIdx)
                        mapping = mapping.copy(nameColIndex = bestNameIdx, nameHeaderName = headers[bestNameIdx], isNameAutoMatched = true)
                    }
                }
            }
    
            return mapping
        }
    
        private fun matchKeyword(headers: List<String>, availableIndices: Set<Int>, keywords: List<String>): Int? {
            for (idx in availableIndices) {
                val header = headers.getOrNull(idx)?.trim()?.lowercase() ?: continue
                for (keyword in keywords) {
                    // Exact match or partial match if the keyword is a distinct word in the header
                    if (header == keyword || header.contains(keyword)) {
                        return idx
                    }
                }
            }
            return null
        }
        
        private fun findByDataPattern(
            headers: List<String>, 
            dataRows: List<List<String>>, 
            availableIndices: Set<Int>,
            patternPredicate: (String) -> Boolean
        ): Int? {
            for (idx in availableIndices) {
                // Check if all (or at least majority) non-blank values in this column match the pattern
                val values = dataRows.mapNotNull { it.getOrNull(idx) }.filter { it.isNotBlank() }
                if (values.isNotEmpty()) {
                    val matchCount = values.count { patternPredicate(it) }
                    // If more than 80% match the pattern, consider it a match
                    if (matchCount.toFloat() / values.size >= 0.8f) {
                        return idx
                    }
                }
            }
            return null
        }
    }
    
