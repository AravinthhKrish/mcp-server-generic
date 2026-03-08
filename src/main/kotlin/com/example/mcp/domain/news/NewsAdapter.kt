package com.example.mcp.domain.news

import com.example.mcp.domain.Article
import com.example.mcp.mcp.NewsSearchArticlesInput
import org.springframework.stereotype.Component
import java.time.Instant

interface NewsAdapter {
    fun searchArticles(input: NewsSearchArticlesInput): List<Article>
}

@Component
class StubNewsAdapter : NewsAdapter {
    override fun searchArticles(input: NewsSearchArticlesInput): List<Article> {
        return listOf(
            Article(
                id = "art_001",
                source = "reuters",
                title = "Markets open higher on earnings momentum",
                url = "https://example.com/news/markets-open-higher",
                publishedAt = Instant.now(),
                author = "News Desk",
                summary = "Global indices moved higher on better-than-expected earnings.",
                tags = listOf("markets", "earnings")
            )
        )
    }
}
