package ru.netology

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Post(
    val id: Long,
    val authorId: Long,
    val content: String,
    val published: Long,
    val likedByMe: Boolean,
    val likes: Int = 0,
    var attachment: Attachment? = null,
    var author: Author? = null
)

@Serializable
data class Comment(
    val id: Long,
    val postId: Long,
    val authorId: Long,
    val content: String,
    val published: Long,
    val likedByMe: Boolean,
    val likes: Int = 0,
)

@Serializable
data class Attachment(
    val url: String,
    val description: String,
    val type: AttachmentType,
)

@Serializable
enum class AttachmentType {
    IMAGE, VIDEO
}

@Serializable
data class Author(
    val id: Long,
    val name: String,
    val avatar: String
)

suspend fun fetchAuthor(client: HttpClient, authorId: Long): Author? {
    return try {
        println("Запрос информации об авторе с id: $authorId")
        client.get("http://localhost:9999/api/authors/$authorId").body()
    } catch (e: Exception) {
        println("Ошибка получения автора с id $authorId: ${e.message}")
        null
    }
}

suspend fun fetchPosts(client: HttpClient): List<Post> {
    return try {
        println("Запрос на получение постов")
        val response: HttpResponse = client.get("http://localhost:9999/api/posts")
        println("Ответ сервера: ${response.status}")

        response.body<List<Post>>()
    } catch (e: Exception) {
        println("Ошибка получения постов: ${e.message}")
        emptyList()
    }
}

suspend fun enrichPostsWithAuthors(client: HttpClient, posts: List<Post>): List<Post> {
    return coroutineScope {
        posts.map { post ->
            async {
                val author = fetchAuthor(client, post.authorId)
                post.copy(author = author)
            }
        }.awaitAll()
    }
}

fun main() = runBlocking {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    val posts = fetchPosts(client)

    if (posts.isNotEmpty()) {
        val postsWithAuthors = enrichPostsWithAuthors(client, posts)
        postsWithAuthors.forEach { post ->
            println("Пост: ${post.content}, Автор: ${post.author?.name}, Аватар: ${post.author?.avatar}")
        }
    } else {
        println("Список постов пуст.")
    }
    client.close()
}
