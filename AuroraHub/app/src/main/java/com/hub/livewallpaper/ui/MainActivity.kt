package com.hub.livewallpaper.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.hub.livewallpaper.R
import com.hub.livewallpaper.data.Catalog
import com.hub.livewallpaper.data.Origin
import com.hub.livewallpaper.data.Prefs
import com.hub.livewallpaper.data.Wallpaper
import com.hub.livewallpaper.databinding.ActivityMainBinding
import com.hub.livewallpaper.render.RenderSession

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: WallpaperAdapter

    private var catalog: List<Wallpaper> = emptyList()
    private var selected: Wallpaper? = null
    private var filter: Origin? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { refreshFromPrefs() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        // catalog = Catalog.all(this) -> Movido para requestPermissionsIfNeeded

        adapter = WallpaperAdapter { wp -> select(wp, persist = true) }
        binding.grid.layoutManager = GridLayoutManager(this, 2)
        binding.grid.adapter = adapter
        binding.grid.setHasFixedSize(true)

        binding.preview.onStats = { st -> showStats(st) }
        binding.preview.onVideoFallback = {
            Snackbar.make(binding.root, R.string.video_missing, Snackbar.LENGTH_LONG).show()
        }

        binding.chipAll.setOnClickListener { setFilter(null) }
        binding.chipOxygen.setOnClickListener { setFilter(Origin.OXYGENOS) }
        binding.chipIos.setOnClickListener { setFilter(Origin.IOS) }
        binding.chipColoros.setOnClickListener { setFilter(Origin.COLOROS) }

        binding.applyButton.setOnClickListener { applySelected() }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.randomButton.setOnClickListener {
            val pool = visibleList()
            if (pool.isNotEmpty()) select(pool.random(), persist = true)
        }

        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 101)
        } else {
            loadCatalog()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        loadCatalog() // Carrega o catálogo mesmo se negar (para mostrar os internos)
    }

    private fun loadCatalog() {
        catalog = Catalog.all(this)
        binding.subtitle.text = getString(R.string.gallery_subtitle, catalog.size)
        select(Catalog.byId(this, prefs.wallpaperId), persist = false)
        setFilter(null)
    }

    override fun onStart() {
        super.onStart()
        binding.preview.setPaused(false)
        prefs.register(prefListener)
        refreshFromPrefs()
    }

    override fun onStop() {
        super.onStop()
        binding.preview.setPaused(true)
        prefs.unregister(prefListener)
    }

    private fun visibleList(): List<Wallpaper> =
        if (filter == null) catalog else catalog.filter { it.origin == filter }

    private fun setFilter(origin: Origin?) {
        filter = origin
        binding.chipAll.isChecked = origin == null
        binding.chipOxygen.isChecked = origin == Origin.OXYGENOS
        binding.chipIos.isChecked = origin == Origin.IOS
        binding.chipColoros.isChecked = origin == Origin.COLOROS
        adapter.submitList(visibleList())
    }

    private fun select(wp: Wallpaper, persist: Boolean) {
        selected = wp
        if (persist) prefs.wallpaperId = wp.id
        adapter.selectedId = wp.id
        binding.preview.setWallpaper(wp)
        binding.preview.setConfig(prefs.snapshot())
        binding.selectedTitle.text = wp.originLabel
        binding.selectedMeta.text = if (wp.isVideo) "MP4 · loop · center-crop" else "GLSL · procedural · center-crop"
        binding.previewBadge.text = wp.origin.label
        binding.previewBadge.setTextColor(wp.origin.accent)
    }

    private fun refreshFromPrefs() {
        val wp = Catalog.byId(this, prefs.wallpaperId)
        if (selected?.id != wp.id) select(wp, persist = false)
        binding.preview.setConfig(prefs.snapshot())
        val active = WallpaperApply.isAlreadyActive(this)
        binding.activeBadge.visibility = if (active) View.VISIBLE else View.GONE
        binding.applyButton.setText(if (active) R.string.apply_both else R.string.apply)
    }

    private fun applySelected() {
        val wp = selected ?: return
        prefs.wallpaperId = wp.id
        binding.preview.setWallpaper(wp)
        when (WallpaperApply.apply(this)) {
            WallpaperApply.Result.Direct ->
                Snackbar.make(binding.root, getString(R.string.applied_ok, wp.originLabel), Snackbar.LENGTH_LONG).show()
            WallpaperApply.Result.Chooser ->
                Snackbar.make(binding.root, R.string.applied_chooser, Snackbar.LENGTH_LONG)
                    .setAction(R.string.open_settings) {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }.show()
            WallpaperApply.Result.Failed ->
                Snackbar.make(binding.root, R.string.applied_fail, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showStats(st: RenderSession.Stats) {
        binding.stats.text = buildString {
            append("PRÉVIA ").append(st.surfaceW).append('×').append(st.surfaceH)
            append("  ·  ").append(st.fps.toInt()).append(" fps")
            append("  ·  ").append(st.backend)
            append('\n')
            append("escala ").append("%.2f".format(st.scale))
            append("  ·  corte ").append(st.cropX.toInt()).append('/').append(st.cropY.toInt()).append(" px")
            append("  ·  bordas ").append(st.letterboxX.toInt()).append('/').append(st.letterboxY.toInt()).append(" px")
            append("  ·  cobertura ").append("%.0f".format(st.coverage * 100)).append('%')
        }
        binding.stats.setTextColor(
            getColor(if (st.coverage > 0.999f) R.color.success else R.color.warning)
        )
    }
}
