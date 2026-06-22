package eu.r3pr3ss10n.hangar.ui.account

import android.content.Intent
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.r3pr3ss10n.hangar.R

private const val GITHUB_URL = "https://github.com/r3pr3ss10n/Hangar"
private const val AUTHOR_URL = "https://github.com/r3pr3ss10n"

/** Static "About Hangar" page: brand, version, credit and a link to the repo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }
    // The actual launcher icon, so this matches the app icon exactly.
    val appIcon = remember {
        context.packageManager.getApplicationIcon(context.packageName)
            .toBitmap(width = 288, height = 288)
            .asImageBitmap()
    }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.tg_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))

            // App icon
            Image(
                bitmap = appIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp)),
            )

            Spacer(Modifier.height(16.dp))
            Text("Hangar", style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.about_version, version),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            Text(
                stringResource(R.string.about_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
            )

            Button(onClick = { open(GITHUB_URL) }) {
                Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.about_view_on_github),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()

            ListItem(
                modifier = Modifier.clickable { open(AUTHOR_URL) },
                headlineContent = { Text(stringResource(R.string.about_author)) },
                trailingContent = {
                    Text(
                        "r3pr3ss10n",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                leadingContent = { Icon(Icons.Filled.Gavel, null) },
                headlineContent = { Text(stringResource(R.string.about_license)) },
            )
        }
    }
}
