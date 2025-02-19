/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.Navigation
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mozilla.components.browser.icons.IconRequest
import mozilla.components.feature.sitepermissions.SitePermissions
import org.jetbrains.anko.alert
import org.jetbrains.anko.noButton
import org.jetbrains.anko.yesButton
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components
import kotlin.coroutines.CoroutineContext
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView

private const val MAX_ITEMS_PER_PAGE = 50

@SuppressWarnings("TooManyFunctions")
class SitePermissionsExceptionsFragment : Fragment(), View.OnClickListener, CoroutineScope {
    private lateinit var emptyContainerMessage: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var clearButton: Button
    private lateinit var job: Job
    override val coroutineContext: CoroutineContext get() = Dispatchers.IO + job

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.show()
        job = Job()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_site_permissions_exceptions, container, false)
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)
        bindEmptyContainerMess(rootView)
        bindClearButton(rootView)
        bindRecyclerView(rootView)
    }

    private fun bindRecyclerView(rootView: View) {
        recyclerView = rootView.findViewById(R.id.exceptions)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val sitePermissionsPaged = requireContext().components.core.permissionStorage.getSitePermissionsPaged()

        val adapter = ExceptionsAdapter(this)
        val liveData = LivePagedListBuilder(sitePermissionsPaged, MAX_ITEMS_PER_PAGE).build()

        liveData.observe(this, Observer<PagedList<SitePermissions>> {
            if (it.isEmpty()) {
                showEmptyListMessage()
            } else {
                hideEmptyListMessage()
                adapter.submitList(it)
                recyclerView.adapter = adapter
            }
        })
    }

    private fun hideEmptyListMessage() {
        emptyContainerMessage.visibility = GONE
        recyclerView.visibility = VISIBLE
        clearButton.visibility = VISIBLE
    }

    private fun showEmptyListMessage() {
        emptyContainerMessage.visibility = VISIBLE
        recyclerView.visibility = GONE
        clearButton.visibility = GONE
    }

    private fun bindEmptyContainerMess(rootView: View) {
        emptyContainerMessage = rootView.findViewById<View>(R.id.empty_exception_container)
    }

    private fun bindClearButton(rootView: View) {
        clearButton = rootView.findViewById(R.id.delete_all_site_permissions_button)
        clearButton.setOnClickListener {
            requireContext().alert(
                R.string.confirm_clear_permissions_on_all_sites,
                R.string.clear_permissions
            ) {
                yesButton {
                    deleteAllSitePermissions()
                }
                noButton { }
            }.show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun deleteAllSitePermissions() {
        launch(IO) {
            requireContext().components.core.permissionStorage.deleteAllSitePermissions()
            launch(Main) {
                showEmptyListMessage()
            }
        }
    }

    override fun onClick(view: View?) {
        val sitePermissions = view?.tag as SitePermissions
        val directions = SitePermissionsExceptionsFragmentDirections
            .actionSitePermissionsToExceptionsToSitePermissionsDetails(sitePermissions)
        Navigation.findNavController(requireNotNull(view)).navigate(directions)
    }
}

class SitePermissionsViewHolder(val view: View, val iconView: ImageView, val siteTextView: TextView) :
    RecyclerView.ViewHolder(view)

class ExceptionsAdapter(private val clickListener: View.OnClickListener) :
    PagedListAdapter<SitePermissions, SitePermissionsViewHolder>(diffCallback), CoroutineScope {
    private lateinit var job: Job

    override val coroutineContext: CoroutineContext
        get() = Main + job

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        job = Job()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        job.cancel()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SitePermissionsViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.fragment_site_permissions_exceptions_item, parent, false)
        val iconView = view.findViewById<ImageView>(R.id.exception_icon)
        val siteTextView = view.findViewById<TextView>(R.id.exception_text)
        return SitePermissionsViewHolder(view, iconView, siteTextView)
    }

    override fun onBindViewHolder(holder: SitePermissionsViewHolder, position: Int) {
        val sitePermissions = requireNotNull(getItem(position))
        val context = holder.view.context

        launch(IO) {

            val bitmap = context.components.core.icons
                .loadIcon(IconRequest("https://${sitePermissions.origin}/")).await().bitmap
            launch(Main) {
                val drawable = BitmapDrawable(context.resources, bitmap)
                holder.iconView.setImageDrawable(drawable)
            }
        }
        holder.siteTextView.text = sitePermissions.origin
        holder.view.tag = sitePermissions
        holder.view.setOnClickListener(clickListener)
    }

    companion object {

        private val diffCallback = object :
            DiffUtil.ItemCallback<SitePermissions>() {
            override fun areItemsTheSame(old: SitePermissions, new: SitePermissions) = old.origin == new.origin
            override fun areContentsTheSame(old: SitePermissions, new: SitePermissions) = old == new
        }
    }
}
