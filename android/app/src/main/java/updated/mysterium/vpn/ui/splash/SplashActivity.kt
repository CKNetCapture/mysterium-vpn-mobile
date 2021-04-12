package updated.mysterium.vpn.ui.splash

import android.animation.Animator
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred
import network.mysterium.service.core.MysteriumAndroidCoreService
import network.mysterium.service.core.MysteriumCoreService
import network.mysterium.vpn.R
import network.mysterium.vpn.databinding.ActivitySplashBinding
import org.koin.android.ext.android.inject
import updated.mysterium.vpn.common.animation.OnAnimationCompletedListener
import updated.mysterium.vpn.ui.balance.BalanceViewModel
import updated.mysterium.vpn.ui.base.BaseActivity
import updated.mysterium.vpn.ui.create.account.CreateAccountActivity
import updated.mysterium.vpn.ui.manual.connect.home.HomeActivity
import updated.mysterium.vpn.ui.onboarding.OnboardingActivity
import updated.mysterium.vpn.ui.terms.TermsOfUseActivity

class SplashActivity : BaseActivity() {

    companion object {
        private const val TAG = "SplashActivity"
    }

    private lateinit var binding: ActivitySplashBinding
    private val balanceViewModel: BalanceViewModel by inject()
    private val viewModel: SplashViewModel by inject()
    private val deferredMysteriumCoreService = CompletableDeferred<MysteriumCoreService>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configure()
        prepareNodeForStarting()
    }

    override fun retryLoading() {
        prepareNodeForStarting()
    }

    private fun prepareNodeForStarting() {
        if (isInternetAvailable()) {
            bindMysteriumService()
            subscribeViewModel()
            ensureVpnServicePermission()
        }
    }

    private fun configure() {
        binding.onceAnimationView.addAnimatorListener(object : OnAnimationCompletedListener() {

            override fun onAnimationEnd(animation: Animator?) {
                viewModel.animationLoaded()
                binding.onceAnimationView.visibility = View.GONE
                binding.onceAnimationView.cancelAnimation()
                binding.loopAnimationView.visibility = View.VISIBLE
                binding.loopAnimationView.playAnimation()
            }
        })
    }

    private fun subscribeViewModel() {
        viewModel.navigateForward.observe(this, {
            navigateForward()
        })
    }

    private fun navigateForward() {
        when {
            !viewModel.isUserAlreadyLogin() -> {
                startActivity(Intent(this, OnboardingActivity::class.java))
            }
            viewModel.isAccountFlowShown() -> {
                startActivity(Intent(this, HomeActivity::class.java))
            }
            viewModel.isTermsAccepted() -> {
                startActivity(Intent(this, CreateAccountActivity::class.java))
            }
            else -> {
                startActivity(Intent(this, TermsOfUseActivity::class.java))
            }
        }
        finish()
    }

    private fun ensureVpnServicePermission() {
        val vpnServiceIntent = VpnService.prepare(this)
        if (vpnServiceIntent == null) {
            startLoading()
        } else {
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    startLoading()
                } else {
                    showPermissionErrorToast()
                    finish()
                }
            }.launch(vpnServiceIntent)
        }
    }

    private fun showPermissionErrorToast() {
        Toast.makeText(
            this,
            getString(R.string.error_vpn_permission),
            Toast.LENGTH_LONG
        ).apply {
            (view.findViewById<View>(android.R.id.message) as TextView).gravity = Gravity.CENTER
        }.show()
    }

    private fun bindMysteriumService() {
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

    private fun startLoading() {
        balanceViewModel.initDeferredNode(deferredMysteriumCoreService)
        viewModel.startLoading(deferredMysteriumCoreService).observe(this) { result ->
            result.onSuccess {
                binding.onceAnimationView.playAnimation()
                viewModel.initRepository()
            }
        }
    }
}
