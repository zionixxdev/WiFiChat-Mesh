package com.wifichat.mesh.ui.onboarding

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.wifichat.mesh.R
import com.wifichat.mesh.databinding.ActivityOnboardingBinding
import com.wifichat.mesh.service.MeshService
import com.wifichat.mesh.ui.MainActivity
import com.wifichat.mesh.ui.container

/**
 * Short first-launch flow: welcome -> choose name -> explain permissions ->
 * start discovery. No email, phone number, cloud account or login is required,
 * and none is requested.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var step = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        renderStep()
        binding.primaryButton.setOnClickListener { advance() }
        binding.backButton.setOnClickListener {
            if (step > 0) { step--; renderStep() }
        }
    }

    private fun advance() {
        when (step) {
            1 -> {
                val name = binding.nameInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    binding.nameInput.error = getString(R.string.onboarding_name_required)
                    return
                }
                container.settings.setDisplayName(name)
            }
            3 -> {
                container.settings.setOnboardingDone()
                container.settings.setDiscoveryEnabled(true)
                MeshService.start(this)
                startActivity(android.content.Intent(this, MainActivity::class.java))
                finish()
                return
            }
        }
        step++
        renderStep()
    }

    private fun renderStep() {
        binding.backButton.isEnabled = step > 0
        binding.stepIndicator.text = getString(R.string.onboarding_step, step + 1, TOTAL_STEPS)
        binding.nameInputLayout.visibility =
            if (step == 1) android.view.View.VISIBLE else android.view.View.GONE

        when (step) {
            0 -> {
                binding.title.setText(R.string.onboarding_title_0)
                binding.body.setText(R.string.onboarding_body_0)
                binding.primaryButton.setText(R.string.onboarding_start)
            }
            1 -> {
                binding.title.setText(R.string.onboarding_title_1)
                binding.body.setText(R.string.onboarding_body_1)
                binding.primaryButton.setText(R.string.onboarding_continue)
            }
            2 -> {
                binding.title.setText(R.string.onboarding_title_2)
                binding.body.setText(R.string.onboarding_body_2)
                binding.primaryButton.setText(R.string.onboarding_grant)
            }
            else -> {
                binding.title.setText(R.string.onboarding_title_3)
                binding.body.setText(R.string.onboarding_body_3)
                binding.primaryButton.setText(R.string.onboarding_finish)
            }
        }
    }

    companion object {
        private const val TOTAL_STEPS = 4
    }
}
