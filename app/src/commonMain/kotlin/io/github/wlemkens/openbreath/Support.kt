package io.github.wlemkens.openbreath

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val SUPPORT = listOf(
    "OpenBreath is free, and it stays free — no trial, no unlock, no subscription.",
    "Plenty of apps like this one charge before you have tried them, or ask for a yearly " +
        "subscription. For something that sits quietly and counts your breathing, that seems " +
        "a lot to ask.",
    "So there is no price here. If the app has been good to you and you feel like it, send a " +
        "coffee, or more. If not, that is genuinely fine — it is the same app either way.",
)

/** The amounts on the buttons. Anything else goes through PayPal's own field. */
private val AMOUNTS = listOf(4, 15, 26)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // a phone with nothing that opens links is not a crash
    fun pay(euros: Int?) = runCatching { context.startActivity(paypalIntent(euros)) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Support", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Done") }
        }

        SUPPORT.forEach { paragraph ->
            Text(paragraph, style = MaterialTheme.typography.bodyMedium)
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AMOUNTS.forEach { euros ->
                Button(onClick = { pay(euros) }) { Text("€$euros") }
            }
            OutlinedButton(onClick = { pay(null) }) { Text("Another amount") }
        }

        Text(
            "Opens PayPal in your browser. Nothing is charged from here, and the app is not told " +
                "whether you went through with it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}
