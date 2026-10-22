package com.wendigo.zifiri

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class GameActivity : Activity(), GameView.Host {
    companion object {
        const val EXTRA_CLASS = "class"
        const val EXTRA_CONTINUE = "continue"
    }

    private lateinit var game: Game
    private lateinit var view: GameView
    private var dialog: AlertDialog? = null
    private var scoreSaved = false
    private var ending = false
    private val saveHandler = Handler(Looper.getMainLooper())
    private var saveScheduled = false
    private val saveRunnable = Runnable {
        saveScheduled = false
        persist()
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        Sfx.enabled = Store.sound
        Sfx.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupFullscreen()

        val continueRun = intent.getBooleanExtra(EXTRA_CONTINUE, false)
        val cls = HeroClass.byKey(intent.getStringExtra(EXTRA_CLASS) ?: "blade")
        val loaded = if (continueRun) Store.load(this) else null
        game = loaded ?: Game.newGame(cls)
        if (loaded == null) Store.clearSave(this)

        game.listener = object : GameListener {
            override fun onGameOver() {
                runOnUiThread { showEnd(false) }
            }

            override fun onVictory() {
                runOnUiThread { showEnd(true) }
            }

            override fun onShopRequested() {
                runOnUiThread { showShop() }
            }
        }
        game.onProgress = { scheduleSave() }

        view = GameView(this, game, this)
        setContentView(view)
        view.keepScreenOn = true

        if (loaded != null) {
            Toast.makeText(this, "Kaldığın yerden devam ediyorsun.", Toast.LENGTH_SHORT).show()
        } else if (!Store.hintSeen) {
            Store.hintSeen = true
            view.postDelayed({ showHelp() }, 700)
        }
    }

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupFullscreen()
    }

    override fun onResume() {
        super.onResume()
        Sfx.enabled = Store.sound
        setupFullscreen()
        view.refresh()
    }

    override fun onPause() {
        super.onPause()
        view.stopWalking()
        cancelScheduledSave()
        persist()
    }

    override fun onStop() {
        super.onStop()
        persist()
    }

    override fun onDestroy() {
        cancelScheduledSave()
        saveHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun scheduleSave() {
        if (ending || saveScheduled) return
        saveScheduled = true
        saveHandler.postDelayed(saveRunnable, 800L)
    }

    private fun cancelScheduledSave() {
        if (!saveScheduled) return
        saveScheduled = false
        saveHandler.removeCallbacks(saveRunnable)
    }

    override fun onBackPressed() {
        if (dialog?.isShowing == true) {
            dialog?.dismiss()
            return
        }
        showPause()
    }

    private fun persist() {
        if (ending) return
        if (!game.gameOver && !game.victory) {
            Store.save(this, game)
        }
    }

    // ------------------------------------------------------------------
    // Host
    // ------------------------------------------------------------------

    override fun openInventory() {
        showInventory()
    }

    override fun openPause() {
        showPause()
    }

    // ------------------------------------------------------------------
    // Views / helpers
    // ------------------------------------------------------------------

    private fun dialogBuilder(title: String?): AlertDialog.Builder {
        val b = AlertDialog.Builder(this, R.style.Zifiri_Dialog)
        if (title != null) b.setTitle(title)
        return b
    }

    private fun textLabel(text: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setLineSpacing(0f, 1.12f)
        }
    }

    private fun button(text: String, ghost: Boolean = false): Button {
        val style = if (ghost) R.style.Zifiri_Button_Ghost else R.style.Zifiri_Button
        return Button(this, null, 0, style).apply {
            this.text = text
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8f) }
        }
    }

    private fun scrollBody(build: (LinearLayout) -> Unit): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(10f), dp(18f), dp(16f))
        }
        build(root)
        scroll.addView(root)
        return scroll
    }

    private fun showDialog(builder: AlertDialog.Builder) {
        dialog?.dismiss()
        dialog = builder.create()
        dialog?.setOnDismissListener { view.refresh() }
        dialog?.show()
    }

    // ------------------------------------------------------------------
    // Inventory
    // ------------------------------------------------------------------

    private fun showInventory() {
        if (game.gameOver || game.victory) return
        view.stopWalking()
        val p = game.player
        val body = scrollBody { root ->
            val head = textLabel(
                "${game.heroClass.title} · Sv ${p.level}   ◆ ${p.gold}   Can ${p.hp}/${p.maxHp}",
                12f, Palette.TEXT_DIM
            )
            root.addView(head)

            root.addView(sectionTitle("KUŞANILAN"))
            root.addView(equipmentRow("Silah", p.weapon))
            root.addView(equipmentRow("Zırh", p.armor))
            root.addView(equipmentRow("Yadigâr", p.relic))

            root.addView(sectionTitle("ÇANTA (${p.inventory.size}/24)"))
            if (p.inventory.isEmpty()) {
                root.addView(textLabel(getString(R.string.inventory_empty), 12f, Palette.TEXT_FAINT))
            } else {
                for (item in p.inventory.toList()) {
                    root.addView(itemRow(item))
                }
            }
        }

        val b = dialogBuilder(getString(R.string.inventory_title))
        b.setView(body)
        b.setNegativeButton(R.string.action_close, null)
        showDialog(b)
    }

    private fun sectionTitle(text: String): TextView {
        return textLabel(text, 10f, Palette.AMBER).apply {
            letterSpacing = 0.12f
            setPadding(0, dp(14f), 0, dp(4f))
        }
    }

    private fun equipmentRow(slot: String, item: Item?): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2f), dp(5f), dp(2f), dp(5f))
        }
        val name = if (item == null) "—" else game.itemName(item)
        val label = textLabel("$slot: $name", 12.5f, if (item == null) Palette.TEXT_FAINT else Palette.TEXT, true)
        label.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(label)
        if (item != null && item.upgrade > 0) {
            row.addView(textLabel("+${item.upgrade}", 12f, Palette.AMBER_HI, true))
        }
        return row
    }

    private fun itemRow(item: Item): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            setBackgroundResource(R.drawable.card_bg)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8f) }
            isClickable = true
            isFocusable = true
        }
        val name = game.itemName(item) + if (item.count > 1) " ×${item.count}" else ""
        row.addView(textLabel(name, 13.5f, item.def.color, true))
        val kind = when (item.def.kind) {
            ItemKind.WEAPON -> "Silah"
            ItemKind.ARMOR -> "Zırh"
            ItemKind.RELIC -> "Yadigâr"
            ItemKind.POTION -> "İksir"
            ItemKind.SCROLL -> "Parşömen"
            ItemKind.OIL -> "Yağ"
            ItemKind.KEY -> "Anahtar"
            ItemKind.FOOD -> "Yiyecek"
            ItemKind.QUEST -> "Görev"
        }
        row.addView(textLabel(kind + " · " + game.itemDescription(item), 11f, Palette.TEXT_DIM))
        row.setOnClickListener {
            showItemActions(item)
        }
        return row
    }

    private fun showItemActions(item: Item) {
        val p = game.player
        val canUse = item.def.kind != ItemKind.KEY && item.def.kind != ItemKind.QUEST
        val options = ArrayList<String>()
        if (canUse) {
            options.add(
                when (item.def.kind) {
                    ItemKind.WEAPON, ItemKind.ARMOR, ItemKind.RELIC -> getString(R.string.action_equip)
                    else -> getString(R.string.action_use)
                }
            )
        }
        options.add(getString(R.string.action_drop))
        options.add(getString(R.string.action_cancel))
        val arr = options.toTypedArray()
        val b = dialogBuilder(game.itemName(item))
        b.setItems(arr) { _, which ->
            when {
                canUse && which == 0 -> {
                    val ok = game.useItem(item)
                    if (ok) {
                        Sfx.play(if (item.def.kind == ItemKind.POTION) "potion" else "equip")
                        view.refresh()
                        showInventory()
                    }
                }
                canUse && which == 1 -> {
                    game.dropItem(item)
                    view.refresh()
                    showInventory()
                }
                !canUse && which == 0 -> {
                    game.dropItem(item)
                    view.refresh()
                    showInventory()
                }
            }
        }
        showDialog(b)
    }

    // ------------------------------------------------------------------
    // Shop
    // ------------------------------------------------------------------

    private fun showShop() {
        if (game.shopStock.isEmpty()) {
            Toast.makeText(this, R.string.shop_empty, Toast.LENGTH_SHORT).show()
            return
        }
        view.stopWalking()
        val body = scrollBody { root ->
            root.addView(textLabel(getString(R.string.shop_gold, game.player.gold), 12.5f, Palette.AMBER_HI, true))
            root.addView(textLabel(getString(R.string.shop_title), 11f, Palette.TEXT_FAINT))
            val stock = game.shopStock.toList()
            for ((index, entry) in stock.withIndex()) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                    setBackgroundResource(R.drawable.card_bg)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(8f) }
                }
                row.addView(textLabel(game.itemName(entry.first), 13.5f, entry.first.def.color, true))
                row.addView(textLabel(game.itemDescription(entry.first), 11f, Palette.TEXT_DIM))
                val b = button("${entry.second} altın · " + getString(R.string.action_buy), ghost = game.player.gold < entry.second)
                b.setOnClickListener {
                    if (game.buyFromShop(index)) {
                        Sfx.play("gold")
                        view.refresh()
                        showShop()
                    } else {
                        Sfx.play("empty")
                        Toast.makeText(this, R.string.shop_buy_no, Toast.LENGTH_SHORT).show()
                    }
                }
                row.addView(b)
                root.addView(row)
            }
        }
        val b = dialogBuilder(getString(R.string.shop_title))
        b.setView(body)
        b.setNegativeButton(R.string.action_close, null)
        showDialog(b)
    }

    // ------------------------------------------------------------------
    // Pause / help / end
    // ------------------------------------------------------------------

    private fun showPause() {
        view.stopWalking()
        val body = scrollBody { root ->
            root.addView(textLabel("Nefes al. Karanlık bekler.", 12f, Palette.TEXT_DIM))
            root.addView(button(getString(R.string.pause_resume)).apply {
                setOnClickListener { dialog?.dismiss() }
            })
            root.addView(button(getString(R.string.pause_save_exit)).apply {
                setOnClickListener {
                    Store.save(this@GameActivity, game)
                    dialog?.dismiss()
                    finish()
                }
            })
            root.addView(button(getString(R.string.menu_help), ghost = true).apply {
                setOnClickListener { showHelp() }
            })

            root.addView(sectionTitle(getString(R.string.settings_title)))
            root.addView(switchRow("Ses", Store.sound) { on ->
                Store.sound = on
                Sfx.enabled = on
            })
            root.addView(switchRow("Titreşim", Store.haptic) { on ->
                Store.haptic = on
            })
            root.addView(switchRow("Ekran sarsıntısı", Store.shakeScreen) { on ->
                Store.shakeScreen = on
            })
        }
        val b = dialogBuilder(getString(R.string.pause_title))
        b.setView(body)
        showDialog(b)
    }

    private fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10f), 0, dp(4f))
        }
        val t = textLabel(label, 13f, Palette.TEXT)
        t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(t)
        val sw = Switch(this)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _: CompoundButton, on: Boolean -> onChange(on) }
        row.addView(sw)
        return row
    }

    private fun showHelp() {
        val body = scrollBody { root ->
            root.addView(textLabel(getString(R.string.help_body), 12.5f, Palette.TEXT))
        }
        val b = dialogBuilder(getString(R.string.help_title))
        b.setView(body)
        b.setPositiveButton(R.string.action_close, null)
        showDialog(b)
    }

    private fun showEnd(victory: Boolean) {
        if (isFinishing) return
        if (game.gameOver || game.victory) {
            ending = true
        }
        view.stopWalking()
        val score = if (!scoreSaved) {
            scoreSaved = true
            Store.addScore(this, game)
        } else Store.scoreOf(game)
        Store.clearSave(this)
        dialog?.dismiss()

        val stats = getString(
            R.string.run_stats,
            game.depth, game.player.level, game.player.gold, game.player.kills
        )
        val body = scrollBody { root ->
            root.addView(textLabel(if (victory) getString(R.string.victory_sub) else getString(R.string.death_sub), 12.5f, Palette.TEXT_DIM))
            root.addView(textLabel(stats, 12f, Palette.TEXT))
            root.addView(textLabel("Skor: $score", 16f, Palette.AMBER_HI, true).apply {
                setPadding(0, dp(10f), 0, dp(4f))
            })
        }
        val b = dialogBuilder(if (victory) getString(R.string.victory_title) else getString(R.string.death_title))
        b.setView(body)
        b.setCancelable(false)
        b.setPositiveButton(R.string.btn_retry) { _, _ ->
            val i = Intent(this, GameActivity::class.java)
            i.putExtra(EXTRA_CLASS, game.heroClass.key)
            startActivity(i)
            finish()
        }
        b.setNegativeButton(R.string.btn_menu) { _, _ ->
            finish()
        }
        dialog = b.create()
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.show()
    }
}
