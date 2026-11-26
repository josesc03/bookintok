package com.bookintok.api.book

import com.bookintok.api.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.multipart
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BookImageApiTest : IntegrationTest() {

    private var bookId = 0L

    @BeforeEach
    fun setUp() {
        register("owner")
        register("other")
        bookId = createBook("owner")
    }

    private fun png(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private fun upload(uid: String, bytes: ByteArray): ResultActionsDsl =
        mockMvc.multipart(HttpMethod.PUT, "/api/v1/books/$bookId/image") {
            file(MockMultipartFile("file", "photo.png", "image/png", bytes))
            with(jwt().jwt { it.subject(uid) })
        }

    @Test
    fun `owner uploads a photo that anyone can download as a scaled-down JPEG`() {
        val response = upload("owner", png(3000, 1500))
            .andExpect {
                status { isOk() }
                jsonPath("$.imageUrl") { exists() }
            }
            .andReturn().response.contentAsString
        val imageUrl = Regex("\"imageUrl\"\\s*:\\s*\"([^\"]+)\"").find(response)!!.groupValues[1]

        // Public: no token needed
        val bytes = getAs(null, imageUrl)
            .andExpect {
                status { isOk() }
                header { string("Cache-Control", org.hamcrest.Matchers.containsString("max-age")) }
            }
            .andReturn().response.contentAsByteArray

        val stored = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(1280, stored.width)
        assertEquals(640, stored.height)
        // JPEG magic number: the PNG was re-encoded, so no original metadata survives
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
    }

    @Test
    fun `a new upload replaces the photo URL`() {
        val first = upload("owner", png(10, 10)).andReturn().response.contentAsString
        val second = upload("owner", png(10, 10)).andReturn().response.contentAsString
        assertFalse(first == second)
    }

    @Test
    fun `only the owner can change the photo`() {
        upload("other", png(10, 10)).andExpect { status { isForbidden() } }
        deleteAs("other", "/api/v1/books/$bookId/image").andExpect { status { isForbidden() } }
    }

    @Test
    fun `files that are not images are rejected`() {
        upload("owner", "not an image".toByteArray()).andExpect { status { isUnprocessableContent() } }
    }

    @Test
    fun `deleting the photo removes it`() {
        upload("owner", png(10, 10))
        deleteAs("owner", "/api/v1/books/$bookId/image").andExpect { status { isNoContent() } }
        getAs("owner", "/api/v1/books/$bookId").andExpect { jsonPath("$.imageUrl") { doesNotExist() } }
        getAs(null, "/api/v1/books/$bookId/image").andExpect { status { isNotFound() } }
    }

}
