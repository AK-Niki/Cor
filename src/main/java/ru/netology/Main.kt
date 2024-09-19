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

// Модель Post с автором и комментарием
@Serializable
data class Post(
    val id: Long,
    val authorId: Long,
    val content: String,
    val published: Long,
    val likedByMe: Boolean,
    val likes: Int = 0,
    var attachment: Attachment? = null,
    var author: Author? = null // Добавим поле для информации об авторе
)

// Модель Comment
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

// Модель Attachment
@Serializable
data class Attachment(
    val url: String,
    val description: String,
    val type: AttachmentType,
)

// Типы вложений
@Serializable
enum class AttachmentType {
    IMAGE, VIDEO
}

// Модель Author для сериализации
@Serializable
data class Author(
    val id: Long,
    val name: String,
    val avatar: String
)

// Функция для получения автора по его id с сервера
suspend fun fetchAuthor(client: HttpClient, authorId: Long): Author? {
    return try {
        println("Запрос информации об авторе с id: $authorId")
        client.get("http://localhost:9999/api/authors/$authorId").body()
    } catch (e: Exception) {
        println("Ошибка получения автора с id $authorId: ${e.message}")
        null
    }
}

// Функция для получения постов с сервера
suspend fun fetchPosts(client: HttpClient): List<Post> {
    return try {
        println("Запрос на получение постов")
        // Ожидаем, что сервер вернет JSON-массив
        val response: HttpResponse = client.get("http://localhost:9999/api/posts")
        println("Ответ сервера: ${response.status}")

        // Десериализация массива Post
        response.body<List<Post>>()
    } catch (e: Exception) {
        println("Ошибка получения постов: ${e.message}")
        emptyList()
    }
}

// Функция для загрузки авторов и обогащения постов
suspend fun enrichPostsWithAuthors(client: HttpClient, posts: List<Post>): List<Post> {
    return coroutineScope {
        posts.map { post ->
            async {
                val author = fetchAuthor(client, post.authorId)
                post.copy(author = author) // Копируем пост с добавлением автора
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

    // Получаем список постов с сервера
    val posts = fetchPosts(client)

    // Если список постов не пуст, загружаем авторов для каждого поста
    if (posts.isNotEmpty()) {
        val postsWithAuthors = enrichPostsWithAuthors(client, posts)

        // Выводим посты с информацией об авторах
        postsWithAuthors.forEach { post ->
            println("Пост: ${post.content}, Автор: ${post.author?.name}, Аватар: ${post.author?.avatar}")
        }
    } else {
        println("Список постов пуст.")
    }

    client.close()
}
