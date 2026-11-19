package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

const val PLATFORM_MAIN_TEAM_ID = "blacksea_robotics"
const val PLATFORM_FOUNDER_EMAIL = "filim3290@gmail.com"
const val BLACKSEA_AUTHORIZED_TEACHER_EMAIL = "mehmetilkerbas@gmail.com"
const val BLACKSEA_AUTHORIZED_TEACHER_NAME = "Mehmet İlker Baş"

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
    val ASSIGNABLE_MANAGEMENT_ROLES = listOf(
        "Yetkili Öğretmen",
        "Takım Kaptanı",
        "Takım Yardımcısı"
    )

    val MANAGEMENT_ROLES = listOf(
        "Yetkili Öğretmen",
        "Takım Kaptanı",
        "Takım Yardımcısı",
        "Yönetici"
    )

    val TEAM_ROLES = listOf(
        "Yazılım Üyesi",
        "Mekanik Üyesi",
        "PR Üyesi",
        "Genel Yönetim Üyesi"
    )

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

object TeamStatus {
    const val PENDING = "pending"
    const val APPROVED = "approved"
    const val SUSPENDED = "suspended"
    const val UNDER_REVIEW = "under_review"
    const val REJECTED = "rejected"

    val ALL = listOf(PENDING, APPROVED, SUSPENDED, UNDER_REVIEW, REJECTED)

    fun isApproved(status: String?): Boolean = status.equals(APPROVED, ignoreCase = true)
    fun isPending(status: String?): Boolean = status.equals(PENDING, ignoreCase = true)
    fun isSuspended(status: String?): Boolean = status.equals(SUSPENDED, ignoreCase = true)
    fun isUnderReview(status: String?): Boolean = status.equals(UNDER_REVIEW, ignoreCase = true)
    fun isRejected(status: String?): Boolean = status.equals(REJECTED, ignoreCase = true)
    fun isWorkspaceBlocked(status: String?): Boolean = isSuspended(status) || isUnderReview(status)

    fun displayName(status: String?): String = when {
        isApproved(status) -> "Onaylandı"
        isSuspended(status) -> "Durduruldu"
        isUnderReview(status) -> "İncelemede"
        isRejected(status) -> "Reddedildi"
        else -> "Onay Bekliyor"
    }
}

object TeamAppealStatus {
    const val NONE = ""
    const val PENDING = "pending"
    const val REVIEWED = "reviewed"
    const val REJECTED = "rejected"

    fun displayName(status: String?): String = when {
        status.equals(PENDING, ignoreCase = true) -> "İtiraz İnceleniyor"
        status.equals(REVIEWED, ignoreCase = true) -> "İtiraz Değerlendirildi"
        status.equals(REJECTED, ignoreCase = true) -> "İtiraz Reddedildi"
        else -> ""
    }
}

enum class TeamLogoPreset(val key: String, val label: String) {
    BSR_PHOENIX("bsr_phoenix", "BlackSea Phoenix"),
    ROCKET("rocket", "Roket / TEKNOFEST"),
    DRONE("drone", "İHA / Drone"),
    AI_BRAIN("ai_brain", "Yapay Zeka"),
    GEAR("gear", "Mekanik / Robot"),
    BOLT("bolt", "Elektronik / Enerji"),
    CIRCUIT("circuit", "Yazılım / Devre"),
    SHIELD("shield", "Okul Takımı");

    companion object {
        fun fromKey(raw: String?): TeamLogoPreset {
            return entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: SHIELD
        }
    }
}

val TEAM_ACCENT_COLORS = listOf(
    "#0284C7", // Electric Blue
    "#7C3AED", // Royal Purple
    "#059669", // Emerald Green
    "#EA580C", // Vivid Orange
    "#DC2626", // Crimson Red
    "#0D9488", // Cyber Teal
    "#4F46E5", // Deep Indigo
    "#D97706"  // Amber Gold
)

val COMPETITION_PRESETS = listOf(
    "TEKNOFEST",
    "FRC / Robotik",
    "Yazılım & Yapay Zeka",
    "İHA / Drone",
    "Roket & Uzay",
    "Elektrikli Araç / Otonom",
    "Proje & Mühendislik"
)

@Keep
@IgnoreExtraProperties
data class TeamInfo(
    @DocumentId val id: String = "",
    val name: String = "",
    val shortCode: String = "",
    val competition: String = "",
    val schoolName: String = "",
    val city: String = "",
    val description: String = "",
    val motto: String = "",
    val logoKey: String = TeamLogoPreset.SHIELD.key,
    val logoUrl: String = "",
    val accentColorHex: String = "#0284C7",
    val managerName: String = "",
    val managerContact: String = "",
    val founderId: String = "",
    val founderEmail: String = "",
    val status: String = TeamStatus.PENDING,
    @get:PropertyName("isMainTeam") @field:PropertyName("isMainTeam")
    val isMainTeam: Boolean = false,
    val memberCount: Long = 1L,
    val rejectionReason: String = "",
    val moderationReason: String = "",
    val reviewedBy: String = "",
    val appealText: String = "",
    val appealStatus: String = "",
    val appealByUserId: String = "",
    val appealByName: String = "",
    val appealSubmittedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isApproved(): Boolean = TeamStatus.isApproved(status)
    fun isPending(): Boolean = TeamStatus.isPending(status)
    fun isSuspended(): Boolean = TeamStatus.isSuspended(status)
    fun isUnderReview(): Boolean = TeamStatus.isUnderReview(status)
    fun isRejected(): Boolean = TeamStatus.isRejected(status)
    fun isWorkspaceBlocked(): Boolean = TeamStatus.isWorkspaceBlocked(status)

    companion object {
        fun mainBlackseaTeam(): TeamInfo = TeamInfo(
            id = PLATFORM_MAIN_TEAM_ID,
            name = "BlackSea Robotics",
            shortCode = "BSR",
            competition = "FRC & TEKNOFEST Robotik",
            schoolName = "Ünye Fen Lisesi",
            city = "Ordu / Ünye",
            description = "Ünye Fen Lisesi Robotik Takımı — Platform Ana Ekibi",
            motto = "ONE TEAM. ONE VISION. ONE ROBOT.",
            logoKey = TeamLogoPreset.BSR_PHOENIX.key,
            accentColorHex = "#0284C7",
            managerName = BLACKSEA_AUTHORIZED_TEACHER_NAME,
            managerContact = BLACKSEA_AUTHORIZED_TEACHER_EMAIL,
            founderId = PLATFORM_MAIN_TEAM_ID,
            founderEmail = PLATFORM_FOUNDER_EMAIL,
            status = TeamStatus.APPROVED,
            isMainTeam = true,
            memberCount = 1L
        )
    }
}

@Keep
@IgnoreExtraProperties
data class TeamReopenWatch(
    @DocumentId val id: String = "",
    val teamId: String = "",
    val userId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TeamMembership(
    @DocumentId val id: String = "",
    val teamId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val userPhotoUrl: String = "",
    val subTeam: String = TeamType.GENERAL.displayName,
    val role: String = "Takım Üyesi",
    val roles: List<String> = listOf("Takım Üyesi"),
    val status: String = UserStatus.PENDING.value,
    @get:PropertyName("isTeamAdmin") @field:PropertyName("isTeamAdmin")
    val isTeamAdmin: Boolean = false,
    val joinNote: String = "",
    val suspensionReason: String = "",
    val appealText: String = "",
    val appealStatus: String = "",
    val appealSubmittedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isActive(): Boolean = UserStatus.isActive(status)
    fun isPending(): Boolean = UserStatus.isPending(status)
    fun isRejected(): Boolean = UserStatus.isRejected(status)
    fun isRevoked(): Boolean = UserStatus.isRevoked(status)
    fun hasPendingAppeal(): Boolean = appealStatus.equals(TeamAppealStatus.PENDING, ignoreCase = true)

    fun isManagerInTeam(): Boolean {
        if (!isActive()) return false
        return isTeamAdmin ||
            role in listOf("Yönetici", "Takım Kaptanı", "Yetkili Öğretmen") ||
            roles.any { it in listOf("Yönetici", "Takım Kaptanı", "Yetkili Öğretmen") }
    }
}

object TeamTaskStatus {
    const val TODO = "todo"
    const val IN_PROGRESS = "in_progress"
    const val DONE = "done"

    val ALL = listOf(TODO, IN_PROGRESS, DONE)

    fun normalize(raw: String?): String {
        val clean = raw?.trim()?.lowercase()
        return if (clean in ALL) clean!! else TODO
    }

    fun displayName(raw: String?): String = when (normalize(raw)) {
        IN_PROGRESS -> "Devam Ediyor"
        DONE -> "Tamamlandı"
        else -> "Yapılacak"
    }
}

@Keep
@IgnoreExtraProperties
data class TeamTask(
    @DocumentId val id: String = "",
    val teamId: String = PLATFORM_MAIN_TEAM_ID,
    val title: String = "",
    val description: String = "",
    val subTeam: String = TeamType.GENERAL.displayName,
    val assignedToId: String = "",
    val assignedToName: String = "",
    val createdById: String = "",
    val createdByName: String = "",
    val priority: String = "Orta", // "Düşük", "Orta", "Yüksek"
    val status: String = TeamTaskStatus.TODO,
    val dueDateText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

object IdeaStatus {
    const val NEW = "Yeni"
    const val ACCEPTED = "Kabul Edildi"
    const val REJECTED = "Reddedildi"

    val ALL = listOf(NEW, ACCEPTED, REJECTED)
}

@Keep
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
    val status: String = UserStatus.ACTIVE.value,
    val photoUrl: String = "",
    @get:PropertyName("isSuperAdmin") @field:PropertyName("isSuperAdmin")
    val isSuperAdmin: Boolean = false,
    @get:PropertyName("isPlatformAdmin") @field:PropertyName("isPlatformAdmin")
    val isPlatformAdmin: Boolean = false,
    val activeTeamId: String = "",
    @get:Exclude @field:Exclude
    val isTeamContextApplied: Boolean = false,
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

    fun isPlatformManagerAccount(): Boolean {
        return email.trim().equals(PLATFORM_FOUNDER_EMAIL, ignoreCase = true)
    }

    fun isBlackseaAuthorizedTeacherAccount(): Boolean {
        return email.trim().equals(BLACKSEA_AUTHORIZED_TEACHER_EMAIL, ignoreCase = true)
    }

    fun isManagerAccount(): Boolean {
        return isPlatformManagerAccount() ||
            isBlackseaAuthorizedTeacherAccount() ||
            isSuperAdmin ||
            role in PredefinedRoles.MANAGEMENT_ROLES ||
            roles.any { it in PredefinedRoles.MANAGEMENT_ROLES }
    }
}

/**
 * Helper resolving a user's display profile inside a specific team context cleanly.
 */
fun UserProfile.withTeamContext(
    teamId: String,
    membership: TeamMembership?
): UserProfile {
    if (membership == null) return this
    val isFounder = isPlatformManagerAccount()
    return copy(
        team = membership.subTeam.ifBlank { team },
        role = membership.role.ifBlank { role },
        roles = membership.roles.ifEmpty { roles },
        status = membership.status.ifBlank { status },
        isSuperAdmin = membership.isTeamAdmin || (teamId == PLATFORM_MAIN_TEAM_ID && isFounder),
        isPlatformAdmin = isFounder,
        isTeamContextApplied = true,
        activeTeamId = teamId
    )
}

@Keep
@IgnoreExtraProperties
data class ChatRoom(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val teamId: String = PLATFORM_MAIN_TEAM_ID,
    val name: String = "",
    val description: String = "",
    val allowedTeam: String = "all",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

const val MESSAGE_EDIT_WINDOW_MS = 60L * 60L * 1000L // 1 saat

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val teamId: String = PLATFORM_MAIN_TEAM_ID,
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

@Keep
@IgnoreExtraProperties
data class IdeaItem(
    @DocumentId val id: String = "",
    val teamId: String = PLATFORM_MAIN_TEAM_ID,
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

@Keep
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

@Keep
@IgnoreExtraProperties
data class SharedProjectFile(
    @DocumentId val id: String = "",
    val teamId: String = PLATFORM_MAIN_TEAM_ID,
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

@Keep
@IgnoreExtraProperties
data class TeamAnnouncement(
    @DocumentId val id: String = "",
    val teamId: String = PLATFORM_MAIN_TEAM_ID,
    val authorId: String = "",
    val authorName: String = "",
    val title: String = "",
    val content: String = "",
    @get:PropertyName("isPinned") @field:PropertyName("isPinned") val isPinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TeamNotification(
    @DocumentId val id: String = "",
    val teamId: String = "",
    val teamName: String = "",
    val recipientUserId: String = "", // "all", "managers", "platform_admins", or specific userId
    val title: String = "",
    val message: String = "",
    val type: String = "", // "member_pending", "member_approved", "new_member", "message", "file", "idea", "comment", "announcement", "broadcast", "update", "team_application_pending", "team_application_approved", "team_application_rejected"
    val targetId: String = "",
    val targetRoomId: String = "",
    @get:PropertyName("isRead") @field:PropertyName("isRead") val isRead: Boolean = false,
    val readBy: List<String> = emptyList(),
    val senderId: String = "",
    val senderName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun effectiveTeamId(): String = teamId.ifBlank { PLATFORM_MAIN_TEAM_ID }

    fun isReadBy(userId: String): Boolean {
        if (isRead) return true
        if (userId.isNotBlank() && readBy.contains(userId)) return true
        return false
    }
}

data class TeamActivity(
    val id: String = "",
    val userName: String = "",
    val userTeam: String = "",
    val actionText: String = "",
    val targetTitle: String = "",
    val type: String = "idea", // "idea", "file", "announcement", "chat", "task"
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

@Keep
@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId val id: String = "",
    val teamId: String = PLATFORM_MAIN_TEAM_ID,
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
) {
    fun originTeamName(): String {
        val raw = userTeam.trim()
        return if (raw.contains("•")) {
            raw.substringBefore("•").trim().ifBlank { "BlackSea Robotics" }
        } else {
            "BlackSea Robotics"
        }
    }

    fun originSubTeamName(): String {
        val raw = userTeam.trim()
        return if (raw.contains("•")) {
            raw.substringAfter("•").trim().ifBlank { "Genel Ekip" }
        } else {
            raw.ifBlank { "Genel Ekip" }
        }
    }
}

data class SupportFaqItem(
    val id: String,
    val category: String,
    val question: String,
    val answer: String
)

object SupportFaqData {
    val CATEGORIES = listOf("Tümü", "Takımlar", "Bildirimler", "Sohbet", "Güncelleme", "Hesap", "Dosyalar")

    val ITEMS = listOf(
        SupportFaqItem(
            id = "faq_multi_team",
            category = "Takımlar",
            question = "Birden fazla takıma nasıl katılabilirim veya takım değiştirebilirim?",
            answer = "Aynı Google hesabınızla platformdaki onaylı tüm takımları 'Takımlar' merkezinde görebilir, istediğiniz takıma katılma başvurusu gönderebilirsiniz. Üyesi olduğunuz takımlar arasında Profil veya üst çubuktaki 'Takımlar' butonuna dokunarak tek tıkla geçiş yapabilirsiniz."
        ),
        SupportFaqItem(
            id = "faq_create_team",
            category = "Takımlar",
            question = "Kendi robotik/TEKNOFEST ekibimi nasıl oluşturabilirim?",
            answer = "Giriş ekranındaki veya Takımlar merkezindeki 'Kendi Ekibini Oluştur' seçeneğini kullanarak takım adı, okul, şehir ve yarışma bilgilerinizi içeren başvuruyu gönderin. BlackSea Robotics yöneticileri başvuruyu onayladığında ekibinizin bağımsız çalışma alanı otomatik olarak aktif edilir."
        ),
        SupportFaqItem(
            id = "faq_bg_notif",
            category = "Bildirimler",
            question = "Mesaj veya duyuru bildirimleri neden gecikiyor veya gelmiyor?",
            answer = "Uygulamada anlık bildirim ve arka plan senkronizasyonu her kullanıcıda otomatik olarak aktiftir. Bildirimlerin kesintisiz gelmesi için Android bildirim izninin açık olduğundan ve cihaz pil ayarlarında uygulama için 'Kısıtlanmamış' pil kullanımının seçili olduğundan emin olun."
        ),
        SupportFaqItem(
            id = "faq_active_room",
            category = "Bildirimler",
            question = "Sohbet odasının içindeyken neden yeni mesaj bildirimi gelmiyor?",
            answer = "Dikkat dağılmasını önlemek için o anda açık olan ve görüntülediğiniz sohbet odasından gelen mesajlar için bildirim sesi/penceresi bastırılır. Diğer odalardan veya diğer üyesi olduğunuz takımlardan gelen mesajlar anında bildirim olarak düşer."
        ),
        SupportFaqItem(
            id = "faq_chat_gestures",
            category = "Sohbet",
            question = "Mesaja nasıl yanıt veririm, düzenlerim, sabitlerim veya silerim?",
            answer = "Herhangi bir mesaj balonunu sağa doğru kaydırarak o mesaja hızlıca yanıt verebilirsiniz. Mesajların üzerine basılı tutarak çoklu seçim modunu açabilir; üst çubuktan seçilen mesajları kopyalayabilir, kendi mesajlarınızı (ve takım yöneticileri için tüm mesajları) topluca silebilir, tekil mesajlarda sağ üstteki (⋮) menüsünden sabitleme ve 1 saat içinde düzenleme yapabilirsiniz."
        ),
        SupportFaqItem(
            id = "faq_updates",
            category = "Güncelleme",
            question = "Yeni sürüm yayınlandığında uygulamayı nasıl güncellerim?",
            answer = "GitHub üzerinde yeni bir sürüm yayınlandığında bildirim alırsınız. Profil > 'Uygulama & Güncellemeler' bölümünden de istediğiniz zaman 'Güncellemeleri Kontrol Et' butonuna basarak en son sürümü indirip kurabilirsiniz."
        ),
        SupportFaqItem(
            id = "faq_profile_roles",
            category = "Hesap",
            question = "Ekibimi, adımı veya yetki/rolümü nasıl değiştirebilirim?",
            answer = "Ad, soyad, kullanıcı adı ve aktif takım içi alt ekip bilgilerinizi Profil ekranındaki 'Profili Düzenle' butonundan güncelleyebilirsiniz. Takım Kaptanı, Yetkili Öğretmen veya Yönetici gibi yetki rolleri yalnızca bulunduğunuz takımın yöneticileri tarafından Yönetici Paneli üzerinden atanabilir."
        ),
        SupportFaqItem(
            id = "faq_files_media",
            category = "Dosyalar",
            question = "Hangi dosya türlerini yükleyebilirim ve boyut sınırı nedir?",
            answer = "Fotoğraf, video, ses kaydı, PDF, kod dosyaları (.py, .kt, .cpp vb.) ve CAD dosyalarını (.step, .stl vb.) 100 MB sınırına kadar paylaşabilirsiniz. Medya ve kod dosyaları doğrudan bulunduğunuz takımın izole alanında önizlenebilir."
        )
    )
}
