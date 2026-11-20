package com.example.jarvis.memory

import com.example.jarvis.database.MemoryDao
import com.example.jarvis.database.MemoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class MemoryRepository(private val memoryDao: MemoryDao) {

    val allMemories: Flow<List<MemoryEntity>> = memoryDao.getAllMemories()

    suspend fun saveMemory(
        key: String,
        content: String,
        category: String = MemoryEntity.CATEGORY_FACT
    ): Long {
        val cleanKey = key.trim().ifEmpty { "memory_${System.currentTimeMillis()}" }
        val cleanContent = content.trim()
        val existing = memoryDao.findByKey(cleanKey)
        val now = System.currentTimeMillis()
        return if (existing != null) {
            memoryDao.updateMemory(
                existing.copy(
                    content = cleanContent,
                    category = category,
                    updatedAt = now
                )
            )
            existing.id
        } else {
            memoryDao.insertMemory(
                MemoryEntity(
                    key = cleanKey,
                    content = cleanContent,
                    category = category,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun updateMemory(memory: MemoryEntity) {
        memoryDao.updateMemory(memory.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteMemory(id)
    }

    suspend fun clearAll() {
        memoryDao.clearAllMemories()
    }

    suspend fun retrieveRelevantMemories(query: String): List<MemoryEntity> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            memoryDao.getAllMemoriesOnce()
        } else {
            val matched = memoryDao.searchMemories(trimmed)
            if (matched.isNotEmpty()) matched else memoryDao.getAllMemoriesOnce().take(12)
        }
    }

    suspend fun getAllOnce(): List<MemoryEntity> = memoryDao.getAllMemoriesOnce()

    suspend fun exportMemoriesJson(): String {
        val items = memoryDao.getAllMemoriesOnce()
        val array: JsonArray = buildJsonArray {
            items.forEach { item ->
                add(
                    buildJsonObject {
                        put("key", item.key)
                        put("content", item.content)
                        put("category", item.category)
                        put("createdAt", item.createdAt)
                        put("updatedAt", item.updatedAt)
                    }
                )
            }
        }
        return Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), array)
    }

    suspend fun importMemoriesJson(rawJson: String): Result<Int> {
        return try {
            val array = Json.parseToJsonElement(rawJson).jsonArray
            var imported = 0
            for (element in array) {
                val obj: JsonObject = element.jsonObject
                val key = obj["key"]?.jsonPrimitive?.content?.trim().orEmpty()
                val content = obj["content"]?.jsonPrimitive?.content?.trim().orEmpty()
                val category = obj["category"]?.jsonPrimitive?.content?.trim()
                    ?: MemoryEntity.CATEGORY_FACT
                if (content.isNotBlank()) {
                    saveMemory(
                        key = key.ifBlank { "imported_${System.currentTimeMillis()}_$imported" },
                        content = content,
                        category = category
                    )
                    imported++
                }
            }
            Result.success(imported)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Invalid memory JSON format: ${e.localizedMessage}"))
        }
    }
}
