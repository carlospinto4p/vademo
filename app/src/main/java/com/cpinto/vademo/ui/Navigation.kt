package com.cpinto.vademo.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cpinto.vademo.ui.browse.BrowseScreen
import com.cpinto.vademo.ui.conflict.ConflictScreen
import com.cpinto.vademo.ui.diagram.DiagramListScreen
import com.cpinto.vademo.ui.diagram.DiagramScreen
import com.cpinto.vademo.ui.edit.EditScreen
import com.cpinto.vademo.ui.graph.GraphScreen
import com.cpinto.vademo.ui.home.HomeScreen
import com.cpinto.vademo.ui.kb.KbScreen
import com.cpinto.vademo.ui.note.NoteScreen
import com.cpinto.vademo.ui.search.SearchScreen
import com.cpinto.vademo.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val KB = "kb/{kb}?tab={tab}&name={name}"
    const val BROWSE = "browse/{kb}?path={path}"
    const val NOTE = "note/{kb}?path={path}"
    const val EDIT = "edit/{kb}?path={path}&template={template}&title={title}"
    const val CONFLICT = "conflict/{kb}"
    const val MAP_AROUND = "map/{kb}?focus={focus}"
    const val DIAGRAMS = "diagrams/{kb}"
    const val DIAGRAM = "diagram/{kb}?name={name}"
    const val SEARCH = "search?q={q}"

    /** Search, starting with [query] in the field. */
    fun search(query: String = "") = "search?q=${enc(query)}"

    /** Search in the knowledge base [kb] only, with [filters] already typed. */
    fun searchIn(kb: String, filters: String = "") = search("kb:${kb.removePrefix("kb-")} $filters")

    /** The knowledge base [kb]'s first screen, on [tab], with [diagram] as its main diagram. */
    fun kb(kb: String, tab: String, diagram: String?) = "kb/${enc(kb)}?tab=${enc(tab)}&name=${enc(diagram.orEmpty())}"

    fun browse(kb: String, path: String = "") = "browse/${enc(kb)}?path=${enc(path)}"
    /** The map round the note at [path]: it and the notes up to two links away. */
    fun mapAround(kb: String, path: String) = "map/${enc(kb)}?focus=${enc(path)}"
    fun diagrams(kb: String) = "diagrams/${enc(kb)}"
    fun diagram(kb: String, name: String) = "diagram/${enc(kb)}?name=${enc(name)}"
    fun note(kb: String, path: String) = "note/${enc(kb)}?path=${enc(path)}"
    fun edit(kb: String, path: String) = "edit/${enc(kb)}?path=${enc(path)}"

    /** A new note at [path], started from the template at [template] with [title] filled in. */
    fun newNote(kb: String, path: String, template: String, title: String) =
        "edit/${enc(kb)}?path=${enc(path)}&template=${enc(template)}&title=${enc(title)}"
    fun conflict(kb: String) = "conflict/${enc(kb)}"

    private fun enc(value: String) = Uri.encode(value)
}

private val kbArgs = listOf(
    navArgument("kb") { type = NavType.StringType },
    navArgument("path") { type = NavType.StringType; defaultValue = "" },
)

/** Screens Forward may reopen. The editor, settings and conflicts are steps, not places. */
private val PLACES = setOf(Routes.KB, Routes.BROWSE, Routes.NOTE, Routes.MAP_AROUND, Routes.DIAGRAMS, Routes.DIAGRAM, Routes.SEARCH)

/** The route that opens [entry] again, its pattern with each `{argument}` filled in. */
private fun routeOf(entry: NavBackStackEntry): String =
    Regex("""\{(\w+)\}""").replace(entry.destination.route.orEmpty()) {
        Uri.encode(entry.arguments?.getString(it.groupValues[1]).orEmpty())
    }

@Composable
fun VademoNavHost() {
    val nav = rememberNavController()
    val history = remember { NavHistory() }
    history.open = { nav.navigate(it) }
    LaunchedEffect(nav) {
        nav.currentBackStackEntryFlow.collect { entry ->
            history.onTop(entry.id, routeOf(entry), entry.destination.route in PLACES)
        }
    }
    CompositionLocalProvider(LocalNavHistory provides history) {
        AppNavHost(nav)
    }
}

@Composable
private fun AppNavHost(nav: NavHostController) {
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenKb = { nav.navigate(Routes.kb(it.kb, it.tab.name, it.diagram)) },
                onResolve = { nav.navigate(Routes.conflict(it)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onSearch = { nav.navigate(Routes.search()) },
            )
        }
        composable(
            Routes.SEARCH,
            arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" }),
        ) {
            SearchScreen(
                onBack = { nav.popBackStack() },
                onOpenNote = { kb, path -> nav.navigate(Routes.note(kb, path)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onDone = { nav.popBackStack() })
        }
        composable(
            Routes.KB,
            arguments = listOf(
                navArgument("kb") { type = NavType.StringType },
                navArgument("tab") { type = NavType.StringType; defaultValue = "" },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            KbScreen(
                onBack = { nav.popBackStack() },
                onOpenFolder = { kb, path -> nav.navigate(Routes.browse(kb, path)) },
                onOpenNote = { kb, path -> nav.navigate(Routes.note(kb, path)) },
                onOpenDiagram = { kb, name -> nav.navigate(Routes.diagram(kb, name)) },
                onAllDiagrams = { nav.navigate(Routes.diagrams(it)) },
                onSearch = { kb, filters -> nav.navigate(Routes.searchIn(kb, filters)) },
                onNewNote = { kb, path, template, title -> nav.navigate(Routes.newNote(kb, path, template, title)) },
            )
        }
        composable(Routes.BROWSE, arguments = kbArgs) {
            BrowseScreen(
                onBack = { nav.popBackStack() },
                onOpenFolder = { kb, path -> nav.navigate(Routes.browse(kb, path)) },
                onOpenNote = { kb, path -> nav.navigate(Routes.note(kb, path)) },
                onSearch = { kb, filters -> nav.navigate(Routes.searchIn(kb, filters)) },
                onNewNote = { kb, path, template, title -> nav.navigate(Routes.newNote(kb, path, template, title)) },
            )
        }
        composable(Routes.DIAGRAMS, arguments = listOf(navArgument("kb") { type = NavType.StringType })) {
            DiagramListScreen(
                onBack = { nav.popBackStack() },
                onOpen = { kb, name -> nav.navigate(Routes.diagram(kb, name)) },
            )
        }
        composable(
            Routes.DIAGRAM,
            arguments = listOf(
                navArgument("kb") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType },
            ),
        ) {
            DiagramScreen(
                onBack = { nav.popBackStack() },
                onOpenNote = { kb, path -> nav.navigate(Routes.note(kb, path)) },
            )
        }
        composable(
            Routes.MAP_AROUND,
            arguments = listOf(
                navArgument("kb") { type = NavType.StringType },
                navArgument("focus") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            GraphScreen(
                onBack = { nav.popBackStack() },
                onOpenNote = { kb, path -> nav.navigate(Routes.note(kb, path)) },
            )
        }
        composable(Routes.NOTE, arguments = kbArgs) {
            NoteScreen(
                onBack = { nav.popBackStack() },
                onOpenNote = { kb, path -> nav.navigate(Routes.note(kb, path)) },
                onEdit = { kb, path -> nav.navigate(Routes.edit(kb, path)) },
                onShowOnMap = { kb, path -> nav.navigate(Routes.mapAround(kb, path)) },
                onOpenDiagram = { kb, name -> nav.navigate(Routes.diagram(kb, name)) },
                onSearch = { kb, filters -> nav.navigate(Routes.searchIn(kb, filters)) },
            )
        }
        composable(
            Routes.EDIT,
            arguments = kbArgs + listOf(
                navArgument("template") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            EditScreen(onDone = { nav.popBackStack() })
        }
        composable(Routes.CONFLICT, arguments = listOf(navArgument("kb") { type = NavType.StringType })) {
            ConflictScreen(onDone = { nav.popBackStack() })
        }
    }
}
