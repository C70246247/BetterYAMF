package com.buildsession.betterYAMF.manager.adapter

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.RoundedDrawable
import com.buildsession.betterYAMF.R
import com.buildsession.betterYAMF.common.model.AppInfo
import com.buildsession.betterYAMF.databinding.ItemAppBinding
import com.buildsession.betterYAMF.manager.services.YAMFManagerProxy
import com.buildsession.betterYAMF.xposed.IAppIconCallback
import com.buildsession.betterYAMF.xposed.utils.componentName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppListAdapter (
    private val onClick: (AppInfo) -> Unit,
    private val appList: ArrayList<AppInfo>,
    private val onLongClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    fun setData(items: List<AppInfo>?) {
        appList.apply {
            clear()
            items?.let { addAll(it) }
        }
    }
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(appList[position])

    override fun getItemCount(): Int = appList.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val binding = ItemAppBinding.bind(itemView)
        private var currentAppInfo: AppInfo? = null

        fun bind(appInfo: AppInfo) {
            currentAppInfo = appInfo

            binding.tvLabel.text = ""
            binding.ivIcon.setImageDrawable(null)

            binding.ll.setOnClickListener {
                onClick(appInfo)
            }

            binding.ll.setOnLongClickListener {
                onLongClick(appInfo)
                true
            }

            CoroutineScope(Dispatchers.IO).launch {
                val pm = binding.root.context.packageManager
                val icon = try {
                    pm.getActivityIcon(appInfo.activityInfo.componentName)
                } catch (e: PackageManager.NameNotFoundException) {
                    ContextCompat.getDrawable(binding.root.context, R.drawable.work_icon)
                }

                val label = appInfo.activityInfo.loadLabel(pm)

                launch(Dispatchers.Main) {
                    if (currentAppInfo == appInfo) {
                        binding.ivIcon.setImageDrawable(icon)
                        binding.tvLabel.text = label
                    }
                }
            }
        }
    }
}
