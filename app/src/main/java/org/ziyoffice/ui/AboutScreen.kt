package org.ziyoffice.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.ziyoffice.BuildConfig
import org.ziyoffice.R

/**
 * The About screen is also the privacy receipt: it reads the permissions the
 * installed APK *actually* declares from the system and shows them to the user.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val declaredPermissions = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                ?.toList()
                .orEmpty()
        }
    }

    val permissionSummary = when {
        declaredPermissions.isFailure -> "Could not read permissions."
        declaredPermissions.getOrDefault(emptyList()).isEmpty() -> {
            "None. So Android itself makes it impossible for ZiyOffice to reach the network."
        }
        else -> declaredPermissions.getOrDefault(emptyList()).joinToString(", ")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("ZiyOffice", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Version ${BuildConfig.VERSION_NAME} \u00B7 Documents, Sheets, Slides and PDF \u2014 offline",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "A free, open-source office suite. No network permission, no accounts, no ads.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                stringResource(R.string.about_privacy_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text("\u2022 Permissions declared by this app: $permissionSummary")
            Text("\u2022 " + stringResource(R.string.about_privacy_no_network))
            Text("\u2022 " + stringResource(R.string.about_privacy_no_ads))
            Text("\u2022 " + stringResource(R.string.about_privacy_local))

            Text(
                "License",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "GNU GPL-3.0-or-later \u2014 free software, forever. The full source text ships in the project repository.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
