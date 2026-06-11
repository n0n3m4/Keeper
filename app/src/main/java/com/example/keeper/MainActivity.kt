@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalSharedTransitionApi::class,
)

package com.example.keeper

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.keeper.theme.KeeperTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
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

// Fixed recurrence codes for the Repeat dropdown, in display order. Custom
// intervals use the "EVERY:<n>:<unit>" code (see Notifier.repeatStep) and aren't
// listed here. Labels are resolved per-locale by repeatLabel().
val RepeatCodes = listOf("NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY")

private fun fmt(ms: Long) = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ms))

/** Human label for any repeat code, including custom "EVERY:<n>:<unit>".
 *  Localised, so it takes a Context for string/plural lookup. */
fun repeatLabel(ctx: Context, code: String): String = when {
    code == "DAILY" -> ctx.getString(R.string.daily)
    code == "WEEKLY" -> ctx.getString(R.string.weekly)
    code == "MONTHLY" -> ctx.getString(R.string.monthly)
    code == "YEARLY" -> ctx.getString(R.string.yearly)
    code.startsWith("EVERY:") -> runCatching {
        val (_, n, unit) = code.split(":")
        val count = n.toInt()
        val plural = when (unit) {
            "DAY" -> R.plurals.every_days
            "WEEK" -> R.plurals.every_weeks
            "MONTH" -> R.plurals.every_months
            else -> R.plurals.every_years
        }
        ctx.resources.getQuantityString(plural, count, count)
    }.getOrDefault(ctx.getString(R.string.does_not_repeat))
    else -> ctx.getString(R.string.does_not_repeat)   // NONE
}

/** A reminder is "spent" — and shown dimmed — once a one-time reminder has
 *  fired or its time has passed. Repeating reminders are always live. */
fun reminderInactive(at: Long, repeat: String, fired: Boolean): Boolean =
    repeat == "NONE" && (fired || at <= System.currentTimeMillis())

/** Which drawer view is showing. One object drives the whole note query.
 *  [name] is a stable internal key for the three built-ins ("Notes" /
 *  "Reminders" / "Archive") and the label's own name for a label view; the
 *  shown title is localised by [filterTitle]. */
data class Filter(
    val name: String,
    val archived: Boolean = false,
    val reminders: Boolean = false,
    val labelId: Long? = null,
)

/** Localised app-bar title for a filter. Built-ins map to their translation;
 *  a label view shows the (user-entered) label name as-is. */
@Composable
fun filterTitle(f: Filter): String = when (f.name) {
    "Notes" -> stringResource(R.string.notes)
    "Reminders" -> stringResource(R.string.reminders)
    "Archive" -> stringResource(R.string.archive)
    else -> f.name
}

/*
 * MainActivity hosts the entire app. There is no ViewModel and no navigation
 * graph: a single `editing` state flips between the note grid and the editor,
 * and the SQLite database is always re-read after a change. Four files, read
 * top to bottom.
 */
class MainActivity : ComponentActivity() {
    // Note id to open straight into the editor (set when launched from a notification).
    private val openState = mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Db.init(this)
        Notifier.rescheduleAll(this)              // safety net on every launch
        AutoBackup.sync(this)                     // (re)register the periodic backup job
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
    var showSettings by remember { mutableStateOf(false) }

    // Multi-select (long-press a tile). Non-empty = selection mode: the app
    // bar swaps to ✕ / count / bulk actions, and taps toggle instead of open.
    val selected = remember { mutableStateListOf<Long>() }
    // A long-press starts both selection and a reorder pick-up. The Card's
    // clickable still fires a tap on release, which would immediately toggle the
    // just-selected note back off; this flag swallows that one trailing click.
    var longPressActive by remember { mutableStateOf(false) }
    var showBulkColors by remember { mutableStateOf(false) }
    var showBulkLabels by remember { mutableStateOf(false) }
    var showBulkDelete by remember { mutableStateOf(false) }

    fun reload() {
        labels = Db.labels()
        notes = Db.notes(filter.archived, filter.reminders, filter.labelId, search)
    }
    LaunchedEffect(filter, search) { reload() }

    // Re-read when a link preview finishes fetching in the background, so a
    // chip appears on its tile without the user having to reopen the note.
    LaunchedEffect(LinkPreviews.version.intValue) { reload() }

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

    // Drag-to-reorder state for the note grid. onMove rearranges the in-memory
    // list for a smooth animation; the new order is persisted on drag release.
    // Hoisted above the open/close transition so the scroll position survives a
    // round-trip into the editor and back.
    val gridState = rememberLazyStaggeredGridState()
    val reorderState = rememberReorderableLazyStaggeredGridState(gridState) { from, to ->
        notes = notes.toMutableList().apply { add(to.index, removeAt(from.index)) }
        // A reorder of a lone tentative selection means the user wanted to move
        // it, not select it (Keep behavior) — leave selection mode.
        if (selected.size <= 1) selected.clear()
    }

    // The tapped tile morphs into the editor (Keep-style shared-element
    // container transform) and shrinks back on close. Grid and editor must
    // co-exist in the composition for the morph, so they are the two states of
    // one AnimatedContent rather than an early return. The matching
    // sharedBounds key ("note-<id>") on the tile and the editor drives the morph;
    // the fade here is just the backdrop.
    SharedTransitionLayout {
    AnimatedContent(
        targetState = editing,
        contentKey = { it?.id },
        label = "editor",
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
    ) { current ->
    if (current != null) {
        NoteEditor(
            note = current,
            labels = labels,
            onAddLabel = { name -> Db.addLabel(name); labels = Db.labels() },
            onClose = { editing = null; reload() },
            sharedScope = this@SharedTransitionLayout,
            animScope = this@AnimatedContent,
        )
    } else {
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = selected.isEmpty(),   // no drawer swipe while selecting
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(24.dp),
                )
                fun go(f: Filter) {
                    filter = f; search = ""; searching = false; selected.clear()
                    scope.launch { drawer.close() }
                }
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.notes)) }, selected = filter.name == "Notes",
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    onClick = { go(Filter("Notes")) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.reminders)) }, selected = filter.name == "Reminders",
                    icon = { Icon(Icons.Default.Notifications, null) },
                    onClick = { go(Filter("Reminders", reminders = true)) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.archive)) }, selected = filter.name == "Archive",
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
                    label = { Text(stringResource(R.string.edit_labels)) }, selected = false,
                    onClick = { showLabelManager = true; scope.launch { drawer.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(Modifier.padding(12.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.export_db)) }, selected = false,
                    onClick = { exporter.launch("keeper-backup.db"); scope.launch { drawer.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.import_db)) }, selected = false,
                    onClick = { importer.launch(arrayOf("*/*")); scope.launch { drawer.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(Modifier.padding(12.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.settings)) }, selected = false,
                    icon = { Icon(Icons.Default.Settings, null) },
                    onClick = { showSettings = true; scope.launch { drawer.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.reminder_reliability)) }, selected = false,
                    icon = { Icon(Icons.Default.Notifications, null) },
                    onClick = { showReliabilityHelp = true; scope.launch { drawer.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
        // In selection mode, Back drops the selection instead of leaving.
        BackHandler(enabled = selected.isNotEmpty()) { selected.clear() }
        Scaffold(
            topBar = {
                // Keep the count stable while the selection bar fades out
                // (`selected` is already empty by then) so it never flashes "0".
                var selCount by remember { mutableStateOf(0) }
                if (selected.isNotEmpty()) selCount = selected.size
                AnimatedContent(
                    targetState = selected.isNotEmpty(),
                    label = "topBar",
                    transitionSpec = {
                        // Keep-style: the selection toolbar pulls down over the
                        // normal bar (and retracts) — a vertical mirror of the
                        // search field's horizontal swap.
                        (fadeIn(tween(220)) + expandVertically(tween(220), expandFrom = Alignment.Top)) togetherWith
                            (fadeOut(tween(140)) + shrinkVertically(tween(140), shrinkTowards = Alignment.Top))
                    },
                ) { selecting ->
                if (selecting) {
                    // Keep-style selection bar: ✕, count, bulk actions. No
                    // reminder action — a shared time makes no sense for a group.
                    val allPinned = selected.isNotEmpty() && notes.filter { it.id in selected }.all { it.pinned }
                    var bulkMenu by remember { mutableStateOf(false) }
                    fun done() { reload(); selected.clear() }
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { selected.clear() }) {
                                Icon(Icons.Default.Close, stringResource(R.string.cancel))
                            }
                        },
                        title = { Text("$selCount") },
                        actions = {
                            // Like Keep: pin all unless every one is already pinned.
                            IconButton(onClick = { Db.setPinned(selected.toList(), !allPinned); done() }) {
                                val tint = MaterialTheme.colorScheme.onSurface
                                Icon(
                                    Icons.Default.Star, stringResource(R.string.pin),
                                    tint = if (allPinned) tint else tint.copy(alpha = 0.4f),
                                )
                            }
                            IconButton(onClick = { showBulkColors = true }) {
                                Icon(PaletteIcon, stringResource(R.string.color))
                            }
                            IconButton(onClick = { showBulkLabels = true }) {
                                Icon(Icons.AutoMirrored.Filled.List, stringResource(R.string.labels))
                            }
                            IconButton(onClick = { bulkMenu = true }) {
                                Icon(Icons.Default.MoreVert, stringResource(R.string.more))
                            }
                            DropdownMenu(expanded = bulkMenu, onDismissRequest = { bulkMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (filter.archived) R.string.unarchive else R.string.archive)) },
                                    onClick = {
                                        bulkMenu = false
                                        Db.setArchived(selected.toList(), !filter.archived)
                                        done()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    onClick = { bulkMenu = false; showBulkDelete = true },
                                )
                            }
                        },
                    )
                } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(Icons.Default.Menu, stringResource(R.string.menu))
                        }
                    },
                    title = {
                        AnimatedContent(
                            targetState = searching,
                            label = "search",
                            transitionSpec = {
                                (fadeIn(tween(200)) + expandHorizontally(expandFrom = Alignment.End)) togetherWith
                                    (fadeOut(tween(120)) + shrinkHorizontally(shrinkTowards = Alignment.End))
                            },
                        ) { on ->
                            if (on) {
                                TextField(
                                    value = search, onValueChange = { search = it },
                                    placeholder = { Text(stringResource(R.string.search_notes)) },
                                    singleLine = true, colors = clearFieldColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text(filterTitle(filter))
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            searching = !searching
                            if (!searching) search = ""
                        }) {
                            AnimatedContent(searching, label = "searchIcon") { on ->
                                Icon(
                                    if (on) Icons.Default.Close else Icons.Default.Search,
                                    stringResource(R.string.search),
                                )
                            }
                        }
                    },
                )
                } // else (normal bar)
                } // AnimatedContent
            },
            floatingActionButton = {
                // No "create" button in Archive — a new note is never archived —
                // and none while selecting, like Keep.
                if (!filter.archived && selected.isEmpty()) {
                    FloatingActionButton(onClick = { editing = Note() }) {
                        Icon(Icons.Default.Add, stringResource(R.string.new_note))
                    }
                }
            },
        ) { pad ->
            if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                    Text(
                        if (search.isNotBlank()) stringResource(R.string.no_matching_notes)
                        else stringResource(R.string.nothing_here_yet),
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
                                // Long-press = select (and pick up for reorder),
                                // exactly like Keep: releasing without moving
                                // just leaves the note selected.
                                Modifier.longPressDraggableHandle(
                                    onDragStarted = {
                                        longPressActive = true
                                        if (note.id !in selected) selected.add(note.id)
                                    },
                                    onDragStopped = {
                                        longPressActive = false
                                        Db.reorder(notes.map { it.id })
                                    },
                                ),
                                sharedScope = this@SharedTransitionLayout,
                                animScope = this@AnimatedContent,
                                selected = note.id in selected,
                                selecting = selected.isNotEmpty(),
                            ) {
                                if (longPressActive) {
                                    // Trailing tap from the long-press that just
                                    // selected this note — keep it selected.
                                    longPressActive = false
                                } else if (selected.isNotEmpty()) {
                                    if (!selected.remove(note.id)) selected.add(note.id)
                                } else editing = note
                            }
                        }
                    }
                }
            }
        }
    }
    } // else (grid screen)
    } // AnimatedContent
    } // SharedTransitionLayout

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

    if (showSettings) {
        SettingsDialog(onDismiss = { showSettings = false })
    }

    /* -- bulk (multi-select) dialogs -- */

    if (showBulkColors) {
        AlertDialog(
            onDismissRequest = { showBulkColors = false },
            title = { Text(stringResource(R.string.color)) },
            text = {
                // Highlight the shared colour, or nothing when they differ.
                val common = notes.filter { it.id in selected }
                    .map { it.color }.distinct().singleOrNull() ?: -1
                ColorBar(common) { idx ->
                    Db.setColor(selected.toList(), idx)
                    showBulkColors = false
                    reload(); selected.clear()
                }
            },
            confirmButton = {
                TextButton(onClick = { showBulkColors = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showBulkLabels) {
        // A box is ticked only when every selected note carries the label;
        // toggling then adds it to all (or removes it from all).
        val common = labels.filter { l ->
            notes.filter { it.id in selected }.all { l.id in it.labelIds }
        }.map { it.id }
        LabelPickerDialog(
            labels = labels,
            selected = common,
            onToggle = { id -> Db.setLabel(selected.toList(), id, id !in common); reload() },
            onAddLabel = { name -> Db.addLabel(name); labels = Db.labels() },
            onDismiss = { showBulkLabels = false; reload(); selected.clear() },
        )
    }

    if (showBulkDelete) {
        AlertDialog(
            onDismissRequest = { showBulkDelete = false },
            text = {
                Text(pluralStringResource(R.plurals.delete_notes_question, selected.size, selected.size))
            },
            confirmButton = {
                TextButton(onClick = {
                    showBulkDelete = false
                    selected.forEach { Db.delete(it); Notifier.cancel(ctx, it) }
                    reload(); selected.clear()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/* ---- note grid tile ---- */

@Composable
fun NoteTile(
    note: Note,
    allLabels: List<Label>,
    modifier: Modifier,
    sharedScope: SharedTransitionScope,
    animScope: AnimatedVisibilityScope,
    selected: Boolean = false,
    selecting: Boolean = false,
    onClick: () -> Unit,
) {
    val (bg, fg) = noteColors(note.color)
    // The matching key on the editor's Scaffold makes the tile expand into the
    // editor (and shrink back) — the Keep-style container transform.
    val cardModifier = with(sharedScope) {
        modifier
            .sharedBounds(
                rememberSharedContentState(key = "note-${note.id}"),
                animatedVisibilityScope = animScope,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            )
            .fillMaxWidth()
    }
    Card(
        modifier = cardModifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
        // Selection draws Keep's thick dark outline over the usual hairline.
        border = if (selected) androidx.compose.foundation.BorderStroke(
            3.dp, MaterialTheme.colorScheme.primary,
        ) else if (note.color == 0) androidx.compose.foundation.BorderStroke(
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
                if (note.pinned) Icon(Icons.Default.Star, stringResource(R.string.pinned), Modifier.size(18.dp))
            }
            // Keep gives a tile title airier breathing room before its text —
            // roughly twice the title's x-height (16sp · ~0.53 ≈ 8.5sp, ×2 ≈
            // 17sp, of which the line boxes already supply ~7sp).
            if (note.title.isNotBlank()) Spacer(Modifier.height(10.dp))
            if (note.checklist) {
                note.items.take(8).forEach { item ->
                    Text(
                        (if (item.checked) "☑ " else "☐ ") + item.text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (note.items.size > 8)
                    Text(
                        stringResource(R.string.more_items, note.items.size - 8),
                        style = MaterialTheme.typography.bodySmall,
                    )
            } else if (note.body.isNotBlank()) {
                Text(
                    // While selecting, URL taps must not open the browser — the
                    // whole tile is one select-toggle target. Dropping the link
                    // annotations on the long-press recomposition also swallows
                    // the release of the very gesture that started selecting.
                    linkified(note.body, clickable = !selecting),
                    maxLines = 12, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (note.links.any { it.status == "OK" }) {
                Spacer(Modifier.height(6.dp))
                LinkChips(note.links, enabled = !selecting)
            }
            if (note.reminderAt > 0L) {
                Spacer(Modifier.height(2.dp))
                ReminderChip(
                    note.reminderAt, note.reminderRepeat,
                    dimmed = reminderInactive(
                        note.reminderAt, note.reminderRepeat, note.reminderFired,
                    ),
                )
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

/** The reminder chip shown on a note tile and in the editor — larger than a
 *  label chip, and dimmed with a strikethrough once the reminder is spent. */
@Composable
private fun ReminderChip(
    at: Long,
    repeat: String,
    dimmed: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val content =
        if (dimmed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.onSurface
    val bg =
        if (dimmed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        else Color.Black.copy(alpha = 0.08f)
    val label = fmt(at) + if (repeat == "NONE") "" else " · ${repeatLabel(ctx, repeat)}"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(Icons.Default.Notifications, null, Modifier.size(18.dp), tint = content)
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            textDecoration = if (dimmed) TextDecoration.LineThrough else null,
        )
        if (repeat != "NONE") {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp), tint = content)
        }
    }
}

/* ---- link previews ---- */

/** Builds an AnnotatedString that underlines every http(s) URL in `text`.
 *  When `clickable`, each URL also carries a LinkAnnotation so a tap opens
 *  the browser — used for the read-only tile body, not the editor field. */
private fun linkified(text: String, clickable: Boolean): AnnotatedString = buildAnnotatedString {
    append(text)
    val underline = SpanStyle(textDecoration = TextDecoration.Underline)
    LinkPreviews.urlRanges(text).forEach { (start, end) ->
        addStyle(underline, start, end)
        if (clickable) addLink(LinkAnnotation.Url(text.substring(start, end)), start, end)
    }
}

/** Underlines URLs inside the editor's body field. Length is unchanged, so the
 *  offset mapping is the identity. */
private val LinkUnderline = VisualTransformation { original ->
    TransformedText(linkified(original.text, clickable = false), OffsetMapping.Identity)
}

/** The Keep-style link preview chip: favicon, page title, domain. Tapping it
 *  opens the URL. Only OK previews are passed here. */
@Composable
private fun LinkChip(link: Link, enabled: Boolean = true) {
    val ctx = LocalContext.current
    val favicon = remember(link.favicon) {
        link.favicon?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (enabled) Modifier.clickable {
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                    }
                } else Modifier
            )
            .background(Color.Black.copy(alpha = 0.06f))
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            if (favicon != null) Image(favicon, null, Modifier.size(24.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                link.title.ifBlank { link.url },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                link.domain,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Stack of preview chips. A failed (or still-loading) link draws no chip. */
@Composable
private fun LinkChips(links: List<Link>, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val ready = links.filter { it.status == "OK" }
    if (ready.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ready.forEach { LinkChip(it, enabled) }
    }
}

/* ---- note editor ---- */

@Composable
fun NoteEditor(
    note: Note,
    labels: List<Label>,
    onAddLabel: (String) -> Unit,
    onClose: () -> Unit,
    sharedScope: SharedTransitionScope,
    animScope: AnimatedVisibilityScope,
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
    // Mirrors note.reminderFired as Compose state so the chip un-dims the moment
    // the reminder is rescheduled, before persist()/reload() rebuild the note.
    var reminderFired by remember { mutableStateOf(note.reminderFired) }
    val items = remember { note.items.toMutableStateList() }
    val labelIds = remember { note.labelIds.toMutableStateList() }
    var links by remember { mutableStateOf(Db.links(note.id)) }

    var menu by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    var showLabels by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }

    fun persist() {
        val newRepeat = if (reminderAt > 0L) reminderRepeat else "NONE"
        // A changed reminder time/recurrence is a fresh reminder: let it fire again.
        if (note.reminderAt != reminderAt || note.reminderRepeat != newRepeat) {
            note.reminderFired = false
            note.reminderSnoozeAt = 0L         // a fresh reminder drops any pending snooze
        }
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

    fun saveAndClose() {
        persist()
        // Closing the note is the second fetch trigger (besides the 3 s
        // debounce); it also reconciles previews when the body emptied.
        if (note.id != 0L && !Db.isBlank(note)) LinkPreviews.refresh(note.id, body)
        onClose()
    }
    BackHandler { saveAndClose() }

    // Pick up previews as their background fetches finish.
    LaunchedEffect(LinkPreviews.version.intValue) {
        if (note.id != 0L) links = Db.links(note.id)
    }
    // Debounced fetch: 3 s after the body text stops changing. The effect is
    // restarted on every keystroke, so the fetch only runs once typing settles.
    LaunchedEffect(body) {
        if (body.isBlank()) return@LaunchedEffect
        delay(3000)
        persist()                       // gives a brand-new note its id first
        LinkPreviews.refresh(note.id, body)
    }

    val (bg, fg) = noteColors(color)
    // Same key as the source tile: the editor grows out of / collapses back
    // into the tile that opened it. A blank note from the FAB has id 0, which
    // matches no tile, so it simply fades in via the AnimatedContent backdrop.
    val scaffoldModifier = with(sharedScope) {
        Modifier.sharedBounds(
            rememberSharedContentState(key = "note-${note.id}"),
            animatedVisibilityScope = animScope,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(12.dp)),
        )
    }
    Scaffold(
        modifier = scaffoldModifier,
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                title = {},
                actions = {
                    IconButton(onClick = { pinned = !pinned }) {
                        Icon(
                            Icons.Default.Star, stringResource(R.string.pin),
                            tint = if (pinned) fg else fg.copy(alpha = 0.4f),
                        )
                    }
                    IconButton(onClick = { showReminder = true }) {
                        Icon(Icons.Default.Notifications, stringResource(R.string.reminder))
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (checklist) R.string.switch_to_text else R.string.switch_to_checklist)) },
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
                            text = { Text(stringResource(R.string.labels)) },
                            onClick = { showLabels = true; menu = false },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (archived) R.string.unarchive else R.string.archive)) },
                            onClick = { archived = !archived; menu = false },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
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
        // Like Keep, the palette lives in a bottom bar. The bar is inset past
        // the system navigation buttons so neither it nor the colour strip is
        // ever drawn behind them.
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                AnimatedVisibility(
                    visible = showColors,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) { ColorBar(color) { color = it } }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showColors = !showColors }) {
                        Icon(
                            PaletteIcon, stringResource(R.string.color),
                            tint = if (showColors) fg else fg.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        },
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
                placeholder = { Text(stringResource(R.string.title_hint)) },
                colors = clearFieldColors(), modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.titleLarge,
            )
            // The title and the body/checklist are both TextFields, whose default
            // ~16dp content padding stacks into too wide a gap (3+× the title's
            // x-height). The simple M3 TextField overload can't take a smaller
            // contentPadding, so nudge the content up by 8dp — bringing the
            // title↔text distance to ~2.5× the 24sp title's x-height, like Keep.
            val tightenTitleGap = Modifier.offset(y = (-8).dp)
            if (checklist) {
                Column(tightenTitleGap) {
                    items.forEachIndexed { i, item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = item.checked,
                                onCheckedChange = { items[i] = item.copy(checked = it) },
                            )
                            TextField(
                                value = item.text,
                                onValueChange = { items[i] = item.copy(text = it) },
                                placeholder = { Text(stringResource(R.string.list_item_hint)) },
                                colors = clearFieldColors(), singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { items.removeAt(i) }) {
                                Icon(Icons.Default.Close, stringResource(R.string.remove_item))
                            }
                        }
                    }
                    TextButton(
                        onClick = { items.add(Item()) },
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_item))
                    }
                }
            } else {
                TextField(
                    value = body, onValueChange = { body = it },
                    placeholder = { Text(stringResource(R.string.note_hint)) },
                    colors = clearFieldColors(),
                    visualTransformation = LinkUnderline,
                    modifier = tightenTitleGap.fillMaxWidth().heightIn(min = 120.dp),
                )
            }

            // Link preview chips for the URLs in the body, like Keep.
            if (!checklist) {
                LinkChips(
                    links,
                    Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                )
            }

            // Reminder + labels live below the title and the note text, like
            // Keep. The reminder chip is tappable — it opens the same dialog.
            if (reminderAt > 0L) {
                Row(Modifier.padding(start = 16.dp, top = 2.dp, bottom = 4.dp)) {
                    ReminderChip(
                        reminderAt, reminderRepeat,
                        dimmed = reminderInactive(reminderAt, reminderRepeat, reminderFired),
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
            onSet = { at, rep ->
                if (at != reminderAt || rep != reminderRepeat) reminderFired = false
                reminderAt = at; reminderRepeat = rep; showReminder = false
            },
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

/** The Material "palette" icon. It lives in material-icons-extended, which
 *  isn't a dependency, so the vector is defined inline here. */
private val PaletteIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Palette", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.74-.39" +
                    "-1.01-.23-.26-.38-.61-.38-.99 0-.83.67-1.5 1.5-1.5H16c2.76 0 5-2.24 5-5 0" +
                    "-4.42-4.03-8-9-8zm-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 9 6.5 9 8 9.67 8 10.5" +
                    " 7.33 12 6.5 12zm3-4C8.67 8 8 7.33 8 6.5S8.67 5 9.5 5s1.5.67 1.5 1.5S10.33" +
                    " 8 9.5 8zm5 0c-.83 0-1.5-.67-1.5-1.5S13.67 5 14.5 5s1.5.67 1.5 1.5S15.33 8" +
                    " 14.5 8zm3 4c-.83 0-1.5-.67-1.5-1.5S16.67 9 17.5 9s1.5.67 1.5 1.5-.67 1.5" +
                    "-1.5 1.5z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()
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
    var time by remember { mutableLongStateOf(if (at > 0L) at else preset(Prefs.evening(ctx).first, 0)) }
    var rep by remember { mutableStateOf(repeat) }
    var showCustom by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val weekday = remember { SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder)) },
        text = {
            // Date / time / repeat are dropdowns of common presets, each with a
            // custom escape hatch — the everyday case is one or two taps.
            Column {
                DropdownField(stringResource(R.string.date), dateFmt.format(Date(time))) { close ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.today)) },
                        onClick = { time = withDate(time, 0); close() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tomorrow)) },
                        onClick = { time = withDate(time, 1); close() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.next_weekday, weekday)) },
                        onClick = { time = withDate(time, 7); close() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pick_date)) },
                        onClick = { close(); pickDate(ctx, time) { time = it } },
                    )
                }
                DropdownField(stringResource(R.string.time), timeFmt.format(Date(time))) { close ->
                    val (mh, mm) = Prefs.morning(ctx)
                    val (ah, am) = Prefs.afternoon(ctx)
                    val (eh, em) = Prefs.evening(ctx)
                    DropdownMenuItem(
                        text = { Text("${stringResource(R.string.morning_preset)} · ${timeFmt.format(Date(withTime(time, mh, mm)))}") },
                        onClick = { time = withTime(time, mh, mm); close() },
                    )
                    DropdownMenuItem(
                        text = { Text("${stringResource(R.string.afternoon_preset)} · ${timeFmt.format(Date(withTime(time, ah, am)))}") },
                        onClick = { time = withTime(time, ah, am); close() },
                    )
                    DropdownMenuItem(
                        text = { Text("${stringResource(R.string.evening_preset)} · ${timeFmt.format(Date(withTime(time, eh, em)))}") },
                        onClick = { time = withTime(time, eh, em); close() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pick_time)) },
                        onClick = { close(); pickTime(ctx, time) { time = it } },
                    )
                }
                DropdownField(stringResource(R.string.repeat), repeatLabel(ctx, rep)) { close ->
                    RepeatCodes.forEach { code ->
                        DropdownMenuItem(
                            text = { Text(repeatLabel(ctx, code)) },
                            onClick = { rep = code; close() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.custom_interval_ellipsis)) },
                        onClick = { close(); showCustom = true },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSet(time, rep) }) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (at > 0L) TextButton(onClick = onRemove) { Text(stringResource(R.string.remove)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

    if (showCustom) {
        CustomIntervalDialog(
            initial = rep,
            onSet = { rep = it; showCustom = false },
            onDismiss = { showCustom = false },
        )
    }
}

/** A labelled field that opens a dropdown of choices when tapped. The [menu]
 *  lambda fills it with DropdownMenuItems and gets a [close] callback. */
@Composable
fun DropdownField(
    label: String,
    value: String,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { open = true }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ArrowDropDown, stringResource(R.string.open))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                menu { open = false }
            }
        }
        HorizontalDivider()
    }
}

/** Builds an "EVERY:<n>:<unit>" repeat code from a count + unit. */
@Composable
fun CustomIntervalDialog(
    initial: String,
    onSet: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val units = listOf(
        "DAY" to R.string.unit_days, "WEEK" to R.string.unit_weeks,
        "MONTH" to R.string.unit_months, "YEAR" to R.string.unit_years,
    )
    // Re-editing an existing custom interval keeps its current value.
    val parsed = remember {
        initial.takeIf { it.startsWith("EVERY:") }?.split(":")?.takeIf { it.size == 3 }
    }
    var count by remember { mutableStateOf(parsed?.get(1) ?: "2") }
    var unit by remember { mutableStateOf(parsed?.get(2)?.takeIf { u -> units.any { it.first == u } } ?: "WEEK") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_interval)) },
        text = {
            Column {
                Text(stringResource(R.string.repeat_every), style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = count,
                        onValueChange = { v -> count = v.filter { it.isDigit() }.take(3) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        DropdownField(
                            stringResource(R.string.unit),
                            stringResource(units.first { it.first == unit }.second),
                        ) { close ->
                            units.forEach { (code, nameRes) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(nameRes)) },
                                    onClick = { unit = code; close() },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // A zero/blank count would make the scheduler loop forever.
                val n = (count.toIntOrNull() ?: 1).coerceAtLeast(1)
                onSet("EVERY:$n:$unit")
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
        title = { Text(stringResource(R.string.labels)) },
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
                        placeholder = { Text(stringResource(R.string.new_label)) },
                        singleLine = true, colors = clearFieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { if (newName.isNotBlank()) { onAddLabel(newName.trim()); newName = "" } },
                    ) { Icon(Icons.Default.Add, stringResource(R.string.add_label)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
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
        title = { Text(stringResource(R.string.edit_labels)) },
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
                        }) { Icon(Icons.Default.Delete, stringResource(R.string.delete_label)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = newName, onValueChange = { newName = it },
                        placeholder = { Text(stringResource(R.string.new_label)) },
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
                    }) { Icon(Icons.Default.Add, stringResource(R.string.add_label)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                rows.forEach { (id, name) -> Db.renameLabel(id, name.value) }
                onChanged(); onDismiss()
            }) { Text(stringResource(R.string.done)) }
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
        title = { Text(stringResource(R.string.reminder_reliability)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.reliability_intro))
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
                ) { Text(stringResource(R.string.reliability_battery)) }
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
                ) { Text(stringResource(R.string.reliability_autostart)) }
                OutlinedButton(
                    onClick = {
                        open(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.reliability_notifications)) }
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
                ) { Text(stringResource(R.string.reliability_exact)) }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.reliability_outro))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

/** Lets the user retime the three reminder presets (Morning/Afternoon/Evening).
 *  Each tap opens the platform time picker; the choice is saved to [Prefs] and
 *  picked up the next time the reminder dialog is opened. */
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var morning by remember { mutableStateOf(Prefs.morning(ctx)) }
    var afternoon by remember { mutableStateOf(Prefs.afternoon(ctx)) }
    var evening by remember { mutableStateOf(Prefs.evening(ctx)) }

    // Backup state. AutoBackup.version bumps after each run so the status line
    // below re-reads Prefs (which a background worker may have written).
    var backupOn by remember { mutableStateOf(Prefs.backupEnabled(ctx)) }
    var folderUri by remember { mutableStateOf(Prefs.backupTreeUri(ctx)) }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            ctx.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            Prefs.setBackupTreeUri(ctx, it.toString())
            folderUri = it.toString()
            AutoBackup.sync(ctx)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.preset_times), style = MaterialTheme.typography.titleSmall)
                PresetRow(stringResource(R.string.morning_preset), "morning", morning) { morning = it }
                PresetRow(stringResource(R.string.afternoon_preset), "afternoon", afternoon) { afternoon = it }
                PresetRow(stringResource(R.string.evening_preset), "evening", evening) { evening = it }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleSmall)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.backup_enable), Modifier.weight(1f))
                    Switch(checked = backupOn, onCheckedChange = {
                        backupOn = it; Prefs.setBackupEnabled(ctx, it); AutoBackup.sync(ctx)
                    })
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { folderPicker.launch(null) }
                        .padding(vertical = 14.dp),
                ) {
                    Text(stringResource(R.string.backup_folder), Modifier.weight(1f))
                    val label = remember(folderUri) { AutoBackup.folderLabel(ctx) }
                    Text(label ?: stringResource(R.string.backup_folder_none))
                }

                val keep = Prefs.backupKeep(ctx)
                Text(
                    pluralStringResource(R.plurals.backup_keep_summary, keep, keep),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Re-read status whenever a run completes (worker or "Back up now").
                val v = AutoBackup.version.intValue
                val err = remember(v) { Prefs.backupError(ctx) }
                val last = remember(v) { Prefs.lastBackup(ctx) }
                val dateFmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }
                val status = when {
                    err != 0 -> stringResource(err)
                    last > 0L -> stringResource(R.string.backup_last, dateFmt.format(Date(last)))
                    else -> stringResource(R.string.backup_last, stringResource(R.string.backup_never))
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (err != 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                OutlinedButton(
                    onClick = { scope.launch(Dispatchers.IO) { AutoBackup.runOnce(ctx) } },
                    enabled = folderUri != null,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(R.string.backup_now)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

/** One row of [SettingsDialog]: a preset [label] and its current time; tapping
 *  picks a new time, saves it under [key], and reports it back via [onChange]. */
@Composable
private fun PresetRow(label: String, key: String, value: Pair<Int, Int>, onChange: (Pair<Int, Int>) -> Unit) {
    val ctx = LocalContext.current
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val ms = withTime(System.currentTimeMillis(), value.first, value.second)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                pickTime(ctx, ms) {
                    val c = Calendar.getInstance().apply { timeInMillis = it }
                    val hm = c.get(Calendar.HOUR_OF_DAY) to c.get(Calendar.MINUTE)
                    Prefs.set(ctx, key, hm.first, hm.second); onChange(hm)
                }
            }
            .padding(vertical = 14.dp),
    ) {
        Text(label, Modifier.weight(1f))
        Text(timeFmt.format(Date(ms)))
    }
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

/** App preferences (device-local; not part of the DB export/import). Currently
 *  just the three configurable reminder time presets, each an (hour, minute). */
object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("keeper_settings", Context.MODE_PRIVATE)
    fun morning(ctx: Context) = p(ctx).getInt("morning_h", 8) to p(ctx).getInt("morning_m", 0)
    fun afternoon(ctx: Context) = p(ctx).getInt("afternoon_h", 13) to p(ctx).getInt("afternoon_m", 0)
    fun evening(ctx: Context) = p(ctx).getInt("evening_h", 18) to p(ctx).getInt("evening_m", 0)
    fun set(ctx: Context, key: String, h: Int, m: Int) =
        p(ctx).edit().putInt("${key}_h", h).putInt("${key}_m", m).apply()

    /* ---- automatic backup (see AutoBackup) ---- */

    /** Persisted SAF tree URI for the backup folder, or null if not chosen. */
    fun backupTreeUri(ctx: Context): String? = p(ctx).getString("backup_uri", null)
    fun setBackupTreeUri(ctx: Context, uri: String?) =
        p(ctx).edit().putString("backup_uri", uri).apply()

    fun backupEnabled(ctx: Context) = p(ctx).getBoolean("backup_enabled", false)
    fun setBackupEnabled(ctx: Context, on: Boolean) =
        p(ctx).edit().putBoolean("backup_enabled", on).apply()

    /** How many timestamped backups to retain in the folder. */
    fun backupKeep(ctx: Context) = p(ctx).getInt("backup_keep", 5)

    /** Epoch ms of the last successful backup; 0 = never. */
    fun lastBackup(ctx: Context) = p(ctx).getLong("backup_last", 0L)
    /** Records a success: stamps the time and clears any pending error. */
    fun setLastBackup(ctx: Context, ms: Long) =
        p(ctx).edit().putLong("backup_last", ms).putInt("backup_error", 0).apply()

    /** String-resource id of the last backup error (0 = none), so the message
     *  stays localized whenever it is later shown in the dialog. */
    fun backupError(ctx: Context) = p(ctx).getInt("backup_error", 0)
    fun setBackupError(ctx: Context, msgRes: Int) =
        p(ctx).edit().putInt("backup_error", msgRes).apply()
}

/** A future preset time at a given hour of day. */
fun preset(hour: Int, addDays: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_MONTH, addDays)
    set(Calendar.HOUR_OF_DAY, hour)
    set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Today (+[addDaysFromToday]) at the time-of-day carried by [ms]. */
fun withDate(ms: Long, addDaysFromToday: Int): Long {
    val src = Calendar.getInstance().apply { timeInMillis = ms }
    return Calendar.getInstance().apply {
        add(Calendar.DAY_OF_MONTH, addDaysFromToday)
        set(Calendar.HOUR_OF_DAY, src.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, src.get(Calendar.MINUTE))
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** The date of [ms] at [hour]:[minute]. */
fun withTime(ms: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** Platform date picker — applies only Y/M/D to [initial], keeping its time. */
fun pickDate(ctx: Context, initial: Long, onPicked: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(
        ctx,
        { _, y, m, d ->
            c.set(Calendar.YEAR, y); c.set(Calendar.MONTH, m); c.set(Calendar.DAY_OF_MONTH, d)
            onPicked(c.timeInMillis)
        },
        c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH),
    ).show()
}

/** Platform time picker — applies only H/M to [initial], keeping its date. */
fun pickTime(ctx: Context, initial: Long, onPicked: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = initial }
    TimePickerDialog(
        ctx,
        { _, h, min ->
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, min)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            onPicked(c.timeInMillis)
        },
        c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true,
    ).show()
}
