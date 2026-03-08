package com.example.mcp

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class McpControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    @Test
    fun `market quote returns normalized payload`() {
        mockMvc.perform(
            post("/mcp/tools/market.quote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"symbol":"AAPL"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.quote.symbol").value("AAPL"))
            .andExpect(jsonPath("$.quote.provider").exists())
    }
}
