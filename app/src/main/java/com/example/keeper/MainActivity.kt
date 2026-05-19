@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.keeper

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.keeper.theme.KeeperTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* ---- shared constants & helpers ---- */

// Index 0 = "default" (uses the theme surface colour); 1..11 are Keep's palette.
val NoteColors = listOf(
    0x00000000L, 0xFFFAAFA8L, 0xFFF39F76L, 0xFFFFF8B8L, 0xFFE2F6D3L, 0xFFB4DDD3L,
    0xFFD4E4EDL, 0xFFAECCDCL, 0xFFD3BFDBL, 0xFFF6E2DDL, 0xFFE9E3D4L, 0xFFEFEFF1L,
)

// Repeat code -> human label. Matches the recurrence options Google Keep offered.
val Repeats = listOf(
    "NONE" to "Does not repeat", "DAILY" to "Daily", "WEEKLY" to "Weekly",
    "MONTHLY" to "Monthly", "YEARLY" to "Yearly",
)

private fun fmt(ms: Long) = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ms))

/** Which drawer view is showing. One object drives the whole note query. */
data class Filter(
    val name: String,
    val archived: Boolean = false,
    val reminders: Boolean = false,
    val labelId: Long? = null,
)

/*
 * MainActivity hosts the entire app. There is no ViewModel and no navigation
 * graph: a single `editing` state flips between the note grid and the editor,
 * and the SQLite database is always re-read after a change. Three files, read
 * top to bottom.
 */
class MainActivity : ComponentActivity() {
    // Note id to open straight into the editor (set when launched from a notification).
    private val openState = mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Db.init(this)
        Notifier.rescheduleAll(this)              // safety net on every launch
        requestBatteryExemption()
        openState.longValue = intent.getLongExtra("open", 0L)
        enableEdgeToEdge()
        setContent { KeeperTheme { App(openState) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openState.longValue = intent.getLongExtra("open", 0L)
    }

    /** Ask once for a Doze exemption so alarms stay exact and reliable. */
    private fun requestBatteryExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                )
            )
        }
    }
}

/* ---- top-level UI ---- */

@Composable
fun App(openState: androidx.compose.runtime.MutableLongState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)

    var filter by remember { mutableStateOf(Filter("Notes")) }
    var search by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(listOf<Note>()) }
    var labels by remember { mutableStateOf(listOf<Label>()) }
    var editing by remember { mutableStateOf<Note?>(null) }
    var showLabelManager by remember { mutableStateOf(false) }
    var showReliabilityHelp by remember { mutableStateOf(false) }

    fun reload() {
        labels = Db.labels()
        notes = Db.notes(filter.archived, filter.reminders, filter.labelId, search)
    }
    LaunchedEffect(filter, search) { reload() }

    // Re-read the database whenever the app comes back to the foreground, so
    // changes made by notification actions (snooze/done/repeat) show up.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reload()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Open a note straight from a notification tap.
    LaunchedEffect(openState.longValue) {
        if (openState.longValue > 0L) {
            editing = Db.note(openState.longValue)
            openState.longValue = 0L
        }
    }

    // POST_NOTIFICATIONS runtime permission (Android 13+).
    val askNotif = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    LaunchedEffect(Unit) {
        if (ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) askNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    // Database export / import through the system file picker (SAF).
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { ctx.contentResolver.openOutputStream(it)?.use(Db::backup) } }
    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            ctx.contentResolver.openInputStream(it)?.use(Db::restore)
            Notifier.rescheduleAll(ctx)
            reload()
        }
    }

    val current = editing
    if (current != null) {
        NoteEditor(
            note = current,
            labels = labels,
            onAddLabel = { name -> Db.addLabel(name); labels = Db.labels() },
            onClose = { editing = null; reload() },
        )
        return
    }

    // Drag-to-reorder state for the note grid. onMove rearranges the in-memory
    // list for a smooth animation; the new order is persisted on drag release.
    val gridState = rememberLazyStaggeredGridState()
    val reorderState = rememberReorderableLazyStaggeredGridState(gridState) { from, to ->
        notes = notes.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Keeper",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(24.dp),
                )
                fun go(f: Filter) {
                    filter = f; search = ""; searching = false
                    scope.launch { drawer.close() }
                }
                NavigationDrawerItem(
                    label = { Text("Notes") }, selected = filter.name == "Notes",
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    onClick = { go(Filter("Notes")) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Reminders") }, selected = filter.name == "Reminders",
                    icon = { Icon(Icons.Default.Notifications, null) },
                    onClick = { go(Filter("Reminders", reminders = true)) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Archive") }, selected = filter.name == "Archive",
                    onClick = { go(Filter("Archive", archived = true)) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(Modifier.padding(12.dp))
                labels.forEach { label ->
                    NavigationDrawerItem(
                        label = { Text(label.name) },
                        selected = filter.labelId == label.id,
                        onClick = { go(Filter(label.name, labelId = label.id)) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                NavigationDrawerItem(
                    label = { Text("Edit labels") }, selected = false,
                    onClick = { showLabelManager = true; scope.launch { drawer.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(Modifier.padding(12.dp))
                NavigationDrawerItem(
                    label = { Text("Export database") }, selected = false,
                    onClick = { exporter.launch("keeper-backup.db"); scope.launch { drawer.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Import database") }, selected = false,
                    onClick = { importer.launch(arrayOf("*/*")); scope.launch { drawer.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(Modifier.padding(12.dp))
                NavigationDrawerItem(
                    label = { Text("Reminder reliability") }, selected = false,
                    icon = { Icon(Icons.Default.Notifications, null) },
                    onClick = { showReliabilityHelp = true; scope.launch { drawer.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    },
                    title = {
                        if (searching) {
                            TextField(
                                value = search, onValueChange = { search = it },
                                placeholder = { Text("Search notes") },
                                singleLine = true, colors = clearFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(filter.name)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            searching = !searching
                            if (!searching) search = ""
                        }) {
                            Icon(
                                if (searching) Icons.Default.Close else Icons.Default.Search,
                                "Search",
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                // No "create" button in Archive — a new note is never archived.
                if (!filter.archived) {
                    FloatingActionButton(onClick = { editing = Note() }) {
                        Icon(Icons.Default.Add, "New note")
                    }
                }
            },
        ) { pad ->
            if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                    Text(
                        if (search.isNotBlank()) "No matching notes" else "Nothing here yet",
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                LazyVerticalStaggeredGrid(
                    state = gridState,
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(pad),
                    contentPadding = PaddingValues(8.dp),
                    verticalItemSpacing = 8.dp,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(notes, key = { it.id }) { note ->
                        ReorderableItem(reorderState, key = note.id) {
                            NoteTile(
                                note, labels,
                                Modifier.longPressDraggableHandle(
                                    onDragStopped = { Db.reorder(notes.map { it.id }) },
                                ),
                            ) { editing = note }
                        }
                    }
                }
            }
        }
    }

    if (showLabelManager) {
        LabelManagerDialog(
            labels = labels,
            onChanged = { labels = Db.labels() },
            onDismiss = { showLabelManager = false; reload() },
        )
    }

    if (showReliabilityHelp) {
        ReliabilityDialog(onDismiss = { showReliabilityHelp = false })
    }
}

/* ---- note grid tile ---- */

@Composable
fun NoteTile(note: Note, allLabels: List<Label>, modifier: Modifier, onClick: () -> Unit) {
    val (bg, fg) = noteColors(note.color)
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
        border = if (note.color == 0) androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant,
        ) else null,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (note.title.isNotBlank()) {
                    Text(
                        note.title, style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (note.pinned) Icon(Icons.Default.Star, "Pinned", Modifier.size(18.dp))
            }
            if (note.checklist) {
                note.items.take(8).forEach { item ->
                    Text(
                        (if (item.checked) "☑ " else "☐ ") + item.text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (note.items.size > 8)
                    Text("+${note.items.size - 8} more", style = MaterialTheme.typography.bodySmall)
            } else if (note.body.isNotBlank()) {
                Text(
                    note.body, maxLines = 12, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (note.reminderAt > 0L) {
                Spacer(Modifier.height(6.dp))
                Chip(Icons.Default.Notifications, fmt(note.reminderAt))
            }
            val tileLabels = allLabels.filter { it.id in note.labelIds }
            if (tileLabels.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tileLabels.forEach { Chip(Icons.AutoMirrored.Filled.List, it.name) }
                }
            }
        }
    }
}

@Composable
private fun Chip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(Color.Black.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(icon, null, Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/* ---- note editor ---- */

@Composable
fun NoteEditor(
    note: Note,
    labels: List<Label>,
    onAddLabel: (String) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(note.title) }
    var body by remember { mutableStateOf(note.body) }
    var checklist by remember { mutableStateOf(note.checklist) }
    var color by remember { mutableIntStateOf(note.color) }
    var pinned by remember { mutableStateOf(note.pinned) }
    var archived by remember { mutableStateOf(note.archived) }
    var reminderAt by remember { mutableLongStateOf(note.reminderAt) }
    var reminderRepeat by remember { mutableStateOf(note.reminderRepeat) }
    val items = remember { note.items.toMutableStateList() }
    val labelIds = remember { note.labelIds.toMutableStateList() }

    var menu by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    var showLabels by remember { mutableStateOf(false) }

    fun persist() {
        val newRepeat = if (reminderAt > 0L) reminderRepeat else "NONE"
        // A changed reminder time/recurrence is a fresh reminder: let it fire again.
        if (note.reminderAt != reminderAt || note.reminderRepeat != newRepeat)
            note.reminderFired = false
        note.title = title.trim()
        note.body = body
        note.checklist = checklist
        note.color = color
        note.pinned = pinned
        note.archived = archived
        note.reminderAt = reminderAt
        note.reminderRepeat = newRepeat
        note.items = items.filter { it.text.isNotBlank() }.toMutableList()
        note.labelIds = labelIds.toMutableSet()
        if (Db.isBlank(note)) {
            if (note.id != 0L) { Db.delete(note.id); Notifier.cancel(ctx, note.id) }
        } else {
            Db.save(note)
            Notifier.schedule(ctx, note)
        }
    }

    fun saveAndClose() { persist(); onClose() }
    BackHandler { saveAndClose() }

    val (bg, fg) = noteColors(color)
    Scaffold(
        containerColor = bg,
        contentColor = fg,
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = bg, navigationIconContentColor = fg,
                    titleContentColor = fg, actionIconContentColor = fg,
                ),
                navigationIcon = {
                    IconButton(onClick = { saveAndClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                title = {},
                actions = {
                    IconButton(onClick = { pinned = !pinned }) {
                        Icon(
                            Icons.Default.Star, "Pin",
                            tint = if (pinned) fg else fg.copy(alpha = 0.4f),
                        )
                    }
                    IconButton(onClick = { showReminder = true }) {
                        Icon(Icons.Default.Notifications, "Reminder")
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, "More")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (checklist) "Switch to text" else "Switch to checklist") },
                            onClick = {
                                if (checklist) {
                                    // checklist -> text: fold the items into the body
                                    body = (body + "\n" + items.joinToString("\n") { it.text })
                                        .trim()
                                    items.clear()
                                } else if (body.isNotBlank()) {
                                    // text -> checklist: split body lines into items
                                    body.split("\n").filter { it.isNotBlank() }
                                        .forEach { items.add(Item(text = it)) }
                                    body = ""
                                }
                                checklist = !checklist
                                menu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Labels") },
                            onClick = { showLabels = true; menu = false },
                        )
                        DropdownMenuItem(
                            text = { Text(if (archived) "Unarchive" else "Archive") },
                            onClick = { archived = !archived; menu = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menu = false
                                if (note.id != 0L) { Db.delete(note.id); Notifier.cancel(ctx, note.id) }
                                onClose()
                            },
                        )
                    }
                },
            )
        },
        bottomBar = { ColorBar(color) { color = it } },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            TextField(
                value = title, onValueChange = { title = it },
                placeholder = { Text("Title") },
                colors = clearFieldColors(), modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.titleLarge,
            )
            if (checklist) {
                items.forEachIndexed { i, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = item.checked,
                            onCheckedChange = { items[i] = item.copy(checked = it) },
                        )
                        TextField(
                            value = item.text,
                            onValueChange = { items[i] = item.copy(text = it) },
                            placeholder = { Text("List item") },
                            colors = clearFieldColors(), singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { items.removeAt(i) }) {
                            Icon(Icons.Default.Close, "Remove item")
                        }
                    }
                }
                TextButton(
                    onClick = { items.add(Item()) },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add item")
                }
            } else {
                TextField(
                    value = body, onValueChange = { body = it },
                    placeholder = { Text("Note") },
                    colors = clearFieldColors(),
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                )
            }

            // Reminder + labels live below the title and the note text, like
            // Keep. The reminder chip is tappable — it opens the same dialog.
            if (reminderAt > 0L) {
                Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)) {
                    Chip(
                        Icons.Default.Notifications,
                        fmt(reminderAt) + (Repeats.first { it.first == reminderRepeat }
                            .second.let { if (reminderRepeat == "NONE") "" else " · $it" }),
                        onClick = { showReminder = true },
                    )
                }
            }
            val noteLabels = labels.filter { it.id in labelIds }
            if (noteLabels.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                ) { noteLabels.forEach { Chip(Icons.AutoMirrored.Filled.List, it.name) } }
            }
        }
    }

    if (showReminder) {
        ReminderDialog(
            at = reminderAt, repeat = reminderRepeat,
            onSet = { at, rep -> reminderAt = at; reminderRepeat = rep; showReminder = false },
            onRemove = { reminderAt = 0L; reminderRepeat = "NONE"; showReminder = false },
            onDismiss = { showReminder = false },
        )
    }
    if (showLabels) {
        LabelPickerDialog(
            labels = labels, selected = labelIds,
            onToggle = { id -> if (id in labelIds) labelIds.remove(id) else labelIds.add(id) },
            onAddLabel = onAddLabel,
            onDismiss = { showLabels = false },
        )
    }
}

/** The palette strip pinned to the bottom of the editor. */
@Composable
fun ColorBar(selected: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NoteColors.indices.forEach { idx ->
            val swatch = if (idx == 0) MaterialTheme.colorScheme.surface else Color(NoteColors[idx])
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(swatch)
                    .border(
                        width = if (idx == selected) 3.dp else 1.dp,
                        color = if (idx == selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { onPick(idx) },
                contentAlignment = Alignment.Center,
            ) {
                if (idx == 0) Text("⊘", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/* ---- dialogs ---- */

@Composable
fun ReminderDialog(
    at: Long, repeat: String,
    onSet: (Long, String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var time by remember { mutableLongStateOf(if (at > 0L) at else preset(18, 0)) }
    var rep by remember { mutableStateOf(repeat) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder") },
        text = {
            Column {
                Text(fmt(time), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { time = preset(18, 0) }) { Text("Evening") }
                    OutlinedButton(onClick = { time = preset(8, 1) }) { Text("Tomorrow") }
                    OutlinedButton(onClick = { time = preset(8, 7) }) { Text("Next week") }
                }
                TextButton(onClick = { pickDateTime(ctx, time) { time = it } }) {
                    Text("Pick date & time…")
                }
                Spacer(Modifier.height(8.dp))
                Text("Repeat", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Repeats.forEach { (code, label) ->
                        FilterChip(
                            selected = rep == code,
                            onClick = { rep = code },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRemove) { Text("Remove reminder") }
            }
        },
        confirmButton = { TextButton(onClick = { onSet(time, rep) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Per-note label assignment, with inline creation of new labels. */
@Composable
fun LabelPickerDialog(
    labels: List<Label>,
    selected: List<Long>,
    onToggle: (Long) -> Unit,
    onAddLabel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Labels") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                labels.forEach { label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { onToggle(label.id) },
                    ) {
                        Checkbox(
                            checked = label.id in selected,
                            onCheckedChange = { onToggle(label.id) },
                        )
                        Text(label.name)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = newName, onValueChange = { newName = it },
                        placeholder = { Text("New label") },
                        singleLine = true, colors = clearFieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { if (newName.isNotBlank()) { onAddLabel(newName.trim()); newName = "" } },
                    ) { Icon(Icons.Default.Add, "Add label") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** Rename / delete / add labels (the drawer's "Edit labels"). */
@Composable
fun LabelManagerDialog(labels: List<Label>, onChanged: () -> Unit, onDismiss: () -> Unit) {
    // Local editable copy so renaming does not reorder the list mid-typing.
    val rows = remember { labels.map { it.id to mutableStateOf(it.name) }.toMutableStateList() }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {
            rows.forEach { (id, name) -> Db.renameLabel(id, name.value) }
            onChanged(); onDismiss()
        },
        title = { Text("Edit labels") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rows.forEach { (id, name) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = name.value, onValueChange = { name.value = it },
                            singleLine = true, colors = clearFieldColors(),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            Db.deleteLabel(id)
                            rows.removeAll { it.first == id }
                            onChanged()
                        }) { Icon(Icons.Default.Delete, "Delete label") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = newName, onValueChange = { newName = it },
                        placeholder = { Text("New label") },
                        singleLine = true, colors = clearFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        if (newName.isNotBlank()) {
                            val id = Db.addLabel(newName.trim())
                            rows.add(id to mutableStateOf(newName.trim()))
                            newName = ""
                            onChanged()
                        }
                    }) { Icon(Icons.Default.Add, "Add label") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                rows.forEach { (id, name) -> Db.renameLabel(id, name.value) }
                onChanged(); onDismiss()
            }) { Text("Done") }
        },
    )
}

/** Help screen explaining how to keep reminders firing on aggressive OEMs
 *  (Xiaomi/MIUI especially), with shortcuts to the relevant system settings. */
@Composable
fun ReliabilityDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val pkg = ctx.packageName

    // Launches a settings screen; falls back to this app's details page so a
    // device lacking the target component (e.g. non-MIUI) never crashes.
    fun open(intent: Intent) {
        val fallback = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { ctx.startActivity(fallback) } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder reliability") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Reminders use exact alarms. Some phones — Xiaomi/MIUI " +
                        "especially — still freeze the app and delay or drop them. " +
                        "Grant these so reminders fire on time:",
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        open(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:$pkg"),
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Battery: set \"No restrictions\"") }
                OutlinedButton(
                    onClick = {
                        open(
                            Intent().setComponent(
                                ComponentName(
                                    "com.miui.securitycenter",
                                    "com.miui.permcenter.autostart" +
                                        ".AutoStartManagementActivity",
                                )
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Allow Autostart (MIUI)") }
                OutlinedButton(
                    onClick = {
                        open(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Notification settings") }
                OutlinedButton(
                    onClick = {
                        open(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:$pkg"),
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Allow exact alarms") }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Also, in the recent-apps view: lock Keeper (padlock icon) so " +
                        "the system keeps it running, and don't swipe it away.",
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/* ---- small helpers ---- */

/** Returns (background, foreground) for a note colour index. */
@Composable
fun noteColors(idx: Int): Pair<Color, Color> =
    if (idx == 0) MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
    else Color(NoteColors[idx]) to Color(0xFF202124)

@Composable
fun clearFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

/** A future preset time at a given hour of day. */
fun preset(hour: Int, addDays: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_MONTH, addDays)
    set(Calendar.HOUR_OF_DAY, hour)
    set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Chains the platform date + time pickers and reports the chosen instant. */
fun pickDateTime(ctx: Context, initial: Long, onPicked: (Long) -> Unit) {
    val c = Calendar.getInstance().apply {
        timeInMillis = if (initial > 0L) initial else System.currentTimeMillis()
    }
    DatePickerDialog(
        ctx,
        { _, y, m, d ->
            c.set(Calendar.YEAR, y); c.set(Calendar.MONTH, m); c.set(Calendar.DAY_OF_MONTH, d)
            TimePickerDialog(
                ctx,
                { _, h, min ->
                    c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, min)
                    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
                    onPicked(c.timeInMillis)
                },
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true,
            ).show()
        },
        c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH),
    ).show()
}
