package az.cachecleaner

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val perms = mutableStateOf(Perms.Status(false, false, false))
    private val inPip = mutableStateOf(false)

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            CleanerService.instance?.stopClean()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(
            this, stopReceiver, IntentFilter(ACTION_STOP), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val s by CleanerState.state.collectAsState()
                    CacheApp(
                        perms = perms.value,
                        inPip = inPip.value,
                        s = s,
                        onGrant = { step -> Perms.open(this@MainActivity, step) },
                        onAppInfo = { Perms.openAppInfo(this@MainActivity) },
                        onStart = { startCleaning() },
                        onStop = { CleanerService.instance?.stopClean() },
                        onReset = { CleanerState.reset() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        perms.value = Perms.read(this)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Proses gedərkən Home-a basılsa, avtomatik PiP-ə keç
        if (CleanerState.state.value.phase == Phase.RUNNING) enterPip()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(stopReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    private fun startCleaning() {
        val status = Perms.read(this)
        perms.value = status
        if (status.next() != null) return
        val svc = CleanerService.instance
        if (svc == null) {
            Toast.makeText(
                this,
                "Əlçatanlıq xidməti hələ qoşulmayıb. Bir neçə saniyə sonra yenidən cəhd edin.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        svc.startClean()
        enterPip()
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < 26) return
        try {
            enterPictureInPictureMode(pipParams())
        } catch (_: Exception) {
            // PiP olmasa üzən çip istifadə olunur
        }
    }

    @RequiresApi(26)
    private fun pipParams(): PictureInPictureParams {
        val pi = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_STOP).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val action = RemoteAction(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Dayandır", "Prosesi dayandır", pi
        )
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(3, 2))
            .setActions(listOf(action))
            .build()
    }

    companion object {
        const val ACTION_STOP = "az.cachecleaner.STOP"
    }
}

// ======================= UI =======================

@Composable
private fun CacheApp(
    perms: Perms.Status, inPip: Boolean, s: UiState,
    onGrant: (Perms.Step) -> Unit, onAppInfo: () -> Unit,
    onStart: () -> Unit, onStop: () -> Unit, onReset: () -> Unit
) {
    val step = perms.next()
    when {
        inPip -> PipView(s)
        step != null -> PermissionView(step, onGrant, onAppInfo)
        s.phase == Phase.IDLE -> HomeView(onStart)
        s.phase == Phase.RUNNING -> RunningView(s, onStop)
        else -> ReportView(s, onReset)
    }
}

@Composable
private fun PermissionView(step: Perms.Step, onGrant: (Perms.Step) -> Unit, onAppInfo: () -> Unit) {
    val title: String
    val desc: String
    when (step) {
        Perms.Step.OVERLAY -> {
            title = "Üstə görünmə icazəsi"
            desc = "Proses zamanı digər ekranların üstündə faiz və Dayandır düyməsini göstərmək üçün lazımdır. Açılan səhifədə Cache Cleaner üçün icazəni aktiv edin."
        }
        Perms.Step.PIP -> {
            title = "Şəkil içində şəkil (PiP)"
            desc = "Təmizlik başlayanda kiçik pəncərədə faizi göstərmək üçün lazımdır. Açılan səhifədə PiP-ə icazə verin."
        }
        Perms.Step.ACCESSIBILITY -> {
            title = "Əlçatanlıq xidməti"
            desc = "Ayarlar → Tətbiqlər səhifələrində yalnız \"Keşi təmizlə\" düyməsinə basmaq üçün lazımdır. Xidməti tapıb aktiv edin. Android 13+ üçün icazə boz görünürsə: aşağıdakı \"Tətbiq məlumatı\" düyməsi → ⋮ → \"Məhdud ayarlara icazə ver\"."
        }
    }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Addım ${step.ordinal + 1} / 3", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(desc, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Button(onClick = { onGrant(step) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("İcazə ver")
        }
        if (step == Perms.Step.ACCESSIBILITY) {
            TextButton(onClick = onAppInfo) { Text("Tətbiq məlumatı") }
        }
        Spacer(Modifier.height(16.dp))
        Text("İcazə verilmədən davam etmək mümkün deyil.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HomeView(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Cache Cleaner", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Yalnız keş təmizlənir. Hesab və şəxsi məlumatlara toxunulmur.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(48.dp))
        Button(onClick = onStart, shape = CircleShape, modifier = Modifier.size(190.dp)) {
            Text("TƏMİZLƏ", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RunningView(s: UiState, onStop: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Text("${s.percent}%", fontSize = 84.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp))
        Spacer(Modifier.height(24.dp))
        Text("Təmizlənən keş", style = MaterialTheme.typography.labelLarge)
        Text(formatBytes(s.bytesCleaned), fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Text("${s.processed} / ${s.total}  ·  ${s.currentApp}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(76.dp)
        ) { Text("DAYANDIR", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun PipView(s: UiState) {
    val status = when (s.phase) {
        Phase.RUNNING -> "Təmizlənir…"
        Phase.FINISHED -> "Tamamlandı"
        Phase.STOPPED -> "Dayandırıldı"
        Phase.IDLE -> ""
    }
    Column(
        Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("${s.percent}%", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(formatBytes(s.bytesCleaned), fontSize = 16.sp)
        Text(status, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ReportView(s: UiState, onReset: () -> Unit) {
    val title = if (s.phase == Phase.FINISHED) "Təmizlik tamamlandı" else "Proses dayandırıldı"
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Text("Təmizlənən keş", style = MaterialTheme.typography.labelLarge)
        Text(formatBytes(s.bytesCleaned), fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Sərf olunan vaxt: ${formatDuration(s.endMs - s.startMs)}")
        Text("Yoxlanılan: ${s.processed}/${s.total}  ·  Uğurlu: ${s.okCount}  ·  Keçilən: ${s.skipped}")
        Spacer(Modifier.height(16.dp))
        Text("Təmizlənən tətbiqlər (${s.cleaned.size})", style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(s.cleaned.sortedByDescending { it.bytes }, key = { it.pkg }) { a ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(a.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(12.dp))
                    Text(formatBytes(a.bytes), fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onReset, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Bağla") }
    }
}
