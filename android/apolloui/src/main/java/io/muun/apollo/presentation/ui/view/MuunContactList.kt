package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.model.Contact
import io.muun.apollo.domain.model.ContactsPermissionState
import io.muun.apollo.domain.model.P2PState
import io.muun.apollo.presentation.ui.adapter.ItemAdapter
import io.muun.apollo.presentation.ui.adapter.holder.ViewHolderFactory
import io.muun.apollo.presentation.ui.adapter.viewmodel.ContactViewModel

class MuunContactList @JvmOverloads constructor(c: Context, a: AttributeSet? = null, s: Int = 0):
    MuunView(c, a, s) {

    @BindView(R.id.contact_list_empty)
    internal lateinit var goToP2PSetupView: MuunEmptyScreen

    @BindView(R.id.contact_list_loading)
    internal lateinit var loadingView: LoadingView

    @BindView(R.id.contact_list_first_on_muun)
    internal lateinit var firstOnMuunView: FirstOnMuunView

    @BindView(R.id.contact_list_permission_denied_forever)
    internal lateinit var goToSettingsView: SimpleMessageView

    @BindView(R.id.contact_list_recycler)
    internal lateinit var listView: RecyclerView

    @BindView(R.id.contact_list_recycler_title)
    internal lateinit var listViewTitle: TextView

    private lateinit var listAdapter: ItemAdapter

    override fun getLayoutResource() =
        R.layout.muun_contact_list

    var onGoToSettingsListener: () -> Unit = {}
    var onGoToP2PSetupListener: () -> Unit = {}
    var onSelectListener: (contact: Contact) -> Unit = {}

    var state: P2PState? = null
        set(newState) {
            internalSetP2PState(newState)
            field = newState
        }

    override fun setUp(context: Context?, attrs: AttributeSet?) {
        super.setUp(context, attrs)

        goToP2PSetupView.setOnActionClickListener { onGoToP2PSetupListener() }
        goToP2PSetupView.setOnLinkClickListener { onGoToP2PSetupListener() }

        goToSettingsView.setOnActionClickListener { onGoToSettingsListener() }

        listAdapter = ItemAdapter(ViewHolderFactory()).apply {
            setOnItemClickListener { onSelectListener((it as ContactViewModel).model) }
        }

        listView.layoutManager = LinearLayoutManager(context)
        listView.addItemDecoration(DividerItemDecoration(context, 82, 0)) // 82, obviously
        listView.adapter = listAdapter
    }

    fun isShowingContacts() =
        listView.visibility == View.VISIBLE

    private fun internalSetP2PState(newP2PState: P2PState?) {
        // Reset visibility states:
        goToP2PSetupView.visibility = View.GONE
        goToSettingsView.visibility = View.GONE
        firstOnMuunView.visibility = View.GONE
        loadingView.visibility = View.GONE
        listView.visibility = View.GONE

        // Pick the new visible view:
        val visibleView = when {
            newP2PState == null ||
            !newP2PState.user.hasP2PEnabled ||
            newP2PState.permissionState == ContactsPermissionState.DENIED ->
                goToP2PSetupView

            newP2PState.permissionState == ContactsPermissionState.PERMANENTLY_DENIED ->
                goToSettingsView

            newP2PState.syncState.isLoading ->
                loadingView

            newP2PState.contacts.isEmpty() ->
                firstOnMuunView

            else ->
                listView
        }

        visibleView.visibility = View.VISIBLE
        listViewTitle.visibility = listView.visibility

        // Set the contact list items, if available:
        listAdapter.items = getFilteredContacts(newP2PState)?.map(::ContactViewModel) ?: listOf()
        listAdapter.notifyDataSetChanged()
    }

    /**
     * This is a TEMPORARY (yeah sure) quick and dirty hack to hide deprecated/deleted users.
     * In the future, we'll implement a proper solution for deleting and updating contacts.
     * For now, we just use the Unicode Character 'ZERO WIDTH SPACE' (U+200B) as a mark to hide
     * contacts representing deleted/deprecated users.
     */
    private fun getFilteredContacts(newP2PState: P2PState?): List<Contact>? {
        return newP2PState?.contacts
            ?.filter { contact ->
                !contact.publicProfile.firstName.contains("\u200B")
                    && !contact.publicProfile.lastName.contains("\u200B")
            }
    }
}