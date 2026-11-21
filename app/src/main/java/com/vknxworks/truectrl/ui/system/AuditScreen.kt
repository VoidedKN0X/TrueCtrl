package com.vknxworks.truectrl.ui.system

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.system.AdminRepository
import com.vknxworks.truectrl.data.system.AuditEntry
import com.vknxworks.truectrl.ui.components.CtrlSheet
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.GroupSlice
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.theme.Gutter
import com.vknxworks.truectrl.ui.theme.Health
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.text.DateFormat
import java.util.Date

private val PrettyJson = Json { prettyPrint = true }

/**
 * TrueNAS's audit log, newest first: who did what, from where, and whether it worked. Pick the
 * part of the log (TrueNAS itself, SMB, sudo, system), show only what failed, and load older
 * entries a hundred at a time.
 */
@Composable
fun AuditScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var service by rememberSaveable { mutableStateOf("MIDDLEWARE") }
    var failedOnly by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<AuditEntry?>(null) }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM) }

    LoadScreen(
        title = stringResource(R.string.audit_title),
        icon = Icons.Outlined.FactCheck,
        failedTitle = stringResource(R.string.audit_failed),
        load = { c.admin.audit(service, failedOnly, offset = 0) },
        key = service to failedOnly,
        // Not refreshed on its own: the list would move under your finger. Pull down for the newest.
        pollMs = 3_600_000L,
        header = {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AdminRepository.AUDIT_SERVICES.forEach { s ->
                    FilterChip(selected = s == service, onClick = { service = s }, label = { Text(auditService(s)) })
                }
                FilterChip(selected = failedOnly, onClick = { failedOnly = !failedOnly }, label = { Text(stringResource(R.string.audit_failed_only)) })
            }
        },
    ) { first ->
        // Older pages, added below the first one; a fresh first page starts over.
        var older by remember(first) { mutableStateOf<List<AuditEntry>>(emptyList()) }
        var more by remember(first) { mutableStateOf(first.size >= AdminRepository.AUDIT_PAGE) }
        var loading by remember(first) { mutableStateOf(false) }
        var moreError by remember(first) { mutableStateOf<String?>(null) }
        val entries = first + older

        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (entries.isEmpty()) {
                item(key = "none") { Group { GroupRow(stringResource(R.string.audit_none), stringResource(R.string.audit_none_sub)) } }
            }
            itemsIndexed(entries, key = { i, _ -> i }, contentType = { _, _ -> "entry" }) { i, e ->
                GroupSlice(first = i == 0, last = i == entries.lastIndex) {
                    GroupRow(
                        e.summary ?: e.event.ifBlank { stringResource(R.string.no_value) },
                        listOfNotNull(
                            listOfNotNull(
                                if (!e.success) stringResource(R.string.audit_entry_failed) else null,
                                e.username.ifBlank { null },
                                e.address.ifBlank { null },
                            ).joinToString("  ·  ").ifBlank { null },
                            listOfNotNull(e.atMs?.let { dateFormat.format(Date(it)) }, if (e.summary != null) e.event.ifBlank { null } else null).joinToString("  ·  ").ifBlank { null },
                        ).joinToString("\n"),
                        titleColor = if (e.success) MaterialTheme.colorScheme.onSurface else Health.critical,
                        showChevron = true,
                        onClick = { detail = e },
                    )
                }
            }
            if (more || moreError != null) {
                item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            moreError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            TextButton(
                                enabled = !loading,
                                onClick = {
                                    loading = true
                                    moreError = null
                                    scope.launch {
                                        try {
                                            val page = c.admin.audit(service, failedOnly, offset = entries.size)
                                            older = older + page
                                            more = page.size >= AdminRepository.AUDIT_PAGE
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            moreError = errorMessage(context, e, c.sessions.active.value?.server?.host.orEmpty())
                                        } finally {
                                            loading = false
                                        }
                                    }
                                },
                            ) { Text(stringResource(if (loading) R.string.overview_loading else R.string.audit_older)) }
                        }
                    }
                }
            }
        }
    }

    detail?.let { e ->
        CtrlSheet(onDismissRequest = { detail = null }) {
            SheetTitle(e.summary ?: e.event, e.atMs?.let { dateFormat.format(Date(it)) })
            Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 16.dp)) {
                SheetFact(stringResource(R.string.audit_result), stringResource(if (e.success) R.string.audit_entry_ok else R.string.audit_entry_failed))
                SheetFact(stringResource(R.string.audit_user), e.username.ifBlank { stringResource(R.string.no_value) })
                SheetFact(stringResource(R.string.audit_address), e.address.ifBlank { stringResource(R.string.no_value) })
                SheetFact(stringResource(R.string.audit_event), listOf(auditService(e.service), e.event).filter { it.isNotBlank() }.joinToString("  ·  "))
                e.data?.let { data ->
                    val text = remember(data) { runCatching { PrettyJson.encodeToString(JsonElement.serializer(), data) }.getOrDefault(data.toString()) }
                    Text(
                        stringResource(R.string.audit_details),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
                    )
                    SelectionContainer {
                        Text(
                            text,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The audit log's parts in words. */
@Composable
private fun auditService(service: String): String = when (service) {
    "MIDDLEWARE" -> stringResource(R.string.audit_service_middleware)
    "SMB" -> "SMB"
    "SUDO" -> "sudo"
    "SYSTEM" -> stringResource(R.string.audit_service_system)
    else -> service
}
