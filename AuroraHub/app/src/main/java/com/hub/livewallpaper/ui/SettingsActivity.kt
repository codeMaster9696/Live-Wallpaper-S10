package com.hub.livewallpaper.ui

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.hub.livewallpaper.R
import com.hub.livewallpaper.data.Catalog
import com.hub.livewallpaper.data.Prefs
import com.hub.livewallpaper.data.RenderConfig
import com.hub.livewallpaper.data.ScaleMode
import com.hub.livewallpaper.data.Wallpaper
import com.hub.livewallpaper.databinding.ActivitySettingsBinding
import com.hub.livewallpaper.render.RenderSession

/**
 * Ajustes de renderização — abertos tanto pelo app quanto pelo botão de
 * engrenagem da prévia do sistema (res/xml/wallpaper.xml → settingsActivity).
 *
 * Cada movimento de slider grava em SharedPreferences e o Engine ativo
 * recebe o novo RenderConfig na hora: a prévia aqui e o wallpaper real
 * mudam juntos, sem reiniciar serviço.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs
    private var wallpaper: Wallpaper? = null
    private var updating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        binding.backButton.setOnClickListener { finish() }
        binding.preview.onStats = { st -> showStats(st) }
        binding.preview.onVideoFallback = {
            Snackbar.make(binding.root, R.string.video_missing, Snackbar.LENGTH_LONG).show()
        }

        // ---------------- modo de escala ----------------
        binding.scaleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || updating) return@addOnButtonCheckedListener
            prefs.scaleMode = if (checkedId == R.id.btnFill) ScaleMode.FILL else ScaleMode.FIT
            push()
        }

        // ---------------- sliders ----------------
        bindSeek(binding.speedSeek, binding.speedValue, RenderConfig.MIN_SPEED, RenderConfig.MAX_SPEED,
            { prefs.speed }, { prefs.speed = it }) { "%.2f×".format(it) }

        bindSeek(binding.overscanSeek, binding.overscanValue, 1f, 1.3f,
            { prefs.overscan }, { prefs.overscan = it }) { "%.2f×".format(it) }

        bindSeek(binding.brightnessSeek, binding.brightnessValue, 0.5f, 1.5f,
            { prefs.brightness }, { prefs.brightness = it }) { "%.0f%%".format(it * 100) }

        bindSeek(binding.saturationSeek, binding.saturationValue, 0f, 1.8f,
            { prefs.saturation }, { prefs.saturation = it }) { "%.0f%%".format(it * 100) }

        bindSeek(binding.grainSeek, binding.grainValue, 0f, 0.25f,
            { prefs.grain }, { prefs.grain = it }) { "%.0f%%".format(it * 400) }

        // ---------------- qualidade / fps ----------------
        binding.qualityGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || updating) return@addOnButtonCheckedListener
            prefs.quality = when (checkedId) {
                R.id.btnQ50 -> 0.5f
                R.id.btnQ100 -> 1.0f
                else -> 0.75f
            }
            push()
        }
        binding.fpsGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || updating) return@addOnButtonCheckedListener
            prefs.fpsCap = if (checkedId == R.id.btnFps30) 30 else 60
            push()
        }

        // ---------------- switches ----------------
        binding.muteSwitch.setOnCheckedChangeListener { _, checked ->
            if (updating) return@setOnCheckedChangeListener
            prefs.muteVideo = checked
            push()
        }
        binding.batterySwitch.setOnCheckedChangeListener { _, checked ->
            if (updating) return@setOnCheckedChangeListener
            prefs.pauseOnBatterySaver = checked
            push()
        }

        // ---------------- ações ----------------
        binding.resetButton.setOnClickListener {
            prefs.reset()
            syncUi()
            push()
            Snackbar.make(binding.root, R.string.reset, Snackbar.LENGTH_SHORT).show()
        }
        binding.randomButton.setOnClickListener {
            val all = Catalog.all(this)
            val next = all.filter { it.id != wallpaper?.id }.random()
            prefs.wallpaperId = next.id
            syncUi()
            push()
        }
        binding.applyButton.setOnClickListener {
            when (WallpaperApply.apply(this)) {
                WallpaperApply.Result.Direct -> Snackbar.make(binding.root,
                    getString(R.string.applied_ok, wallpaper?.originLabel ?: ""), Snackbar.LENGTH_LONG).show()
                WallpaperApply.Result.Chooser -> Snackbar.make(binding.root, R.string.applied_chooser, Snackbar.LENGTH_LONG).show()
                WallpaperApply.Result.Failed -> Snackbar.make(binding.root, R.string.applied_fail, Snackbar.LENGTH_LONG).show()
            }
        }

        syncUi()
        push()
    }

    override fun onStart() {
        super.onStart()
        binding.preview.setPaused(false)
    }

    override fun onStop() {
        super.onStop()
        binding.preview.setPaused(true)
    }

    // ------------------------------------------------------------------

    private fun bindSeek(
        seek: SeekBar,
        label: android.widget.TextView,
        min: Float,
        max: Float,
        getter: () -> Float,
        setter: (Float) -> Unit,
        format: (Float) -> String
    ) {
        seek.max = 1000
        seek.progress = (((getter() - min) / (max - min)) * 1000).toInt().coerceIn(0, 1000)
        label.text = format(getter())
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val value = min + (progress / 1000f) * (max - min)
                label.text = format(value)
                if (fromUser) {
                    setter(value)
                    push()
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    /** Espelha as preferências nos controles (sem disparar listeners). */
    private fun syncUi() {
        updating = true
        wallpaper = Catalog.byId(this, prefs.wallpaperId)
        binding.wallpaperName.text = wallpaper?.originLabel ?: ""
        binding.scaleGroup.check(if (prefs.scaleMode == ScaleMode.FILL) R.id.btnFill else R.id.btnFit)
        binding.qualityGroup.check(
            when {
                prefs.quality <= 0.55f -> R.id.btnQ50
                prefs.quality >= 0.95f -> R.id.btnQ100
                else -> R.id.btnQ75
            }
        )
        binding.fpsGroup.check(if (prefs.fpsCap <= 30) R.id.btnFps30 else R.id.btnFps60)
        binding.muteSwitch.isChecked = prefs.muteVideo
        binding.batterySwitch.isChecked = prefs.pauseOnBatterySaver

        binding.speedSeek.progress = (((prefs.speed - RenderConfig.MIN_SPEED) /
            (RenderConfig.MAX_SPEED - RenderConfig.MIN_SPEED)) * 1000).toInt().coerceIn(0, 1000)
        binding.speedValue.text = "%.2f×".format(prefs.speed)
        binding.overscanSeek.progress = (((prefs.overscan - 1f) / 0.3f) * 1000).toInt().coerceIn(0, 1000)
        binding.overscanValue.text = "%.2f×".format(prefs.overscan)
        binding.brightnessSeek.progress = (((prefs.brightness - 0.5f) / 1f) * 1000).toInt().coerceIn(0, 1000)
        binding.brightnessValue.text = "%.0f%%".format(prefs.brightness * 100)
        binding.saturationSeek.progress = ((prefs.saturation / 1.8f) * 1000).toInt().coerceIn(0, 1000)
        binding.saturationValue.text = "%.0f%%".format(prefs.saturation * 100)
        binding.grainSeek.progress = ((prefs.grain / 0.25f) * 1000).toInt().coerceIn(0, 1000)
        binding.grainValue.text = "%.0f%%".format(prefs.grain * 400)
        updating = false
    }

    private fun push() {
        val wp = Catalog.byId(this, prefs.wallpaperId)
        if (wallpaper?.id != wp.id) {
            wallpaper = wp
            binding.wallpaperName.text = wp.originLabel
        }
        binding.preview.setWallpaper(wp)
        binding.preview.setConfig(prefs.snapshot())
    }

    private fun showStats(st: RenderSession.Stats) {
        binding.diagText.text = buildString {
            append(st.backend).append(" · ").append(st.fps.toInt()).append(" fps\n")
            append("superfície ").append(st.surfaceW).append('×').append(st.surfaceH)
            if (st.mediaW > 0) append("  ·  mídia ").append(st.mediaW).append('×').append(st.mediaH)
            append('\n')
            append("escala ").append("%.3f".format(st.scale))
            append("  ·  corte ").append(st.cropX.toInt()).append('/').append(st.cropY.toInt()).append(" px\n")
            append("bordas ").append(st.letterboxX.toInt()).append('/').append(st.letterboxY.toInt()).append(" px")
            append("  ·  cobertura ").append("%.1f".format(st.coverage * 100)).append('%')
        }
        binding.diagText.setTextColor(getColor(if (st.coverage > 0.999f) R.color.success else R.color.warning))
        binding.coverageBadge.text = if (st.coverage > 0.999f) "SEM BORDAS" else "COM BORDAS"
        binding.coverageBadge.setTextColor(getColor(if (st.coverage > 0.999f) R.color.success else R.color.warning))
    }
}
