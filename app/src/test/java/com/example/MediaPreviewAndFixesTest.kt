package com.example

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.data.BlackseaRepository
import com.example.data.FileUploadResult
import com.example.model.TeamNotification
import com.example.model.UserStatus
import com.example.ui.components.formatMediaDuration
import com.example.ui.components.resolveMediaImageModel
import com.example.ui.components.resolvePlayableVideoUri
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaPreviewAndFixesTest {

    @Test
    fun `resolveMediaImageModel decodes base64 data uri to byte array`() {
        val raw = byteArrayOf(1, 2, 3, 4, 5, 42)
        val encoded = Base64.encodeToString(raw, Base64.NO_WRAP)
        val model = resolveMediaImageModel("data:image/jpeg;base64,$encoded")
        assertTrue(model is ByteArray)
        assertArrayEquals(raw, model as ByteArray)
    }

    @Test
    fun `resolveMediaImageModel resolves local file only when it exists`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tmp = File(context.cacheDir, "test_preview_img.jpg").apply {
            writeBytes(byteArrayOf(10, 20, 30))
        }
        try {
            val resolved = resolveMediaImageModel("local:${tmp.absolutePath}")
            assertTrue(resolved is File)
            assertEquals(tmp.absolutePath, (resolved as File).absolutePath)

            assertNull(resolveMediaImageModel("local:${tmp.absolutePath}.missing"))
            assertNull(resolveMediaImageModel("   "))
            assertEquals("https://cdn.example.com/a.jpg", resolveMediaImageModel("https://cdn.example.com/a.jpg"))
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `resolvePlayableVideoUri prioritizes http and falls back to local storagePath`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tmpVideo = File(context.cacheDir, "test_clip.mp4").apply {
            writeBytes(byteArrayOf(0, 0, 0, 24))
        }
        try {
            val httpUri = resolvePlayableVideoUri("https://storage.googleapis.com/demo.mp4", "")
            assertNotNull(httpUri)
            assertEquals("https", httpUri?.scheme)

            // When cloud upload failed, downloadUrl carries data:image thumbnail while storagePath holds local file
            val localFallback = resolvePlayableVideoUri(
                downloadUrl = "data:image/jpeg;base64,/9j/4AAQ",
                storagePath = "local:${tmpVideo.absolutePath}"
            )
            assertNotNull(localFallback)
            assertEquals("file", localFallback?.scheme)
            assertEquals(tmpVideo.absolutePath, localFallback?.path)

            // Without existing local file or remote URL, return null instead of crashing player
            val missingFallback = resolvePlayableVideoUri(
                downloadUrl = "data:image/jpeg;base64,/9j/4AAQ",
                storagePath = "local:${tmpVideo.absolutePath}.gone"
            )
            assertNull(missingFallback)
        } finally {
            tmpVideo.delete()
        }
    }

    @Test
    fun `formatMediaDuration formats seconds minutes and hours`() {
        assertEquals("", formatMediaDuration(0L))
        assertEquals("", formatMediaDuration(-100L))
        assertEquals("00:05", formatMediaDuration(5_000L))
        assertEquals("02:05", formatMediaDuration(125_000L))
        assertEquals("1:01:01", formatMediaDuration(3_661_000L))
    }

    @Test
    fun `detectFileType supports extensions and PickVisualMedia mime fallback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        val repo = BlackseaRepository(FirebaseFirestore.getInstance())

        assertEquals("image", repo.detectFileType("photo.heic"))
        assertEquals("video", repo.detectFileType("robot_run.MOV"))
        assertEquals("code", repo.detectFileType("autonomous.ino"))
        assertEquals("cad", repo.detectFileType("arm.step"))

        // Android PickVisualMedia sometimes returns extensionless numeric names
        assertEquals("image", repo.detectFileType("1000001234", "image/jpeg"))
        assertEquals("video", repo.detectFileType("1000005678", "video/mp4"))
        assertEquals("pdf", repo.detectFileType("document", "application/pdf"))
    }

    @Test
    fun `shared notification per-user readBy works independently across users`() {
        val sharedNotif = TeamNotification(
            id = "n1",
            recipientUserId = "all",
            isRead = false,
            readBy = listOf("user_a")
        )
        assertTrue(sharedNotif.isReadBy("user_a"))
        assertFalse(sharedNotif.isReadBy("user_b"))
    }

    @Test
    fun `UserStatus and FileUploadResult defaults are consistent`() {
        assertTrue(UserStatus.isActive("active"))
        assertTrue(UserStatus.isActive("approved"))
        assertTrue(UserStatus.isPending("pending"))
        assertTrue(UserStatus.isRejected("rejected"))

        val res = FileUploadResult(
            fileName = "clip.mp4",
            fileType = "video",
            fileSize = 2048L,
            downloadUrl = "https://example.com/clip.mp4",
            storagePath = "local:/tmp/clip.mp4",
            thumbnailUrl = "data:image/jpeg;base64,abc",
            mediaDurationMs = 15_000L
        )
        assertEquals(15_000L, res.mediaDurationMs)
        assertEquals("local:/tmp/clip.mp4", res.storagePath)
    }
}
