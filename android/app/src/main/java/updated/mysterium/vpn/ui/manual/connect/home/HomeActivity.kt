package updated.mysterium.vpn.ui.manual.connect.home

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import network.mysterium.AppNotificationManager
import network.mysterium.service.core.MysteriumAndroidCoreService
import network.mysterium.service.core.MysteriumCoreService
import network.mysterium.service.core.ProposalPaymentMoney
import network.mysterium.ui.DisplayMoneyOptions
import network.mysterium.ui.PriceUtils
import network.mysterium.vpn.R
import network.mysterium.vpn.databinding.ActivityHomeBinding
import network.mysterium.vpn.databinding.PopUpLostConnectionBinding
import network.mysterium.vpn.databinding.PopUpNodeFailedBinding
import org.koin.android.ext.android.inject
import updated.mysterium.vpn.common.extensions.getTypeLabel
import updated.mysterium.vpn.model.manual.connect.ConnectionState
import updated.mysterium.vpn.model.manual.connect.ConnectionStatistic
import updated.mysterium.vpn.model.manual.connect.Proposal
import updated.mysterium.vpn.ui.base.BaseActivity
import updated.mysterium.vpn.ui.manual.connect.select.node.SelectNodeActivity
import updated.mysterium.vpn.ui.manual.connect.select.node.all.AllNodesViewModel
import updated.mysterium.vpn.ui.menu.MenuActivity

class HomeActivity : BaseActivity() {

    companion object {
        const val EXTRA_PROPOSAL_MODEL = "PROPOSAL_MODEL"
        private const val TAG = "HomeActivity"
        private const val CURRENCY = "MYSTT"
        private const val SECONDS_PER_HOUR = 3600.0
        private const val BYTES_PER_GIGABYTE = 1024.0 * 1024.0 * 1024.0
    }

    private lateinit var binding: ActivityHomeBinding
    private lateinit var appNotificationManager: AppNotificationManager
    private var proposal: Proposal? = null
    private val viewModel: HomeViewModel by inject()
    private val allNodesViewModel: AllNodesViewModel by inject()
    private val deferredMysteriumCoreService = CompletableDeferred<MysteriumCoreService>()
    private var isDisconnectedByUser = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configure()
        subscribeViewModel()
        bindsAction()
    }

    override fun onResume() {
        super.onResume()
        checkCurrentStatus()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (isInternetAvailable()) {
            setIntent(intent)
            checkProposalArgument()
        } else {
            wifiNetworkErrorPopUp()
        }
    }

    private fun configure() {
        loadIpAddress()
        bindMysteriumService()
        initViewModel()
        allNodesViewModel.initProposals()
    }

    private fun subscribeViewModel() {
        viewModel.statisticsUpdate.observe(this, {
            updateStatistics(it)
        })
        viewModel.connectionState.observe(this, {
            handleConnectionChange(it)
        })
        viewModel.connectionException.observe(this, {
            disconnect()
            showFailedToConnectPopUp()
        })
    }

    private fun initViewModel() {
        viewModel.init(deferredMysteriumCoreService, appNotificationManager)
    }

    private fun handleConnectionChange(connection: ConnectionState) {
        when (connection) {
            ConnectionState.NOTCONNECTED -> disconnect()
            ConnectionState.CONNECTING -> inflateConnectingCardView()
            ConnectionState.CONNECTED -> {
                loadIpAddress()
                inflateConnectedCardView()
            }
            ConnectionState.DISCONNECTING -> {
                binding.connectionState.showDisconnectingState()
                checkDisconnectingReason()
            }
        }
    }

    private fun checkCurrentStatus() {
        viewModel.updateCurrentConnectionStatus().observe(this, { result ->
            result.onSuccess {
                if (it == ConnectionState.CONNECTED) {
                    getProposal()
                }
            }
            result.onFailure { throwable ->
                Log.i(TAG, throwable.localizedMessage ?: throwable.toString())
                // TODO("Implement error handling")
            }
        })
    }

    private fun checkDisconnectingReason() {
        if (!isDisconnectedByUser) {
            if (isInternetAvailable()) {
                showLostConnectionPopUp()
            } else {
                wifiNetworkErrorPopUp()
            }
        }
        isDisconnectedByUser = false
    }

    private fun getProposal() {
        viewModel.getProposalModel(deferredMysteriumCoreService).observe(this, { result ->
            result.onSuccess { proposal ->
                proposal?.let {
                    this.proposal = Proposal(proposal)
                    inflateNodeInfo()
                }
            }

            result.onFailure { throwable ->
                Log.i(TAG, throwable.localizedMessage ?: throwable.toString())
                // TODO("Implement error handling")
            }
        })
    }

    private fun updateStatistics(statistics: ConnectionStatistic) {
        binding.connectionState.updateConnectedStatistics(statistics, CURRENCY)
        val countryName = proposal?.countryName ?: "Unknown"
        val notificationTitle = getString(R.string.notification_title_connected, countryName)
        val tokensSpent = PriceUtils.displayMoney(
            ProposalPaymentMoney(
                amount = statistics.tokensSpent,
                currency = CURRENCY
            ),
            DisplayMoneyOptions(fractionDigits = 3, showCurrency = true)
        )
        val notificationContent = getString(
            R.string.notification_content,
            "${statistics.bytesReceived.value} ${statistics.bytesReceived.units}",
            "${statistics.bytesSent.value} ${statistics.bytesSent.units}",
            tokensSpent
        )
        viewModel.showStatisticsNotification(notificationTitle, notificationContent)
    }

    private fun checkProposalArgument() {
        intent.extras?.getParcelable<Proposal>(EXTRA_PROPOSAL_MODEL)?.let {
            proposal = it
            bindMysteriumService()
            viewModel.connectNode(it)
            inflateNodeInfo()
            inflateConnectingCardView()
        }
    }

    private fun bindMysteriumService() {
        appNotificationManager = AppNotificationManager(
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ).apply { init(this@HomeActivity) }
        Intent(this, MysteriumAndroidCoreService::class.java).also { intent ->
            bindService(
                intent,
                object : ServiceConnection {

                    override fun onServiceDisconnected(name: ComponentName?) {
                        Log.i(TAG, "Service disconnected")
                    }

                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        Log.i(TAG, "Service connected")
                        deferredMysteriumCoreService.complete(service as MysteriumCoreService)
                    }
                },
                Context.BIND_AUTO_CREATE
            )
        }
    }

    private fun loadIpAddress() {
        binding.ipTextView.text = getString(R.string.manual_connect_loading)
        viewModel.getLocation().observe(this, { result ->
            result.onSuccess {
                binding.ipTextView.text = it.ip
            }
            result.onFailure {
                Log.e(TAG, "Data loading failed")
                binding.ipTextView.text = getString(R.string.manual_connect_unknown)
                // TODO("Implement error handling")
            }
        })
    }

    private fun bindsAction() {
        binding.selectAnotherNodeButton.setOnClickListener {
            navigateToSelectNode()
        }
        binding.manualConnectToolbar.onLeftButtonClicked {
            navigateToMenu()
        }
        binding.manualConnectToolbar.onRightButtonClicked {
            proposal?.let { proposal ->
                viewModel.isFavourite(proposal.providerID + proposal.serviceType).observe(this, { result ->
                    result.onSuccess {
                        if (it != null) {
                            deleteFromFavourite(proposal)
                        } else {
                            addToFavourite(proposal)
                        }
                    }
                    result.onFailure {
                        Log.i(TAG, it.localizedMessage ?: it.toString())
                    }
                })
            }
        }
        binding.connectionState.initListeners(
            selectNodeManually = {
                navigateToSelectNode()
            },
            disconnect = {
                isDisconnectedByUser = true
                viewModel.disconnect()
            }
        )
    }

    private fun deleteFromFavourite(proposal: Proposal) {
        viewModel.deleteFromFavourite(proposal)
        binding.manualConnectToolbar.setRightIcon(
            ContextCompat.getDrawable(this, R.drawable.icon_save)
        )
    }

    private fun addToFavourite(proposal: Proposal) {
        viewModel.addToFavourite(proposal).observe(this, { result ->
            result.onSuccess {
                Log.i(TAG, "onSuccess")
            }
            result.onFailure {
                Log.i(TAG, it.localizedMessage ?: it.toString())
            }
        })
        binding.manualConnectToolbar.setRightIcon(
            ContextCompat.getDrawable(this, R.drawable.icon_saved)
        )
    }

    private fun disconnect() {
        binding.connectionState.showDisconnectedState()
        binding.connectedNodeInfo.visibility = View.INVISIBLE
        binding.titleTextView.text = getString(R.string.manual_connect_disconnected)
        binding.securityStatusTextView.visibility = View.INVISIBLE
        binding.securityStatusImageView.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.short_divider)
        )
        binding.manualConnectToolbar.unprotectedState()
        binding.multiAnimation.disconnectedState()
        binding.selectAnotherNodeButton.visibility = View.INVISIBLE
        binding.manualConnectToolbar.setRightIcon(null)
        loadIpAddress()
    }

    private fun inflateNodeInfo() {
        proposal?.let {
            binding.nodeType.text = it.nodeType.getTypeLabel()
            binding.nodeProvider.text = it.providerID
            // convert seconds to hours
            binding.pricePerHour.text = getString(
                R.string.manual_connect_price_per_hour,
                it.payment.rate.perSeconds / SECONDS_PER_HOUR
            )
            // convert price by bytes to price by gigabytes
            binding.pricePerGigabyte.text = getString(
                R.string.manual_connect_price_per_gigabyte,
                it.payment.rate.perBytes / BYTES_PER_GIGABYTE
            )
        }
    }

    private fun inflateConnectingCardView() {
        binding.selectAnotherNodeButton.visibility = View.VISIBLE
        binding.connectedNodeInfo.visibility = View.INVISIBLE
        binding.titleTextView.text = getString(R.string.manual_connect_disconnected)
        binding.securityStatusTextView.visibility = View.INVISIBLE
        binding.securityStatusImageView.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.short_divider)
        )
        binding.connectionTypeTextView.visibility = View.INVISIBLE
        binding.manualConnectToolbar.unprotectedState()
        binding.manualConnectToolbar.setRightIcon(null)
        proposal?.let {
            binding.connectionState.showConnectionState(it)
        }
        binding.multiAnimation.connectingState()
    }

    private fun inflateConnectedCardView() {
        binding.selectAnotherNodeButton.visibility = View.VISIBLE
        binding.connectionState.showConnectedState()
        binding.connectedNodeInfo.visibility = View.VISIBLE
        binding.titleTextView.text = getString(R.string.manual_connect_connected)
        binding.connectionTypeTextView.visibility = View.VISIBLE
        binding.connectionTypeTextView.text = proposal?.countryName ?: "UNKNOWN"
        binding.securityStatusTextView.visibility = View.VISIBLE
        binding.securityStatusImageView.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.shape_connected_status)
        )
        binding.manualConnectToolbar.protectedState(isFill = false)
        binding.connectionTypeTextView.setTextColor(
            ContextCompat.getColor(this, R.color.ColorWhite)
        )
        binding.multiAnimation.connectedState()
        isFavourite()
    }

    private fun isFavourite() {
        proposal?.let {
            viewModel.isFavourite(it.providerID + it.serviceType).observe(this, { result ->
                result.onSuccess { nodeEntity ->
                    if (nodeEntity != null) {
                        binding.manualConnectToolbar.setRightIcon(
                            ContextCompat.getDrawable(this, R.drawable.icon_saved)
                        )
                    } else {
                        binding.manualConnectToolbar.setRightIcon(
                            ContextCompat.getDrawable(this, R.drawable.icon_save)
                        )
                    }
                }
            })
        }
    }

    private fun showFailedToConnectPopUp() {
        val bindingPopUp = PopUpNodeFailedBinding.inflate(layoutInflater)
        val dialog = createPopUp(bindingPopUp.root, true)
        bindingPopUp.chooseAnother.setOnClickListener {
            dialog.dismiss()
            navigateToSelectNode()
        }
        dialog.show()
    }

    private fun showLostConnectionPopUp() {
        val bindingPopUp = PopUpLostConnectionBinding.inflate(layoutInflater)
        val dialog = createPopUp(bindingPopUp.root, true)
        bindingPopUp.closeButton.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun navigateToSelectNode() {
        startActivity(Intent(this, SelectNodeActivity::class.java))
    }

    private fun navigateToMenu() {
        startActivity(Intent(this, MenuActivity::class.java))
    }
}
