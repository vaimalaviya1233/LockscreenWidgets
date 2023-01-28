package tk.zwander.common.util

import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import androidx.annotation.CallSuper
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.arasthel.spannedgridlayoutmanager.SpannedGridLayoutManager
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetType
import tk.zwander.common.host.WidgetHostCompat
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter

abstract class BaseDelegate<State : BaseDelegate.BaseState>(context: Context) : ContextWrapper(context),
    EventObserver, WidgetHostCompat.OnClickCallback {
    protected val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    protected val power by lazy { getSystemService(POWER_SERVICE) as PowerManager }
    protected val kgm by lazy { getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    protected val wallpaper by lazy { getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager }
    protected val widgetHost by lazy { widgetHostCompat }
    protected val displayManager by lazy {
        getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    abstract var state: State
        protected set

    protected abstract val prefsHandler: HandlerRegistry
    protected abstract val blurManager: BlurManager
    protected abstract val adapter: WidgetFrameAdapter
    protected abstract val gridLayoutManager: LayoutManager
    protected abstract val params: WindowManager.LayoutParams
    protected abstract val rootView: View
    protected abstract val recyclerView: RecyclerView
    protected abstract var currentWidgets: List<WidgetData>

    private val touchHelperCallback by lazy {
        createTouchHelperCallback(
            adapter = adapter,
            widgetMoved = this::onWidgetMoved,
            onItemSelected = this::onItemSelected,
            frameLocked = this::isLocked
        )
    }
    protected val itemTouchHelper by lazy {
        ItemTouchHelper(touchHelperCallback)
    }

    @CallSuper
    open fun onCreate() {
        prefsHandler.register(this)
        eventManager.addObserver(this)
        blurManager.onCreate()
        widgetHost.addOnClickCallback(this)
        gridLayoutManager.spanSizeLookup = adapter.spanSizeLookup
        recyclerView.setHasFixedSize(true)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = gridLayoutManager
        itemTouchHelper.attachToRecyclerView(recyclerView)
        adapter.updateWidgets(currentWidgets)

        updateCounts()
    }

    @CallSuper
    open fun onDestroy() {
        eventManager.removeObserver(this)
        prefsHandler.unregister(this)
        blurManager.onDestroy()
        widgetHost.removeOnClickCallback(this)
        itemTouchHelper.attachToRecyclerView(null)

        currentWidgets = ArrayList(adapter.widgets)
    }

    @CallSuper
    override fun onEvent(event: Event) {
        when (event) {
            is Event.RemoveWidgetConfirmed -> {
                val position = currentWidgets.indexOf(event.item)

                if (event.remove && currentWidgets.contains(event.item)) {
                    currentWidgets = currentWidgets.toMutableList().apply {
                        remove(event.item)
                        when (event.item?.safeType) {
                            WidgetType.WIDGET -> widgetHost.deleteAppWidgetId(event.item.id)
                            WidgetType.SHORTCUT -> shortcutIdManager.removeShortcutId(event.item.id)
                            else -> {}
                        }
                    }

                    adapter.currentEditingInterfacePosition = -1
                    adapter.updateWidgets(currentWidgets.toList())
                }

                widgetRemovalConfirmed(event, position)
            }
            else -> {}
        }
    }

    open fun updateState(transform: (State) -> State) {
        val newState = transform(state)
        logUtils.debugLog("Updating state from\n$state\nto\n$newState")
        state = newState
    }

    @CallSuper
    protected open fun onWidgetMoved(moved: Boolean) {
        if (moved) {
            currentWidgets = adapter.widgets
            adapter.currentEditingInterfacePosition = -1
        }
    }

    /**
     * Make sure the number of rows/columns in the frame/drawer reflects the user-selected value.
     */
    protected fun updateCounts() {
        val counts = retrieveCounts()

        gridLayoutManager.apply {
            counts.first?.let { rowCount = it }
            counts.second?.let { columnCount = it }
        }
    }

    protected abstract fun onItemSelected(selected: Boolean)
    protected abstract fun isLocked(): Boolean
    protected abstract fun retrieveCounts(): Pair<Int?, Int?>

    protected open fun widgetRemovalConfirmed(event: Event.RemoveWidgetConfirmed, position: Int) {}

    abstract class BaseState {
        abstract val isHoldingItem: Boolean
        abstract val updatedForMove: Boolean
        abstract val handlingClick: Boolean
    }

    abstract class LayoutManager(
        context: Context,
        orientation: Int,
        rowCount: Int,
        colCount: Int
    ) : SpannedGridLayoutManager(
        context,
        orientation,
        rowCount,
        colCount
    )
}
