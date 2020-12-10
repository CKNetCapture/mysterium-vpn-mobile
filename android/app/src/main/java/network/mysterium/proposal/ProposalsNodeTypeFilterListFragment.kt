package network.mysterium.proposal

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import network.mysterium.MainApplication
import network.mysterium.navigation.Screen
import network.mysterium.ui.hideKeyboard
import network.mysterium.ui.BaseItem
import network.mysterium.ui.BaseListAdapter
import network.mysterium.ui.BaseViewHolder
import network.mysterium.navigation.navigateTo
import network.mysterium.navigation.onBackPress
import network.mysterium.vpn.R

class ProposalsNodeTypeFilterListFragment : Fragment() {

    private lateinit var listAdapter: BaseListAdapter
    private lateinit var toolbar: Toolbar
    private lateinit var proposalsViewModel: ProposalsViewModel
    private lateinit var resetBtn: MaterialButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_proposals_node_type_filter, container, false)
    }

    override fun onViewCreated(root: View, savedInstanceState: Bundle?) {
        super.onViewCreated(root, savedInstanceState)

        val appContainer = (requireActivity().application as MainApplication).appContainer
        proposalsViewModel = appContainer.proposalsViewModel

        toolbar = root.findViewById(R.id.proposals_node_type_filter_toolbar)
        resetBtn = root.findViewById(R.id.proposals_node_type_filter_reset_btn)

        toolbar.setNavigationOnClickListener {
            hideKeyboard(root)
            navigateTo(root, Screen.PROPOSALS)
        }

        resetBtn.setOnClickListener {
            proposalsViewModel.applyNodeTypeFilter(NodeType.ALL)
            navigateTo(root, Screen.PROPOSALS)
        }

        initList(root)

        onBackPress {
            navigateTo(root, Screen.PROPOSALS)
        }
    }

    private fun initList(root: View) {
        val listItems = proposalsViewModel.proposalsNodeTypes().map {
            val selected = proposalsViewModel.filter.nodeType == it
            NodeTypeItem(root.context, it, selected)
        }
        listAdapter = BaseListAdapter { clicked ->
            val item = clicked as NodeTypeItem?
            if (item != null) {
                proposalsViewModel.applyNodeTypeFilter(item.nodeType)
                navigateTo(root, Screen.PROPOSALS)
            }
        }

        val list: RecyclerView = root.findViewById(R.id.proposals_node_type_filter_list)
        list.adapter = listAdapter
        list.layoutManager = LinearLayoutManager(context)
        list.addItemDecoration(DividerItemDecoration(root.context, DividerItemDecoration.VERTICAL))
        listAdapter.submitList(listItems)
    }
}

data class NodeTypeItem(val ctx: Context, val nodeType: NodeType, val selected: Boolean) : BaseItem() {

    override val layoutId = R.layout.proposal_filter_node_type_item

    override val uniqueId = nodeType

    override fun bind(holder: BaseViewHolder) {
        super.bind(holder)
        val text: TextView = holder.containerView.findViewById(R.id.proposal_node_type_filter_item_text)
        text.text = when(nodeType) {
            NodeType.ALL -> ctx.getString(R.string.node_type_all)
            NodeType.BUSINESS -> ctx.getString(R.string.node_type_business)
            NodeType.CELLULAR -> ctx.getString(R.string.node_type_cellular)
            NodeType.HOSTING -> ctx.getString(R.string.node_type_hosting)
            NodeType.RESIDENTIAL -> ctx.getString(R.string.node_type_residential)
        }
        if (selected) {
            text.setTextColor(ctx.getColor(R.color.ColorMain))
        }
    }
}
