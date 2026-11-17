package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
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

/**
 * In chat message headers, only team affiliation plus Captain ("Kaptan") or Teacher ("Öğretmen")
 * status should be displayed; all other internal/member roles are hidden.
 */
fun filterChatDisplayRoles(roles: List<String>): List<String> {
    if (roles.isEmpty()) return emptyList()
    val result = mutableListOf<String>()
    for (raw in roles) {
        val lower = raw.trim().lowercase(java.util.Locale("tr", "TR"))
        when {
            lower.contains("öğretmen") || lower.contains("ogretmen") -> {
                if ("Öğretmen" !in result) result.add("Öğretmen")
            }
            lower.contains("kaptan") && !lower.contains("yardımcı") && !lower.contains("yardimcisi") -> {
                if ("Kaptan" !in result) result.add("Kaptan")
            }
        }
    }
    return result
}

enum class UserStatus(val value: String) {
    ACTIVE("active"),
    APPROVED("approved"), // legacy support
    PENDING("pending"),
    REJECTED("rejected"),
    REVOKED("revoked");

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

        fun isRevoked(status: String?): Boolean {
            return status.equals("revoked", ignoreCase = true)
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
    fun isRevoked(): Boolean = UserStatus.isRevoked(status)

    fun isManagerAccount(): Boolean {
        return isSuperAdmin ||
            email.equals("filim3290@gmail.com", ignoreCase = true) ||
            role == UserRole.ADMIN.displayName ||
            roles.any {
                it in listOf("Yönetici", "Takım Kaptanı", "Yetkili Öğretmen")
            }
    }
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

const val MESSAGE_EDIT_WINDOW_MS = 60L * 60L * 1000L // 1 saat

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
    val thumbnailUrl: String = "",
    val storagePath: String = "",
    val mediaDurationMs: Long = 0L,
    @get:PropertyName("isPinned") @field:PropertyName("isPinned") val isPinned: Boolean = false,
    @get:PropertyName("isEdited") @field:PropertyName("isEdited") val isEdited: Boolean = false,
    val editedAt: Timestamp? = null,
    val replyToId: String = "",
    val replyToSender: String = "",
    val replyToText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun ChatMessage.canBeEditedBy(userId: String?, nowMs: Long = System.currentTimeMillis()): Boolean {
    if (userId.isNullOrBlank() || senderId != userId) return false
    if (id.isBlank()) return false
    val sentAtMs = createdAt?.toDate()?.time ?: return true
    val elapsed = nowMs - sentAtMs
    return elapsed <= MESSAGE_EDIT_WINDOW_MS && elapsed >= -300_000L
}

fun ChatMessage.canBeDeletedBy(userId: String?, isManager: Boolean): Boolean {
    if (id.isBlank()) return false
    if (isManager) return true
    return !userId.isNullOrBlank() && senderId == userId
}

fun formatMessagesForClipboard(messages: List<ChatMessage>): String {
    if (messages.isEmpty()) return ""
    if (messages.size == 1) {
        val msg = messages.first()
        return msg.text.ifBlank { msg.fileName }
    }
    return messages.joinToString("\n") { msg ->
        val body = msg.text.ifBlank { msg.fileName }
        if (msg.senderName.isNotBlank()) "${msg.senderName}: $body" else body
    }
}

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
    val fileType: String = "",
    val fileSize: Long = 0L,
    val thumbnailUrl: String = "",
    val storagePath: String = "",
    val mediaDurationMs: Long = 0L,
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
    val thumbnailUrl: String = "",
    val mediaDurationMs: Long = 0L,
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
    val type: String = "", // "member_pending", "member_approved", "new_member", "message", "file", "idea", "comment", "announcement", "broadcast", "update"
    val targetId: String = "", // e.g. ideaId or userId
    val targetRoomId: String = "", // e.g. "software", "general"
    val isRead: Boolean = false,
    val readBy: List<String> = emptyList(), // User IDs who have read this notification
    val senderId: String = "",
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

object SupportTicketStatus {
    const val OPEN = "open"
    const val IN_PROGRESS = "in_progress"
    const val RESOLVED = "resolved"

    val ALL = listOf(OPEN, IN_PROGRESS, RESOLVED)

    fun normalize(raw: String?): String {
        val clean = raw?.trim()?.lowercase()
        return if (clean in ALL) clean!! else OPEN
    }

    fun displayName(status: String?): String {
        return when (normalize(status)) {
            IN_PROGRESS -> "İnceleniyor"
            RESOLVED -> "Çözüldü"
            else -> "Açık"
        }
    }
}

object SupportCategories {
    val ALL = listOf(
        "Bildirim Sorunu",
        "Uygulama Hatası",
        "Güncelleme",
        "Hesap & Rol",
        "Öneri / Diğer"
    )
}

@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val userTeam: String = "",
    val category: String = "Uygulama Hatası",
    val subject: String = "",
    val description: String = "",
    val appVersion: String = "",
    val deviceInfo: String = "",
    val status: String = SupportTicketStatus.OPEN,
    val adminReply: String = "",
    val repliedByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class SupportFaqItem(
    val id: String,
    val category: String,
    val question: String,
    val answer: String
)

object SupportFaqData {
    val CATEGORIES = listOf("Tümü", "Bildirimler", "Sohbet", "Güncelleme", "Hesap", "Dosyalar")

    val ITEMS = listOf(
        SupportFaqItem(
            id = "faq_bg_notif",
            category = "Bildirimler",
            question = "Mesaj veya duyuru bildirimleri neden gecikiyor veya gelmiyor?",
            answer = "Uygulama, mesajları ve duyuruları anlık iletmek için sürekli arka plan bağlantı servisini kullanır. Bildirimlerin kesintisiz gelmesi için Profil sekmesindeki 'Anlık Arka Plan Bildirimleri' anahtarının açık olduğundan, Android bildirim izninin verildiğinden ve cihaz pil ayarlarında BLACKSEA ROBOTICS için 'Kısıtlanmamış' pil kullanımının seçili olduğundan emin olun."
        ),
        SupportFaqItem(
            id = "faq_active_room",
            category = "Bildirimler",
            question = "Sohbet odasının içindeyken neden yeni mesaj bildirimi gelmiyor?",
            answer = "Dikkat dağılmasını önlemek için o anda açık olan ve görüntülediğiniz sohbet odasından gelen mesajlar için bildirim sesi/penceresi bastırılır. Diğer odalardan gelen mesajlar, uygulamayı arka plana aldığınızda veya başka bir ekranda olduğunuzda anında bildirim olarak düşer."
        ),
        SupportFaqItem(
            id = "faq_chat_gestures",
            category = "Sohbet",
            question = "Mesaja nasıl yanıt veririm, düzenlerim, sabitlerim veya silerim?",
            answer = "Herhangi bir mesaj balonunu sağa doğru kaydırarak o mesaja hızlıca yanıt verebilirsiniz. Mesajların üzerine basılı tutarak çoklu seçim modunu açabilir; üst çubuktan seçilen mesajları kopyalayabilir, kendi mesajlarınızı (ve yöneticiler için tüm mesajları) topluca silebilir, tekil mesajlarda sağ üstteki (⋮) menüsünden sabitleme ve 1 saat içinde düzenleme yapabilirsiniz."
        ),
        SupportFaqItem(
            id = "faq_updates",
            category = "Güncelleme",
            question = "Yeni sürüm yayınlandığında uygulamayı nasıl güncellerim?",
            answer = "GitHub üzerinde yeni bir sürüm yayınlandığında bildirim alırsınız. Profil > 'Uygulama & Güncellemeler' bölümünden de istediğiniz zaman 'Güncellemeleri Kontrol Et' butonuna basarak en son sürümü indirip kurabilirsiniz. Kurulum sırasında Android'in 'Bilinmeyen uygulamalara izin ver' onayını vermeniz gerekebilir."
        ),
        SupportFaqItem(
            id = "faq_profile_roles",
            category = "Hesap",
            question = "Ekibimi, adımı veya yetki/rolümü nasıl değiştirebilirim?",
            answer = "Ad, soyad, kullanıcı adı ve ekip bilgilerinizi Profil ekranındaki 'Profili ve Ekibi Düzenle' butonundan güncelleyebilirsiniz. Takım Kaptanı, Yetkili Öğretmen veya Yönetici gibi yetki rolleri yalnızca mevcut yöneticiler tarafından Yönetici Paneli üzerinden atanabilir."
        ),
        SupportFaqItem(
            id = "faq_files_media",
            category = "Dosyalar",
            question = "Hangi dosya türlerini yükleyebilirim ve boyut sınırı nedir?",
            answer = "Fotoğraf, video, ses kaydı, PDF, kod dosyaları (.py, .kt, .cpp vb.) ve CAD dosyalarını (.step, .stl vb.) 100 MB sınırına kadar paylaşabilirsiniz. Medya ve kod dosyaları doğrudan uygulama içinde önizlenebilir."
        ),
        SupportFaqItem(
            id = "faq_approval",
            category = "Hesap",
            question = "Yeni kayıt oldum, onay sürecim ne kadar sürer?",
            answer = "Kaydınızı tamamladığınızda yöneticilere ve takım kaptanlarına otomatik onay bildirimi gider. Yönetici onayladığında bildirim alırsınız ve tüm takım alanlarına erişiminiz anında açılır."
        ),
        SupportFaqItem(
            id = "faq_account_delete",
            category = "Hesap",
            question = "Hesabımı silersem ne olur?",
            answer = "Profil sayfasının en altındaki 'Hesabımı Kalıcı Olarak Sil' seçeneği ile iki aşamalı onay vererek profilinizi ve kişisel bildirim kayıtlarınızı kalıcı olarak silebilirsiniz. Bu işlem geri alınamaz."
        )
    )
}

