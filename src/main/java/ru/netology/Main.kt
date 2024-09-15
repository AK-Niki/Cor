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

data class Post(
    val id: Long,
    val authorId: Long,
    val content: String,
    val published: Long,
    val likedByMe: Boolean,
    val likes: Int = 0,
    var attachment: Attachment? = null,
    var author: Author? = null,
    var comments: List<CommentWithAuthor>? = null
)

data class Comment(
    val id: Long,
    val postId: Long,
    val authorId: Long,
    val content: String,
    val published: Long,
    val likedByMe: Boolean,
    val likes: Int = 0,
)

data class Attachment(
    val url: String,
    val description: String,
    val type: AttachmentType,
)

enum class AttachmentType {
    IMAGE, VIDEO
}

@Serializable
data class Author(
    val id: Long,
    val name: String,
    val avatar: String,
)

data class CommentWithAuthor(
    val comment: Comment,
    var author: Author?
)

suspend fun fetchAuthor(client: HttpClient, authorId: Long): Author? {
    return try {
        // Используем полный URL с localhost и портом 9999
        client.get("http://10.0.2.2:9999/api/authors/$authorId").body()
    } catch (e: Exception) {
        println("Error fetching author with id $authorId: ${e.message}")
        null
    }
}

suspend fun fetchAuthorsForPosts(client: HttpClient, posts: List<Post>): List<Post> {
    return coroutineScope {
        posts.map { post ->
            async {
                val author = fetchAuthor(client, post.authorId)
                post.copy(author = author)
            }
        }.awaitAll()
    }
}

suspend fun fetchAuthorsForComments(client: HttpClient, comments: List<Comment>): List<CommentWithAuthor> {
    return coroutineScope {
        comments.map { comment ->
            async {
                val author = fetchAuthor(client, comment.authorId)
                CommentWithAuthor(comment, author)
            }
        }.awaitAll()
    }
}

suspend fun enrichPostsWithAuthorsAndComments(client: HttpClient, posts: List<Post>, commentsByPost: Map<Long, List<Comment>>): List<Post> {
    val postsWithAuthors = fetchAuthorsForPosts(client, posts)

    return postsWithAuthors.map { post ->
        val comments = commentsByPost[post.id] ?: emptyList()
        val commentsWithAuthors = fetchAuthorsForComments(client, comments)
        post.copy(comments = commentsWithAuthors)
    }
}

fun main() = runBlocking {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    // Пример данных
    val posts = listOf(
        Post(1, 101, "1 Post", 1622543200, false),
        Post(2, 102, "2 Post", 1622546800, true)
    )

    val comments = listOf(
        Comment(1, 1, 201, "1Comment", 1622550400, false),
        Comment(2, 1, 202, "2Comment", 1622554000, true)
    )

    val commentsByPost = comments.groupBy { it.postId }

    // Обогащаем посты авторами и комментариями с авторами
    val postsWithAuthorsAndComments = enrichPostsWithAuthorsAndComments(client, posts, commentsByPost)

    // Выводим результат
    postsWithAuthorsAndComments.forEach { post ->
        println("Post: ${post.content}, Author: ${post.author?.name}, Comments: ${post.comments?.size}")
        post.comments?.forEach { commentWithAuthor ->
            println("  Comment: ${commentWithAuthor.comment.content}, Author: ${commentWithAuthor.author?.name}")
        }
    }

    client.close()
}

