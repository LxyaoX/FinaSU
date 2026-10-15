package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.material.FolkFilledTonalButton
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens

private const val DEVELOPER_GITHUB_URL = "https://github.com/LyraVoid/FinaSU"
private const val DEVELOPER_TELEGRAM_URL = "https://t.me/FinaSU"
private const val DEVELOPER_DONATE_URL = "https://ifdian.net/a/matsuzaka_yuki"

/**
 * The author's profile picture, matching the reference implementation. Served over https because
 * the app deliberately keeps cleartext traffic off outside localhost.
 */
private const val DEVELOPER_AVATAR_URL =
    "https://q.qlogo.cn/headimg_dl?dst_uin=3231515355&spec=640&img_type=jpg"

/**
 * The author card reachable from the settings top bar, mirroring FolkPatch's developer info sheet:
 * a round avatar above the name, role and a short greeting, then the GitHub, Telegram and donation
 * shortcuts.
 *
 * The sheet clears [LocalFolkWallpaperTokens] for the same reason
 * [me.weishu.kernelsu.ui.component.material.ExpressiveDialog] does: a sheet composes below its call
 * site, so it would otherwise inherit the wallpaper materials and turn translucent over a custom
 * wallpaper.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsDeveloperInfo(showDialog: MutableState<Boolean>) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    if (!showDialog.value) return

    CompositionLocalProvider(LocalFolkWallpaperTokens provides null) {
        ModalBottomSheet(
            onDismissRequest = { showDialog.value = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(DEVELOPER_AVATAR_URL)
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .build(),
                        contentDescription = stringResource(R.string.developer_profile_picture),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Matsuzaka Yuki",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                Text(
                    text = stringResource(R.string.developer_and_maintainer),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "\"きょうも いちにち、おつかれさま\"",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FolkFilledTonalButton(
                        onClick = { uriHandler.openUri(DEVELOPER_GITHUB_URL) },
                        modifier = Modifier.height(38.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.github),
                            contentDescription = stringResource(R.string.github),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.github))
                    }

                    FolkFilledTonalButton(
                        onClick = { uriHandler.openUri(DEVELOPER_TELEGRAM_URL) },
                        modifier = Modifier.height(38.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.telegram),
                            contentDescription = stringResource(R.string.telegram),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.telegram))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                FolkFilledTonalButton(
                    onClick = { uriHandler.openUri(DEVELOPER_DONATE_URL) },
                    modifier = Modifier.height(38.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Coffee,
                        contentDescription = stringResource(R.string.support_or_donate),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.support_or_donate))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
