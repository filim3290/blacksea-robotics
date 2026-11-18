package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.model.PLATFORM_MAIN_TEAM_ID
import com.example.model.TeamInfo
import com.example.model.TeamLogoPreset
import com.example.model.TeamType
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ManagementColor
import com.example.ui.theme.ManagementDarkInk
import com.example.ui.theme.ManagementLightInk
import com.example.ui.theme.MechanicColor
import com.example.ui.theme.MechanicDarkInk
import com.example.ui.theme.MechanicLightInk
import com.example.ui.theme.PRColor
import com.example.ui.theme.PRDarkInk
import com.example.ui.theme.PRLightInk
import com.example.ui.theme.PhoenixAmber
import com.example.ui.theme.SoftwareColor
import com.example.ui.theme.SoftwareDarkInk
import com.example.ui.theme.SoftwareLightInk
import com.example.ui.theme.blackseaColors

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

fun parseTeamColor(hex: String?, fallback: Color = ElectricBlue): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        val clean = hex.trim().removePrefix("#")
        val parsed = android.graphics.Color.parseColor("#$clean")
        Color(parsed)
    } catch (_: Exception) {
        fallback
    }
}

fun teamLogoPresetIcon(logoKey: String): ImageVector {
    return when (TeamLogoPreset.fromKey(logoKey)) {
        TeamLogoPreset.BSR_PHOENIX -> Icons.Default.Shield
        TeamLogoPreset.ROCKET -> Icons.Default.RocketLaunch
        TeamLogoPreset.DRONE -> Icons.Default.Flight
        TeamLogoPreset.AI_BRAIN -> Icons.Default.Psychology
        TeamLogoPreset.GEAR -> Icons.Default.Engineering
        TeamLogoPreset.BOLT -> Icons.Default.ElectricBolt
        TeamLogoPreset.CIRCUIT -> Icons.Default.Memory
        TeamLogoPreset.SHIELD -> Icons.Default.Groups
    }
}

@Composable
fun TeamLogoBadge(
    team: TeamInfo,
    size: Int = 48,
    modifier: Modifier = Modifier
) {
    val logoUrl = team.logoUrl.trim()
    val cornerRadius = (size * 0.25f).dp

    if (logoUrl.isNotBlank()) {
        if (logoUrl.startsWith("data:image", ignoreCase = true)) {
            val bitmap: ImageBitmap? = remember(logoUrl) {
                try {
                    val base64Part = logoUrl.substringAfter("base64,", "")
                    if (base64Part.isBlank()) null
                    else {
                        val decoded = Base64.decode(base64Part, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(decoded, 0, decoded.size)?.asImageBitmap()
                    }
                } catch (_: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "${team.name} Logo",
                    contentScale = ContentScale.Crop,
                    modifier = modifier
                        .size(size.dp)
                        .clip(RoundedCornerShape(cornerRadius))
                )
                return
            }
        } else {
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = "${team.name} Logo",
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .size(size.dp)
                    .clip(RoundedCornerShape(cornerRadius)),
                loading = {
                    TeamPresetOrMainLogo(team = team, size = size)
                },
                error = {
                    TeamPresetOrMainLogo(team = team, size = size)
                }
            )
            return
        }
    }

    TeamPresetOrMainLogo(team = team, size = size, modifier = modifier)
}

@Composable
private fun TeamPresetOrMainLogo(
    team: TeamInfo,
    size: Int,
    modifier: Modifier = Modifier
) {
    if (team.isMainTeam || team.id == PLATFORM_MAIN_TEAM_ID || team.logoKey == TeamLogoPreset.BSR_PHOENIX.key) {
        BlackseaLogo(size = size, modifier = modifier)
    } else {
        val accent = parseTeamColor(team.accentColorHex)
        val deeper = accent.copy(
            red = (accent.red * 0.62f).coerceIn(0f, 1f),
            green = (accent.green * 0.62f).coerceIn(0f, 1f),
            blue = (accent.blue * 0.62f).coerceIn(0f, 1f)
        )
        Box(
            modifier = modifier
                .size(size.dp)
                .clip(RoundedCornerShape((size * 0.25f).dp))
                .background(Brush.linearGradient(listOf(accent, deeper))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = teamLogoPresetIcon(team.logoKey),
                contentDescription = "${team.name} Logo",
                tint = Color.White,
                modifier = Modifier.size((size * 0.54f).dp)
            )
        }
    }
}

@Composable
fun DynamicTeamBanner(
    team: TeamInfo,
    memberCount: Long? = null,
    onSwitchTeamClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ext = MaterialTheme.blackseaColors
    val isMain = team.isMainTeam || team.id == PLATFORM_MAIN_TEAM_ID
    val accent = parseTeamColor(team.accentColorHex, ElectricBlue)
    val gradientColors = if (isMain) {
        ext.bannerGradientColors
    } else {
        listOf(
            accent.copy(
                red = (accent.red * 0.45f).coerceIn(0f, 1f),
                green = (accent.green * 0.45f).coerceIn(0f, 1f),
                blue = (accent.blue * 0.45f).coerceIn(0f, 1f)
            ),
            accent.copy(
                red = (accent.red * 0.85f).coerceIn(0f, 1f),
                green = (accent.green * 0.85f).coerceIn(0f, 1f),
                blue = (accent.blue * 0.85f).coerceIn(0f, 1f)
            )
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("team_hero_banner"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = if (ext.isDark) 4.dp else 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(colors = gradientColors))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TeamLogoBadge(team = team, size = 60)

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = team.name.ifBlank { "BLACKSEA ROBOTICS" }.uppercase(),
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (team.shortCode.isNotBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.White.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = team.shortCode,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        val subtitle = listOf(team.schoolName, team.city)
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")
                            .ifBlank { "Ünye Fen Lisesi Robotik Takımı" }
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.86f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (team.competition.isNotBlank() || team.motto.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = team.motto.ifBlank { team.competition },
                                color = if (isMain) ext.bannerMottoColor else Color(0xFFFDE68A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (team.competition.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = team.competition,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (memberCount != null && memberCount > 0L) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "$memberCount Üye",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (onSwitchTeamClick != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier
                                .clickable { onSwitchTeamClick() }
                                .testTag("banner_switch_team_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "Takım Değiştir",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Takımlar",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BlackseaTeamBanner(
    modifier: Modifier = Modifier
) {
    DynamicTeamBanner(team = TeamInfo.mainBlackseaTeam(), modifier = modifier)
}

@Composable
fun TeamBadge(
    teamName: String,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.blackseaColors.isDark
    val bgAlpha = if (isDark) 0.22f else 0.14f
    val (bgColor, textColor, icon) = when (teamName) {
        TeamType.MANAGEMENT.displayName -> Triple(
            ManagementColor.copy(alpha = bgAlpha),
            if (isDark) ManagementDarkInk else ManagementLightInk,
            Icons.Default.Security
        )
        TeamType.PR.displayName -> Triple(
            PRColor.copy(alpha = bgAlpha),
            if (isDark) PRDarkInk else PRLightInk,
            Icons.Default.Handshake
        )
        TeamType.MECHANIC.displayName -> Triple(
            MechanicColor.copy(alpha = bgAlpha),
            if (isDark) MechanicDarkInk else MechanicLightInk,
            Icons.Default.Engineering
        )
        TeamType.SOFTWARE.displayName -> Triple(
            SoftwareColor.copy(alpha = bgAlpha),
            if (isDark) SoftwareDarkInk else SoftwareLightInk,
            Icons.Default.Memory
        )
        else -> Triple(
            ElectricBlue.copy(alpha = bgAlpha),
            MaterialTheme.blackseaColors.accentBlue,
            Icons.Default.Group
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RoleBadge(
    role: String,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.blackseaColors.isDark
    val alpha = if (isDark) 0.22f else 0.13f
    val (bgColor, textColor) = when (role) {
        "Yetkili Öğretmen", "Öğretmen" -> Pair(Color(0xFF8B5CF6).copy(alpha = alpha), if (isDark) Color(0xFFC4B5FD) else Color(0xFF6D28D9))
        "Takım Kaptanı", "Kaptan" -> Pair(PhoenixAmber.copy(alpha = alpha), if (isDark) Color(0xFFFCD34D) else Color(0xFFB45309))
        "Takım Yardımcısı" -> Pair(Color(0xFFF97316).copy(alpha = alpha), if (isDark) Color(0xFFFDBA74) else Color(0xFFC2410C))
        "Yönetici" -> Pair(Color(0xFFE11D48).copy(alpha = alpha), if (isDark) Color(0xFFFDA4AF) else Color(0xFFBE123C))
        "Elektronik Sorumlusu" -> Pair(ElectricBlue.copy(alpha = alpha), if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1))
        "Tasarım Sorumlusu" -> Pair(Color(0xFF10B981).copy(alpha = alpha), if (isDark) Color(0xFF34D399) else Color(0xFF047857))
        "Sosyal Medya Sorumlusu" -> Pair(PRColor.copy(alpha = alpha), if (isDark) PRDarkInk else PRLightInk)
        "Proje Sorumlusu" -> Pair(Color(0xFF0284C7).copy(alpha = alpha), if (isDark) Color(0xFF7DD3FC) else Color(0xFF0369A1))
        else -> Pair(
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
            MaterialTheme.blackseaColors.textSecondary
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (role in listOf("Yetkili Öğretmen", "Öğretmen", "Takım Kaptanı", "Kaptan", "Yönetici")) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(11.dp)
                )
            }
            Text(
                text = role,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
        val ext = MaterialTheme.blackseaColors
        Icon(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isLiked) "Beğenildi" else "Beğen",
            tint = if (isLiked) ext.dangerSolid else ext.textMuted,
            modifier = Modifier
                .size(20.dp)
                .scale(scale)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = likeCount.toString(),
            fontSize = 13.sp,
            fontWeight = if (isLiked) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isLiked) ext.dangerText else ext.textSecondary
        )
    }
}

@Composable
fun IdeaCategoryBadge(
    category: String,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.blackseaColors.isDark
    val alpha = if (isDark) 0.24f else 0.14f
    val (bgColor, textColor) = when (category) {
        "Yeni", "Yeni fikir" -> Pair(Color(0xFF0284C7).copy(alpha = alpha), if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1))
        "Geliştiriliyor", "Geliştirilecek" -> Pair(Color(0xFFF59E0B).copy(alpha = alpha), if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309))
        "Değerlendiriliyor" -> Pair(Color(0xFF8B5CF6).copy(alpha = alpha), if (isDark) Color(0xFFC4B5FD) else Color(0xFF6D28D9))
        "Kabul Edildi", "Kabul edildi" -> Pair(Color(0xFF10B981).copy(alpha = alpha), if (isDark) Color(0xFF34D399) else Color(0xFF047857))
        "Tamamlandı" -> Pair(Color(0xFF059669).copy(alpha = alpha), if (isDark) Color(0xFF6EE7B7) else Color(0xFF065F46))
        "Reddedildi" -> Pair(Color(0xFFEF4444).copy(alpha = alpha), if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C))
        else -> Pair(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f), MaterialTheme.blackseaColors.textSecondary)
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
    val ext = MaterialTheme.blackseaColors
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ext.codeBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ext.codeHeaderBackground)
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
                        tint = ext.accentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = fileName,
                        color = ext.textPrimary,
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
                        tint = ext.textSecondary,
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
                    color = ext.codeTextColor,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
