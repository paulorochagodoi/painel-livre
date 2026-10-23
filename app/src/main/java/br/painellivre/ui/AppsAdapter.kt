package br.painellivre.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.painellivre.R
import java.text.Collator
import java.util.Locale

class AppsAdapter(private val onClick: (AppEntry) -> Unit) : RecyclerView.Adapter<AppsAdapter.Holder>() {

    data class AppEntry(val label: String, val component: ComponentName, val icon: Drawable)

    private var items: List<AppEntry> = emptyList()
    private var styler: (View) -> Unit = {}

    /** [style] pinta cada item no tema atual (dia/noite). */
    fun submit(list: List<AppEntry>, style: (View) -> Unit = {}) {
        items = list
        styler = style
        @Suppress("NotifyDataSetChanged") notifyDataSetChanged()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.appIcon)
        val label: TextView = v.findViewById(R.id.appLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: Holder, position: Int) {
        val app = items[position]
        styler(h.itemView)
        h.icon.setImageDrawable(app.icon)
        h.label.text = app.label
        h.itemView.setOnClickListener { onClick(app) }
    }

    companion object {
        fun loadApps(pm: PackageManager, selfPackage: String): List<AppEntry> {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val collator = Collator.getInstance(Locale("pt", "BR"))
            return pm.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != selfPackage }
                .map {
                    AppEntry(
                        it.loadLabel(pm).toString(),
                        ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                        it.loadIcon(pm),
                    )
                }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
        }
    }
}
