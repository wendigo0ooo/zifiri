package com.wendigo.zifiri

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MenuActivity : Activity() {
    private lateinit var menuButtons: LinearLayout
    private lateinit var classScreen: LinearLayout
    private lateinit var classList: LinearLayout
    private lateinit var continueButton: Button
    private lateinit var scroll: ScrollView
    private lateinit var soundToggle: ImageButton
    private var classShowing = false

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        Sfx.enabled = Store.sound
        Sfx.init(this)
        setupFullscreen()
        setContentView(R.layout.activity_menu)

        menuButtons = findViewById(R.id.menuButtons)
        classScreen = findViewById(R.id.classScreen)
        classList = findViewById(R.id.classList)
        continueButton = findViewById(R.id.menuContinue)
        scroll = findViewById(R.id.menuScroll)
        soundToggle = findViewById(R.id.soundToggle)

        findViewById<Button>(R.id.menuNew).setOnClickListener { onNewGame() }
        continueButton.setOnClickListener { onContinue() }
        findViewById<Button>(R.id.menuHelp).setOnClickListener { showHelp() }
        findViewById<Button>(R.id.menuScores).setOnClickListener { showScores() }
        findViewById<Button>(R.id.classBack).setOnClickListener { showMenu() }

        buildClassList()
        updateSoundIcon()

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
        findViewById<TextView>(R.id.versionText).text = getString(R.string.menu_version, version)
    }

    override fun onResume() {
        super.onResume()
        Sfx.enabled = Store.sound
        updateSoundIcon()
        refreshContinue()
        setupFullscreen()
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

    override fun onBackPressed() {
        if (classShowing) showMenu() else super.onBackPressed()
    }

    private fun refreshContinue() {
        val summary = Store.saveSummary(this)
        continueButton.isEnabled = summary != null
        continueButton.alpha = if (summary != null) 1f else 0.42f
        continueButton.text = if (summary != null) {
            getString(R.string.menu_continue) + " · " + summary
        } else {
            getString(R.string.menu_continue)
        }
    }

    private fun buildClassList() {
        classList.removeAllViews()
        for (hero in HeroClass.values()) {
            val card = ClassOptionView(this, hero)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(124f)
            ).apply { bottomMargin = dp(10f) }
            card.layoutParams = lp
            card.setOnClickListener {
                Sfx.play("equip")
                if (Store.haptic) Sfx.vibrate(this, 22L)
                startGame(hero)
            }
            classList.addView(card)
        }
    }

    private fun onNewGame() {
        if (Store.hasSave(this)) {
            showOverwriteDialog()
        } else {
            showClassScreen()
        }
    }

    private fun onContinue() {
        val summary = Store.saveSummary(this) ?: return
        if (summary.isEmpty()) return
        val i = Intent(this, GameActivity::class.java)
        i.putExtra(GameActivity.EXTRA_CONTINUE, true)
        startActivity(i)
    }

    private fun startGame(hero: HeroClass) {
        Store.clearSave(this)
        val i = Intent(this, GameActivity::class.java)
        i.putExtra(GameActivity.EXTRA_CLASS, hero.key)
        startActivity(i)
    }

    private fun showOverwriteDialog() {
        val b = AlertDialog.Builder(this, R.style.Zifiri_Dialog)
        b.setTitle(R.string.overwrite_title)
        b.setMessage(R.string.overwrite_body)
        b.setPositiveButton(R.string.overwrite_yes) { _, _ -> showClassScreen() }
        b.setNegativeButton(R.string.overwrite_no, null)
        b.show()
    }

    private fun showClassScreen() {
        classShowing = true
        menuButtons.animate().alpha(0f).setDuration(140).withEndAction {
            menuButtons.visibility = View.GONE
            menuButtons.alpha = 1f
            classScreen.visibility = View.VISIBLE
            classScreen.alpha = 0f
            classScreen.translationY = dp(16f).toFloat()
            classScreen.animate().alpha(1f).translationY(0f).setDuration(220).start()
        }.start()
        scroll.smoothScrollTo(0, 0)
    }

    private fun showMenu() {
        classShowing = false
        classScreen.animate().alpha(0f).setDuration(130).withEndAction {
            classScreen.visibility = View.GONE
            classScreen.alpha = 1f
            menuButtons.visibility = View.VISIBLE
            menuButtons.alpha = 0f
            menuButtons.animate().alpha(1f).setDuration(180).start()
        }.start()
    }

    private fun updateSoundIcon() {
        soundToggle.setImageResource(if (Store.sound) R.drawable.ic_sound_on else R.drawable.ic_sound_off)
        soundToggle.contentDescription = getString(if (Store.sound) R.string.menu_sound_on else R.string.menu_sound_off)
        soundToggle.alpha = if (Store.sound) 1f else 0.55f
        soundToggle.setOnClickListener {
            Store.sound = !Store.sound
            Sfx.enabled = Store.sound
            updateSoundIcon()
            if (Store.sound) Sfx.play("open")
        }
    }

    // ------------------------------------------------------------------
    // Dialogs
    // ------------------------------------------------------------------

    private fun dialogText(text: String, size: Float = 12.5f, color: Int = Palette.TEXT): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            setLineSpacing(dp(2f).toFloat(), 1.1f)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    private fun showHelp() {
        val scrollView = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(8f), dp(20f), dp(16f))
        }
        box.addView(dialogText(getString(R.string.help_body), 12.5f, Palette.TEXT))
        scrollView.addView(box)
        val b = AlertDialog.Builder(this, R.style.Zifiri_Dialog)
        b.setTitle(R.string.help_title)
        b.setView(scrollView)
        b.setPositiveButton(R.string.action_close, null)
        b.show()
    }

    private fun showScores() {
        val scores = Store.scores(this)
        val scrollView = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(8f), dp(20f), dp(16f))
        }
        if (scores.isEmpty()) {
            box.addView(dialogText(getString(R.string.scores_empty), 12.5f, Palette.TEXT_FAINT))
        } else {
            for ((i, s) in scores.withIndex()) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(7f), 0, dp(7f))
                }
                val mark = if (s.victory) " ★" else ""
                row.addView(
                    dialogText(
                        "${i + 1}. ${s.cls} · ${s.depth}. Kat · Sv ${s.level}$mark",
                        13f, if (s.victory) Palette.AMBER_HI else Palette.TEXT
                    ).apply { setTypeface(typeface, Typeface.BOLD) }
                )
                row.addView(
                    dialogText(
                        "◆ ${s.gold}   ·   ${s.kills} öldürme   ·   ${s.score} puan",
                        11f, Palette.TEXT_DIM
                    )
                )
                box.addView(row)
            }
        }
        scrollView.addView(box)
        val b = AlertDialog.Builder(this, R.style.Zifiri_Dialog)
        b.setTitle(R.string.scores_title)
        b.setView(scrollView)
        b.setPositiveButton(R.string.action_close, null)
        if (scores.isNotEmpty()) {
            b.setNegativeButton(R.string.scores_clear) { _, _ ->
                Store.clearScores(this)
            }
        }
        b.show()
    }
}
