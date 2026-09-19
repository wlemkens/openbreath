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
import androidx.compose.ui.unit.dp
import io.github.wlemkens.openbreath.media.Res
import io.github.wlemkens.openbreath.media.*
import org.jetbrains.compose.resources.stringResource

/**
 * Who the money reaches is said plainly, and that is not only good manners.
 *
 * On iOS the app is published by a non-profit while a tip goes to the person who writes it, and
 * those two facts have to be impossible to confuse. Apple allows this as a gift between people —
 * "a monetary gift to another individual", optional, all of it to the receiver — and not as
 * fundraising for an organisation, which is a different guideline with Apple Pay, fund disclosure
 * and donor receipts attached. A screen that let someone believe they were funding the
 * non-profit would be describing the wrong one. See the monetisation notes in CLAUDE.md.
 *
 * It is also simply true on both stores: nothing is given in return, and every cent goes to one
 * person.
 */
private val SUPPORT = listOf(
    Res.string.support_p1,
    Res.string.support_p2,
    Res.string.support_p3,
    Res.string.support_p4,
)

/** The amounts on the buttons. Anything else goes through PayPal's own field. */
private val AMOUNTS = listOf(4, 15, 26)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val platform = LocalPlatform.current
    // a phone with nothing that opens links is not a crash
    fun pay(euros: Int?) = platform.links.openPayPal(euros)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.support_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack) { Text(stringResource(Res.string.action_done)) }
        }

        SUPPORT.forEach { paragraph ->
            Text(stringResource(paragraph), style = MaterialTheme.typography.bodyMedium)
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AMOUNTS.forEach { euros ->
                Button(onClick = { pay(euros) }) { Text("€$euros") }
            }
            OutlinedButton(onClick = { pay(null) }) { Text(stringResource(Res.string.support_another)) }
        }

        Text(
            stringResource(Res.string.support_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}
