package com.cpinto.vademo.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cpinto.vademo.ui.browse.BrowseScreen
import com.cpinto.vademo.ui.conflict.ConflictScreen
import com.cpinto.vademo.ui.edit.EditScreen
import com.cpinto.vademo.ui.home.HomeScreen
import com.cpinto.vademo.ui.note.NoteScreen
import com.cpinto.vademo.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val BROWSE = "browse/{kb}?path={path}"
    const val NOTE = "note/{kb}?path={path}"
    const val EDIT = "edit/{kb}?path={path}"
    const val CONFLICT = "conflict/{kb}"

    fun browse(kb: String, path: String = "") = "browse/${enc(kb)}?path=${enc(path)}"
    fun note(kb: String, path: String) = "note/${enc(kb)}?path=${enc(path)}"
    fun edit(kb: String, path: String) = "edit/${enc(kb)}?path=${enc(path)}"
    fun conflict(kb: String) = "conflict/${enc(kb)}"

    private fun enc(value: String) = Uri.encode(value)
}

private val kbArgs = listOf(
    navArgument("kb") { type = NavType.StringType },
    navArgument("path") { type = NavType.StringType; defaultValue = "" },
)

@Composable
fun VademoNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenKb = { nav.navigate(Routes.browse(it)) },
                onResolve = { nav.navigate(Routes.conflict(it)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onDone = { nav.popBackStack() })
        }
        composable(Routes.BROWSE, arguments = kbArgs) {
            BrowseScreen(
                onBack = { nav.popBackStack() },
                onOpenFolder = { kb, path -> nav.navigate(Routes.browse(kb, path)) },
                onOpenNote = { kb, path -> nav.navigate(Routes.note(kb, path)) },
            )
        }
        composable(Routes.NOTE, arguments = kbArgs) {
            NoteScreen(
                onBack = { nav.popBackStack() },
                onOpenNote = { kb, path -> nav.navigate(Routes.note(kb, path)) },
                onEdit = { kb, path -> nav.navigate(Routes.edit(kb, path)) },
            )
        }
        composable(Routes.EDIT, arguments = kbArgs) {
            EditScreen(onDone = { nav.popBackStack() })
        }
        composable(Routes.CONFLICT, arguments = listOf(navArgument("kb") { type = NavType.StringType })) {
            ConflictScreen(onDone = { nav.popBackStack() })
        }
    }
}
