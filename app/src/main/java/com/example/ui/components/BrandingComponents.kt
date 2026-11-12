package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.TeamType
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ManagementColor
import com.example.ui.theme.MechanicColor
import com.example.ui.theme.PRColor
import com.example.ui.theme.PhoenixAmber
import com.example.ui.theme.SoftwareColor

@Composable
fun BlackseaLogo(
    modifier: Modifier = Modifier,
    size: Int = 56
) {
    Image(
        painter = painterResource(id = R.drawable.ic_blacksea_logo),
        contentDescription = "BLACKSEA ROBOTICS Logo",
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
    )
}

@Composable
fun BlackseaTeamBanner(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("team_hero_banner"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF1E293B),
                            Color(0xFF1E3A8A)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BlackseaLogo(size = 64)

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "BLACKSEA ROBOTICS",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ElectricBlue.copy(alpha = 0.3f)
                        ) {
                            Text(
                                text = "BSR",
                                color = ElectricBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "Ünye Fen Lisesi Robotik Takımı",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "ONE TEAM. ONE VISION. ONE ROBOT.",
                        color = PhoenixAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TeamBadge(
    teamName: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, icon) = when (teamName) {
        TeamType.MANAGEMENT.displayName -> Triple(ManagementColor.copy(alpha = 0.15f), ManagementColor, Icons.Default.Security)
        TeamType.PR.displayName -> Triple(PRColor.copy(alpha = 0.15f), PRColor, Icons.Default.Handshake)
        TeamType.MECHANIC.displayName -> Triple(MechanicColor.copy(alpha = 0.15f), MechanicColor, Icons.Default.Engineering)
        TeamType.SOFTWARE.displayName -> Triple(SoftwareColor.copy(alpha = 0.15f), SoftwareColor, Icons.Default.Memory)
        else -> Triple(ElectricBlue.copy(alpha = 0.15f), ElectricBlue, Icons.Default.Group)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = teamName,
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun RoleBadge(
    role: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (role) {
        "Yetkili Öğretmen" -> Pair(Color(0xFF8B5CF6).copy(alpha = 0.18f), Color(0xFF7C3AED))
        "Takım Kaptanı" -> Pair(PhoenixAmber.copy(alpha = 0.18f), Color(0xFFD97706))
        "Takım Yardımcısı" -> Pair(Color(0xFFF97316).copy(alpha = 0.18f), Color(0xFFEA580C))
        "Yönetici" -> Pair(Color(0xFFE11D48).copy(alpha = 0.18f), Color(0xFFE11D48))
        "Elektronik Sorumlusu" -> Pair(ElectricBlue.copy(alpha = 0.18f), ElectricBlue)
        "Tasarım Sorumlusu" -> Pair(Color(0xFF10B981).copy(alpha = 0.18f), Color(0xFF059669))
        "Sosyal Medya Sorumlusu" -> Pair(PRColor.copy(alpha = 0.18f), PRColor)
        "Proje Sorumlusu" -> Pair(Color(0xFF0284C7).copy(alpha = 0.18f), Color(0xFF0369A1))
        else -> Pair(Color(0xFF64748B).copy(alpha = 0.12f), Color(0xFF475569))
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (role in listOf("Yetkili Öğretmen", "Takım Kaptanı", "Yönetici")) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(10.dp)
                )
            }
            Text(
                text = role,
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserRolesRow(
    roles: List<String>,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        roles.forEach { role ->
            RoleBadge(role = role)
        }
    }
}

@Composable
fun AnimatedHeartLikeButton(
    isLiked: Boolean,
    likeCount: Long,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    var animateTrigger by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (animateTrigger) 1.35f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        finishedListener = {
            if (animateTrigger) {
                animateTrigger = false
            }
        },
        label = "heart_scale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                animateTrigger = true
                onToggleLike()
            }
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("heart_like_button")
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isLiked) "Beğenildi" else "Beğen",
            tint = if (isLiked) Color(0xFFEF4444) else Color(0xFF94A3B8),
            modifier = Modifier
                .size(20.dp)
                .scale(scale)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = likeCount.toString(),
            fontSize = 13.sp,
            fontWeight = if (isLiked) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isLiked) Color(0xFFEF4444) else Color(0xFF64748B)
        )
    }
}

@Composable
fun IdeaCategoryBadge(
    category: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (category) {
        "Yeni", "Yeni fikir" -> Pair(Color(0xFF0284C7).copy(alpha = 0.15f), Color(0xFF0284C7))
        "Geliştiriliyor", "Geliştirilecek" -> Pair(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFD97706))
        "Değerlendiriliyor" -> Pair(Color(0xFF8B5CF6).copy(alpha = 0.15f), Color(0xFF7C3AED))
        "Kabul Edildi", "Kabul edildi" -> Pair(Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF059669))
        "Tamamlandı" -> Pair(Color(0xFF059669).copy(alpha = 0.20f), Color(0xFF047857))
        "Reddedildi" -> Pair(Color(0xFFEF4444).copy(alpha = 0.15f), Color(0xFFDC2626))
        else -> Pair(Color(0xFF64748B).copy(alpha = 0.15f), Color(0xFF64748B))
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = category,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun CodeSnippetViewer(
    fileName: String,
    codeContent: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = fileName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Blacksea Robotics Code", codeContent)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Kod panoya kopyalandı", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Kodu Kopyala",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    text = codeContent,
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
