package com.vknxworks.truectrl.ui.system

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group as GroupIcon
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.system.Accounts
import com.vknxworks.truectrl.data.system.GroupInfo
import com.vknxworks.truectrl.data.system.UserInfo
import com.vknxworks.truectrl.ui.components.CtrlSheet
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.GroupSlice
import com.vknxworks.truectrl.ui.components.GroupSwitch
import com.vknxworks.truectrl.ui.components.relativeTime
import com.vknxworks.truectrl.ui.theme.Health

/** The server's users and groups, to look at: who exists, what they may do and who is in which group. */
@Composable
fun AccountsScreen() {
    val c = LocalContext.current.container
    var builtin by rememberSaveable { mutableStateOf(false) }
    var user by remember { mutableStateOf<UserInfo?>(null) }
    var group by remember { mutableStateOf<GroupInfo?>(null) }
    var accounts by remember { mutableStateOf<Accounts?>(null) }

    LoadScreen(
        title = stringResource(R.string.accounts_title),
        icon = Icons.Outlined.Person,
        failedTitle = stringResource(R.string.accounts_failed),
        load = { c.admin.accounts().also { accounts = it } },
        pollMs = 120_000L,
    ) { all ->
        val users = remember(all, builtin) { all.users.filter { builtin || !it.builtin } }
        val groups = remember(all, builtin) { all.groups.filter { builtin || !it.builtin } }
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "filter") {
                Group { GroupSwitch(stringResource(R.string.accounts_builtin), stringResource(R.string.accounts_builtin_sub), checked = builtin) { builtin = it } }
            }
            if (users.isEmpty()) {
                item(key = "no-users") { Group(stringResource(R.string.accounts_users)) { GroupRow(stringResource(R.string.accounts_no_users)) } }
            }
            itemsIndexed(users, key = { _, u -> "u" + u.id }, contentType = { _, _ -> "user" }) { i, u ->
                GroupSlice(first = i == 0, last = i == users.lastIndex, title = stringResource(R.string.accounts_users)) {
                    GroupRow(
                        u.username,
                        listOfNotNull(
                            u.fullName.ifBlank { null },
                            if (u.locked) stringResource(R.string.account_locked) else null,
                            if (u.roles.isNotEmpty()) stringResource(R.string.account_truenas_access) else null,
                        ).joinToString("  ·  ").ifBlank { null },
                        Icons.Outlined.Person,
                        iconTint = if (u.locked) Health.warn else Health.ok,
                        showChevron = true,
                        onClick = { user = u },
                    )
                }
            }
            if (groups.isEmpty()) {
                item(key = "no-groups") { Group(stringResource(R.string.accounts_groups)) { GroupRow(stringResource(R.string.accounts_no_groups)) } }
            }
            itemsIndexed(groups, key = { _, g -> "g" + g.id }, contentType = { _, _ -> "group" }) { i, g ->
                GroupSlice(first = i == 0, last = i == groups.lastIndex, title = stringResource(R.string.accounts_groups)) {
                    GroupRow(
                        g.name,
                        listOfNotNull(
                            pluralStringResource(R.plurals.account_members, g.userIds.size, g.userIds.size),
                            if (g.roles.isNotEmpty()) stringResource(R.string.account_truenas_access) else null,
                        ).joinToString("  ·  "),
                        Icons.Outlined.GroupIcon,
                        showChevron = true,
                        onClick = { group = g },
                    )
                }
            }
        }
    }

    val all = accounts
    user?.let { u ->
        CtrlSheet(onDismissRequest = { user = null }) {
            SheetTitle(u.username, u.fullName.ifBlank { null })
            Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 16.dp)) {
                val yes = stringResource(R.string.yes)
                val no = stringResource(R.string.no)
                val otherGroups = all?.groups.orEmpty().filter { it.id in u.groupIds }.map { it.name }
                Fact(R.string.account_uid, u.uid.toString())
                Fact(R.string.account_kind, stringResource(if (u.builtin) R.string.account_kind_builtin else if (u.local) R.string.account_kind_local else R.string.account_kind_directory))
                Fact(R.string.account_primary_group, u.group.ifBlank { stringResource(R.string.no_value) })
                Fact(R.string.account_other_groups, otherGroups.joinToString().ifBlank { stringResource(R.string.none) })
                Fact(R.string.account_roles, u.roles.joinToString { roleLabel(it) }.ifBlank { stringResource(R.string.none) })
                Fact(R.string.account_locked_label, if (u.locked) yes else no)
                Fact(R.string.account_password, stringResource(if (u.passwordDisabled) R.string.account_password_off else R.string.account_password_on))
                u.passwordChangedMs?.let { Fact(R.string.account_password_changed, relativeTime(it)) }
                Fact(R.string.account_two_factor, if (u.twoFactor) yes else no)
                Fact(R.string.account_api_keys, u.apiKeys.toString())
                Fact(R.string.account_smb, if (u.smb) yes else no)
                Fact(
                    R.string.account_ssh,
                    listOfNotNull(
                        if (u.sshPassword) stringResource(R.string.account_ssh_password) else null,
                        if (u.hasSshKey) stringResource(R.string.account_ssh_key) else null,
                    ).joinToString().ifBlank { no },
                )
                Fact(R.string.account_sudo, if (u.sudo) yes else no)
                Fact(R.string.account_home, u.home.ifBlank { stringResource(R.string.no_value) })
                Fact(R.string.account_shell, u.shell.ifBlank { stringResource(R.string.no_value) })
                u.email?.let { Fact(R.string.account_email, it) }
            }
        }
    }
    group?.let { g ->
        CtrlSheet(onDismissRequest = { group = null }) {
            SheetTitle(g.name)
            Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 16.dp)) {
                val yes = stringResource(R.string.yes)
                val no = stringResource(R.string.no)
                val members = all?.users.orEmpty().filter { it.id in g.userIds }.map { it.username }
                Fact(R.string.account_gid, g.gid.toString())
                Fact(R.string.account_kind, stringResource(if (g.builtin) R.string.account_kind_builtin else if (g.local) R.string.account_kind_local else R.string.account_kind_directory))
                Fact(R.string.account_members_label, members.joinToString().ifBlank { stringResource(R.string.none) })
                Fact(R.string.account_roles, g.roles.joinToString { roleLabel(it) }.ifBlank { stringResource(R.string.none) })
                Fact(R.string.account_smb, if (g.smb) yes else no)
                Fact(R.string.account_sudo, if (g.sudo) yes else no)
            }
        }
    }
}

/** FULL_ADMIN → Full admin: TrueNAS's role names made readable. */
private fun roleLabel(role: String): String = role.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

/** One labelled value in a detail sheet. */
@Composable
fun Fact(label: Int, value: String) {
    SheetFact(stringResource(label), value)
}
