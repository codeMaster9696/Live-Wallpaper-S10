package com.hub.livewallpaper.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hub.livewallpaper.R
import com.hub.livewallpaper.data.SourceKind
import com.hub.livewallpaper.data.Wallpaper
import com.hub.livewallpaper.databinding.ItemWallpaperBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Grade da galeria. ListAdapter + DiffUtil = animações de mudança grátis e
 * nenhum notifyDataSetChanged() manual.
 *
 * As miniaturas são carregadas em coroutine com verificação de "tag": se a
 * view for reciclada antes do bitmap chegar, o resultado é descartado em
 * vez de pintar o item errado (clássico bug de galeria com scroll rápido).
 */
class WallpaperAdapter(
    private val onSelected: (Wallpaper) -> Unit
) : ListAdapter<Wallpaper, WallpaperAdapter.VH>(DIFF) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var selectedId: String? = null
        set(value) {
            val old = field
            field = value
            if (old != value) notifyDataSetChanged()
        }

    inner class VH(val binding: ItemWallpaperBinding) : RecyclerView.ViewHolder(binding.root) {
        var job: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        return VH(ItemWallpaperBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val wp = getItem(position)
        val b = holder.binding
        val ctx = b.root.context

        b.title.text = wp.title
        b.origin.text = wp.origin.label.uppercase()
        b.origin.setTextColor(wp.origin.accent)
        b.kind.text = if (wp.kind == SourceKind.VIDEO) "MP4" else "GLSL"
        b.description.text = wp.description
        b.selectedMark.visibility = if (wp.id == selectedId) View.VISIBLE else View.GONE
        b.card.strokeColor = if (wp.id == selectedId) wp.origin.accent else ctx.getColor(R.color.stroke)
        b.card.strokeWidth = if (wp.id == selectedId) dp(b.root, 2) else dp(b.root, 1)

        holder.job?.cancel()
        b.thumb.setImageDrawable(null)
        val tag = wp.id
        b.thumb.tag = tag
        holder.job = scope.launch {
            val size = dp(b.root, 180)
            val bmp = Thumbnails.load(ctx, wp, size, (size * 16 / 9))
            if (b.thumb.tag == tag) b.thumb.setImageBitmap(bmp)
        }

        b.root.setOnClickListener { onSelected(wp) }
    }

    override fun onViewRecycled(holder: VH) {
        holder.job?.cancel()
        holder.job = null
        holder.binding.thumb.tag = null
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        scope.cancel()
    }

    private fun dp(v: View, value: Int): Int =
        (value * v.resources.displayMetrics.density).toInt()

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Wallpaper>() {
            override fun areItemsTheSame(a: Wallpaper, b: Wallpaper) = a.id == b.id
            override fun areContentsTheSame(a: Wallpaper, b: Wallpaper) = a == b
        }
    }
}
