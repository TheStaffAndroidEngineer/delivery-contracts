package staff.android.delivery

import android.app.Activity
import android.os.Bundle
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import staff.android.delivery.saved.SavedPresenter

class MainActivity : Activity() {
    private val scope = MainScope()
    private val cart get() = (application as ShopApplication).cart
    private lateinit var presenter: SavedPresenter
    private lateinit var body: LinearLayout
    private var screen = "Saved"
    private var diagnostics = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presenter = SavedPresenter(cart)
        val root = column()
        root.setPadding(dp(20), dp(12), dp(20), dp(20))
        root.setBackgroundColor(getColor(R.color.paper))
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = true
            setBackgroundColor(getColor(R.color.paper))
            addView(root)
        }
        val viewport = FrameLayout(this).apply {
            clipToPadding = true
            setBackgroundColor(getColor(R.color.paper))
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom)
                insets
            }
            addView(scroll, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT))
        }
        val brand = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        brand.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_common_goods)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(32), dp(32)))
        brand.addView(label("Common Goods", 21f, true).apply { setPadding(dp(12), dp(12), 0, dp(12)) })
        root.addView(brand)
        val tabs = LinearLayout(this)
        for (title in listOf("Saved", "Cart")) {
            tabs.addView(action(title, primary = false) {
                screen = title; render()
            }.apply { contentDescription = "Open $title" },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(tabs)
        body = column()
        root.addView(body)
        setContentView(viewport)
        viewport.requestApplyInsets()
        scope.launch { cart.state.collect { render() } }
        scope.launch { presenter.status.collect { render() } }
        render()
    }

    private fun render() {
        body.removeAllViews()
        body.addView(label(screen, 30f, true))
        body.addView(ImageView(this).apply {
            setImageResource(R.drawable.product_tea)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = rounded(R.color.panel)
            clipToOutline = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(168)))
        body.addView(label("Tea", 24f, true))
        body.addView(label(if (screen == "Saved") "A little everyday ritual. Saved for later." else "A little everyday ritual. In your cart.", 16f))
        body.addView(label("Accepted quantity: ${cart.state.value.quantityOf("tea")}", 20f))
        if (screen == "Saved") {
            body.addView(action("Add 1 Tea", primary = true) {
                scope.launch { presenter.addTea() }
            }.apply { isEnabled = !presenter.status.value.busy })
            if (presenter.status.value.message.isNotBlank()) {
                body.addView(label(presenter.status.value.message, 18f).apply {
                    background = rounded(R.color.accent_soft)
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                })
            }
        }
        body.addView(action(if (diagnostics) "Close lab controls" else "Lab controls", primary = false) {
            diagnostics = !diagnostics; render()
        })
        if (diagnostics) {
            body.addView(label("Revision: ${cart.state.value.revision}", 16f))
            body.addView(label("One in-memory cart; a new process starts at three.", 16f))
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(getColor(color)); cornerRadius = dp(16).toFloat()
    }
    private fun label(value: String, size: Float, heading: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(getColor(R.color.ink))
        typeface = resources.getFont(if (heading) R.font.sora_bold else R.font.google_sans_regular)
        setPadding(0, dp(12), 0, dp(12))
        if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = heading
    }
    private fun action(title: String, primary: Boolean, callback: () -> Unit) = Button(this).apply {
        text = title; isAllCaps = false; textSize = 16f; minHeight = dp(52)
        typeface = resources.getFont(R.font.google_sans_bold)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        setTextColor(if (primary) getColor(R.color.paper) else getColor(R.color.accent))
        background = rounded(if (primary) R.color.accent else R.color.panel)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10); bottomMargin = dp(10) }
        setOnClickListener { callback() }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
