package app.iconz

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** "Apply": set Iconz as the icon pack in a launcher that supports it. */
object Launchers {
    /** [apply] builds the launcher's own "use this icon pack" intent; null means the user picks Iconz in its settings. */
    private class Launcher(val name: String, val pkg: String, val apply: ((Context) -> Intent?)? = null)

    private val known = listOf(
        Launcher("Lawnchair", "app.lawnchair"),
        Launcher("Lawnchair", "app.lawnchair.play"),
        Launcher("Nova Launcher", "com.teslacoilsw.launcher") { c ->
            Intent("com.teslacoilsw.launcher.APPLY_ICON_THEME")
                .setPackage("com.teslacoilsw.launcher")
                .putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_TYPE", "GO")
                .putExtra("com.teslacoilsw.launcher.extra.ICON_THEME_PACKAGE", c.packageName)
        },
        Launcher("Smart Launcher", "ginlemon.flowerfree") { c ->
            Intent("ginlemon.smartlauncher.setGSLTHEME").putExtra("package", c.packageName)
        },
        Launcher("Action Launcher", "com.actionlauncher.playstore") { c ->
            c.packageManager.getLaunchIntentForPackage("com.actionlauncher.playstore")
                ?.putExtra("apply_icon_pack", c.packageName)
        },
        Launcher("Niagara", "bitpit.launcher"),
        Launcher("Hyperion", "projekt.launcher"),
        Launcher("Kvaesitso", "de.mm20.launcher2.release"),
        Launcher("Microsoft Launcher", "com.microsoft.launcher"),
    )

    fun show(activity: Activity) {
        val installed = known.filter { activity.isInstalled(it.pkg) }
        if (installed.isEmpty()) {
            activity.dialog().setTitle("No icon pack launcher found")
                .setMessage(
                    "Your launcher may not support icon packs (Pixel and Samsung launchers don't). " +
                        "Install Lawnchair or Nova, or pick Iconz in your launcher's icon settings.",
                ).setPositiveButton("OK", null).show()
            return
        }
        activity.dialog().setTitle("Apply Iconz in…")
            .setItems(installed.map { it.name }.toTypedArray()) { _, i -> apply(activity, installed[i]) }
            .show()
    }

    private fun apply(activity: Activity, l: Launcher) {
        val direct = l.apply?.invoke(activity)
        if (direct != null) {
            try {
                activity.startActivity(direct)
                return
            } catch (_: ActivityNotFoundException) {
                // Older or newer version without the intent: fall back to its settings.
            } catch (_: SecurityException) {
            }
        }
        val open = activity.packageManager.getLaunchIntentForPackage(l.pkg)
        if (open != null) activity.startActivity(open)
        activity.toast("In ${l.name}'s settings, choose Iconz as the icon pack")
    }

    private fun Context.isInstalled(pkg: String) = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
