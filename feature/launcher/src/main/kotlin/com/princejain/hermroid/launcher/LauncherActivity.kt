package com.princejain.hermroid.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import com.princejain.hermroid.design.HermroidTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { HermroidTheme { LauncherHome(this) } }
    }
}

private data class LauncherApp(
    val label: String,
    val packageName: String,
    val componentName: ComponentName,
    val icon: ImageBitmap,
)

@Composable
private fun LauncherHome(context: Context) {
    val preferences = remember { context.getSharedPreferences("hermroid_launcher", Context.MODE_PRIVATE) }
    var columns by remember { mutableIntStateOf(preferences.getInt("columns", 4).coerceIn(3, 8)) }
    var query by remember { mutableStateOf("") }
    val apps = remember { loadApps(context) }
    val shown = remember(apps, query) { apps.filter { it.label.contains(query, ignoreCase = true) } }

    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "columns") columns = prefs.getInt("columns", 4).coerceIn(3, 8)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(top = 42.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Home", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { openHermroid(context) }) { Icon(Icons.Rounded.AutoAwesome, "Open Hermroid") }
                IconButton(onClick = { openHermroid(context) }) { Icon(Icons.Rounded.Settings, "Hermroid settings") }
            }
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                shape = RoundedCornerShape(18.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(shown, key = { it.componentName.flattenToString() }) { app ->
                    Column(
                        Modifier.clickable { launch(context, app) }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(shape = CircleShape, shadowElevation = 2.dp) {
                            Image(app.icon, app.label, Modifier.size(52.dp))
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (shown.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No apps found") }
                }
            }
        }
    }
}

private fun loadApps(context: Context): List<LauncherApp> {
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(intent, 0).mapNotNull { info ->
        val activity = info.activityInfo ?: return@mapNotNull null
        LauncherApp(
            label = info.loadLabel(packageManager).toString(),
            packageName = activity.packageName,
            componentName = ComponentName(activity.packageName, activity.name),
            icon = info.loadIcon(packageManager).toBitmap(128, 128).asImageBitmap(),
        )
    }.distinctBy { it.componentName }.sortedBy { it.label.lowercase() }
}

private fun launch(context: Context, app: LauncherApp) {
    context.startActivity(Intent(Intent.ACTION_MAIN).apply {
        component = app.componentName
        addCategory(Intent.CATEGORY_LAUNCHER)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    })
}

private fun openHermroid(context: Context) {
    context.startActivity(Intent().setClassName(context.packageName, "com.princejain.hermroid.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
