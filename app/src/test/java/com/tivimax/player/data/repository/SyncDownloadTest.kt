package com.tivimax.player.data.repository

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class SyncDownloadTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun fakeResponse(body: ResponseBody): Response =
        Response.Builder()
            .request(Request.Builder().url("http://example.com/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()

    @Test
    fun `downloadBodyToFile writes full body content to file`() {
        runBlocking {
            val json = "[1,2,3]"
            val responseBody = json.toResponseBody("application/json".toMediaType())
            val response = fakeResponse(responseBody)
            val dir = tempFolder.newFolder("sync_test")

            var lastBytes = 0L
            val file = downloadBodyToFile(response, dir, "test") { bytes ->
                lastBytes = bytes
            }

            assertTrue(file.exists())
            assertEquals(json, file.readText(Charsets.UTF_8))
            assertEquals(json.length.toLong(), lastBytes)

            file.delete()
        }
    }

    @Test
    fun `downloadBodyToFile throws IOException and deletes temp file on incomplete download`() {
        runBlocking {
            val incompleteBody = object : ResponseBody() {
                override fun contentType() = "application/json".toMediaType()
                override fun contentLength() = 100L
                override fun source(): BufferedSource = Buffer().writeUtf8("[1,2,3]")
            }
            val response = fakeResponse(incompleteBody)
            val dir = tempFolder.newFolder("sync_test_incomplete")

            try {
                downloadBodyToFile(response, dir, "test_incomplete")
                fail("Eksik indirmede IOException fırlatılmalıydı")
            } catch (e: IOException) {
                assertTrue(e.message?.contains("Eksik indirme") == true)
            }

            val filesInDir = dir.listFiles() ?: emptyArray()
            assertEquals(0, filesInDir.size)
        }
    }

    @Test
    fun `formatMegabytes formats bytes as Turkish locale megabytes string`() {
        val result = formatMegabytes(13_002_342)
        assertEquals("12,4 MB", result)
    }

    @Test
    fun `clearSyncTempDir deletes all files inside specified directory`() {
        val dir = tempFolder.newFolder("sync_test_clear")
        val file1 = File(dir, "temp1.json").apply { writeText("data1") }
        val file2 = File(dir, "temp2.json").apply { writeText("data2") }

        assertTrue(file1.exists())
        assertTrue(file2.exists())

        clearSyncTempDir(dir)

        val remainingFiles = dir.listFiles() ?: emptyArray()
        assertEquals(0, remainingFiles.size)
    }
}
