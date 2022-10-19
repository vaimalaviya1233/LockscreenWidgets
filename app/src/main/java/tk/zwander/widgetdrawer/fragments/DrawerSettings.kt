package tk.zwander.widgetdrawer.fragments

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.*
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.util.*
import tk.zwander.lockscreenwidgets.util.backup.BackupRestoreManager
import tk.zwander.lockscreenwidgets.util.backup.backupRestoreManager
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.*

class DrawerSettings : PreferenceFragmentCompat() {
    private val prefsHandler = HandlerRegistry {
        handler(PrefManager.KEY_DRAWER_ENABLED) {
            findPreference<SwitchPreference>(PrefManager.KEY_DRAWER_ENABLED)?.isChecked =
                requireContext().prefManager.drawerEnabled
        }
    }

    private val onWidgetBackUp = registerForActivityResult(ActivityResultContracts.CreateDocument("text/*")) { uri: Uri? ->
        if (uri != null) {
            requireContext().apply {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { output ->
                    output.write(backupRestoreManager.createBackupString(BackupRestoreManager.Which.DRAWER))
                }
            }
        }
    }

    private val onWidgetRestore = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            requireContext().apply {
                try {
                    val input = contentResolver.openInputStream(uri)?.bufferedReader()?.readText()

                    if (!backupRestoreManager.restoreBackupString(input, BackupRestoreManager.Which.DRAWER)) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.error)
                            .setMessage(R.string.unable_to_restore_widgets)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                        logUtils.normalLog("Unable to restore widgets")
                    } else {
                        requireActivity().finish()
                    }
                } catch (e: FileNotFoundException) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.error)
                        .setMessage(R.string.unable_to_restore_widgets)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    logUtils.normalLog("Unable to restore widgets", e)
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_drawer, rootKey)
        prefsHandler.register(requireContext())

        findPreference<Preference>("back_up_widgets")?.setOnPreferenceClickListener {
            val formatter = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())

            onWidgetBackUp.launch("lockscreen_widgets_drawer_backup_${formatter.format(Date())}.lswidg")
            true
        }

        findPreference<Preference>("restore_widgets")?.setOnPreferenceClickListener {
            onWidgetRestore.launch(arrayOf("*/*"))
            true
        }

        val showBlurOptions = requireContext().isOneUI || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (!showBlurOptions) {
            preferenceScreen.removePreferenceRecursively(PrefManager.KEY_BLUR_DRAWER_BACKGROUND)
            preferenceScreen.removePreferenceRecursively(PrefManager.KEY_BLUR_DRAWER_BACKGROUND_AMOUNT)
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> {
        return object : PreferenceGroupAdapter(preferenceScreen) {
            @SuppressLint("RestrictedApi")
            override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                val preference = getItem(position)
                if (preference is PreferenceCategory)
                    setZeroPaddingToLayoutChildren(holder.itemView)
                else
                    holder.itemView.findViewById<View?>(R.id.icon_frame)?.visibility =
                        if (preference?.icon == null) View.GONE else View.VISIBLE
            }
        }
    }

    private fun setZeroPaddingToLayoutChildren(view: View) {
        if (view !is ViewGroup)
            return
        val childCount = view.childCount
        for (i in 0 until childCount) {
            setZeroPaddingToLayoutChildren(view.getChildAt(i))
            view.setPaddingRelative(0, view.paddingTop, view.paddingEnd, view.paddingBottom)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        prefsHandler.unregister(requireContext())
    }
}