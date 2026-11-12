package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TeamType(val displayName: String, val description: String) {
    GENERAL("Genel Ekip", "Tüm takım üyelerinin ortak iletişim ve çalışma alanı"),
    MANAGEMENT("Genel Yönetim", "Takım kaptanları, organizasyon, koordinasyon ve strateji"),
    PR("PR Ekibi", "Sosyal medya, sponsorluk, etkinlik ve tanıtım"),
    MECHANIC("Mekanik Ekibi", "Şasi, tasarım, 3D baskı, montaj ve robot mekaniği"),
    SOFTWARE("Yazılım Ekibi", "Otonom sürüş, robot kontrolü, sensörler ve yapay zeka");

    companion object {
        fun fromDisplayName(name: String): TeamType {
            return entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: GENERAL
        }
    }
}

enum class UserRole(val displayName: String) {
    ADMIN("Yönetici"),
    CAPTAIN("Takım Kaptanı"),
    MEMBER("Takım Üyesi")
}

object PredefinedRoles {
    // Yönetim Rolleri
    val MANAGEMENT_ROLES = listOf(
        "Yetkili Öğretmen",
        "Takım Kaptanı",
        "Takım Yardımcısı",
        "Yönetici"
    )

    // Ekip Rolleri
    val TEAM_ROLES = listOf(
        "Yazılım Üyesi",
        "Mekanik Üyesi",
        "PR Üyesi",
        "Genel Yönetim Üyesi"
    )

    // Özel Roller
    val SPECIAL_ROLES = listOf(
        "Elektronik Sorumlusu",
        "Tasarım Sorumlusu",
        "Sosyal Medya Sorumlusu",
        "Proje Sorumlusu"
    )

    fun getDefaultTeamRole(teamName: String): String {
        return when (teamName) {
            TeamType.SOFTWARE.displayName -> "Yazılım Üyesi"
            TeamType.MECHANIC.displayName -> "Mekanik Üyesi"
            TeamType.PR.displayName -> "PR Üyesi"
            TeamType.MANAGEMENT.displayName -> "Genel Yönetim Üyesi"
            else -> "Takım Üyesi"
        }
    }
}

enum class UserStatus(val value: String) {
    ACTIVE("active"),
    APPROVED("approved"), // legacy support
    PENDING("pending"),
    REJECTED("rejected");

    companion object {
        fun isActive(status: String?): Boolean {
            return status.equals("active", ignoreCase = true) || status.equals("approved", ignoreCase = true)
        }

        fun isPending(status: String?): Boolean {
            return status.equals("pending", ignoreCase = true)
        }

        fun isRejected(status: String?): Boolean {
            return status.equals("rejected", ignoreCase = true)
        }
    }
}

object IdeaStatus {
    const val NEW = "Yeni"
    const val ACCEPTED = "Kabul Edildi"
    const val REJECTED = "Reddedildi"

    val ALL = listOf(NEW, ACCEPTED, REJECTED)
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val displayName: String = "",
    val email: String = "",
    val team: String = TeamType.SOFTWARE.displayName,
    val role: String = "Yazılım Üyesi",
    val roles: List<String> = listOf("Yazılım Üyesi"),
    val status: String = UserStatus.PENDING.value,
    val photoUrl: String = "",
    val isSuperAdmin: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun getFullName(): String {
        val trimmedFirst = firstName.trim()
        val trimmedLast = lastName.trim()
        return if (trimmedFirst.isNotEmpty() && trimmedLast.isNotEmpty()) {
            "$trimmedFirst $trimmedLast"
        } else if (displayName.trim().isNotEmpty() && displayName != "Takım Üyesi") {
            displayName.trim()
        } else if (username.trim().isNotEmpty()) {
            username.trim()
        } else {
            email.substringBefore("@").replace(".", " ").split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        }
    }

    fun isApproved(): Boolean = UserStatus.isActive(status)
    fun isPending(): Boolean = UserStatus.isPending(status)
    fun isRejected(): Boolean = UserStatus.isRejected(status)
}

@IgnoreExtraProperties
data class ChatRoom(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val name: String = "",
    val description: String = "",
    val allowedTeam: String = "all",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String = "",
    val senderTeam: String = "",
    val senderRoles: List<String> = emptyList(),
    val text: String = "",
    val fileUrl: String = "",
    val fileName: String = "",
    val fileType: String = "",
    val fileSize: Long = 0L,
    val isPinned: Boolean = false,
    val replyToId: String = "",
    val replyToSender: String = "",
    val replyToText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class IdeaItem(
    @DocumentId val id: String = "",
    val roomId: String = "general",
    val authorId: String = "",
    val authorName: String = "",
    val authorTeam: String = "",
    val authorRoles: List<String> = emptyList(),
    val title: String = "",
    val description: String = "",
    val category: String = IdeaStatus.NEW, // "Yeni", "Kabul Edildi", "Reddedildi"
    val likes: List<String> = emptyList(),
    val likeCount: Long = 0L,
    val fileUrl: String = "",
    val fileName: String = "",
    val fileSize: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class IdeaComment(
    @DocumentId val id: String = "",
    val ideaId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorRoles: List<String> = emptyList(),
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SharedProjectFile(
    @DocumentId val id: String = "",
    val roomId: String = "general",
    val uploadedById: String = "",
    val uploadedByName: String = "",
    val uploadedByTeam: String = "",
    val uploadedByRoles: List<String> = emptyList(),
    val fileName: String = "",
    val fileType: String = "code", // "code", "image", "pdf", "zip", "doc", "cad", "text"
    val fileSize: Long = 0L,
    val description: String = "",
    val content: String = "", // snippet code, text content, or link
    val downloadUrl: String = "",
    val storagePath: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun getFormattedSize(): String {
        if (fileSize <= 0L) return if (content.isNotBlank()) "${content.length} B" else ""
        val kb = fileSize / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(java.util.Locale.US, "%.1f MB", mb)
        } else if (kb >= 1.0) {
            String.format(java.util.Locale.US, "%.0f KB", kb)
        } else {
            "$fileSize B"
        }
    }
}

@IgnoreExtraProperties
data class TeamAnnouncement(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val title: String = "",
    val content: String = "",
    val isPinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TeamNotification(
    @DocumentId val id: String = "",
    val recipientUserId: String = "", // "all", "managers", or specific userId
    val title: String = "",
    val message: String = "",
    val type: String = "", // "member_pending", "member_approved", "message", "idea", "comment", "announcement"
    val targetId: String = "", // e.g. ideaId or userId
    val targetRoomId: String = "", // e.g. "software"
    val isRead: Boolean = false,
    val readBy: List<String> = emptyList(), // User IDs who have read this notification
    val senderName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun isReadBy(userId: String): Boolean {
        if (isRead) return true
        if (userId.isNotBlank() && readBy.contains(userId)) return true
        if (recipientUserId.isNotBlank() && recipientUserId == userId && isRead) return true
        return false
    }
}

data class TeamActivity(
    val id: String = "",
    val userName: String = "",
    val userTeam: String = "",
    val actionText: String = "",
    val targetTitle: String = "",
    val type: String = "idea", // "idea", "file", "announcement", "chat"
    val timeAgo: String = ""
)
