package com.wifichat.mesh.ui.settings

import android.content.Intent
import androidx.lifecycle.asLiveData
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.slider.Slider
import com.wifichat.mesh.R
import com.wifichat.mesh.databinding.FragmentSettingsBinding
import com.wifichat.mesh.repository.SettingsStore
import com.wifichat.mesh.ui.SimpleViewModelFactory
import com.wifichat.mesh.ui.container
import com.wifichat.mesh.util.Ids

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels {
        SimpleViewModelFactory { SettingsViewModel(container, requireContext().applicationContext) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.deviceTag.text = getString(R.string.device_tag_format, Ids.shortTag(viewModel.peerId))
        binding.version.text = getString(R.string.version_format, viewModel.versionName)
        binding.keystoreState.setText(
            if (viewModel.keystoreHardwareBacked) R.string.keystore_hardware
            else R.string.keystore_software
        )

        binding.nameInput.setText(viewModel.displayName.value)
        binding.saveNameButton.setOnClickListener {
            val name = binding.nameInput.text?.toString()?.trim().orEmpty()
            if (name.isNotEmpty()) {
                viewModel.setDisplayName(name)
                binding.nameInput.clearFocus()
            }
        }

        viewModel.themeMode.asLiveData().observe(viewLifecycleOwner) { mode ->
            binding.themeGroup.check(
                when (mode) {
                    SettingsStore.THEME_LIGHT -> R.id.theme_light
                    SettingsStore.THEME_SYSTEM -> R.id.theme_system
                    else -> R.id.theme_dark
                }
            )
        }
        binding.themeGroup.setOnCheckedChangeListener { _, id ->
            viewModel.setThemeMode(
                when (id) {
                    R.id.theme_light -> SettingsStore.THEME_LIGHT
                    R.id.theme_system -> SettingsStore.THEME_SYSTEM
                    else -> SettingsStore.THEME_DARK
                }
            )
            requireActivity().recreate()
        }

        binding.notificationsSwitch.isChecked = viewModel.notifications.value
        binding.notificationsSwitch.setOnCheckedChangeListener { _, checked ->
            viewModel.setNotifications(checked)
        }

        binding.discoverySwitch.isChecked = viewModel.discoveryEnabled.value
        binding.discoverySwitch.setOnCheckedChangeListener { _, checked ->
            viewModel.setDiscoveryEnabled(checked)
        }

        binding.readReceiptsSwitch.isChecked = viewModel.readReceipts.value
        binding.readReceiptsSwitch.setOnCheckedChangeListener { _, checked ->
            viewModel.setReadReceipts(checked)
        }

        binding.hopsSlider.valueFrom = 1f
        binding.hopsSlider.valueTo = 10f
        binding.hopsSlider.stepSize = 1f
        binding.hopsSlider.value = viewModel.maxHops.value.toFloat().coerceIn(1f, 10f)
        binding.hopsSlider.addOnChangeListener { _: Slider, value: Float, _: Boolean ->
            viewModel.setMaxHops(value.toInt())
            binding.hopsValue.text = getString(R.string.hops_format, value.toInt())
        }
        binding.hopsValue.text = getString(R.string.hops_format, viewModel.maxHops.value)

        binding.ttlSlider.valueFrom = 1f
        binding.ttlSlider.valueTo = 72f
        binding.ttlSlider.stepSize = 1f
        val hours = (viewModel.messageTtlMs.value / 3_600_000L).toInt().coerceIn(1, 72)
        binding.ttlSlider.value = hours.toFloat()
        binding.ttlValue.text = getString(R.string.ttl_format, hours)
        binding.ttlSlider.addOnChangeListener { _: Slider, value: Float, _: Boolean ->
            viewModel.setMessageTtlMs(value.toLong() * 3_600_000L)
            binding.ttlValue.text = getString(R.string.ttl_format, value.toInt())
        }

        binding.clearDataButton.setOnClickListener { confirmClear() }

        binding.exportButton.setOnClickListener {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, viewModel.exportDiagnostics())
            }
            startActivity(Intent.createChooser(share, getString(R.string.export_diagnostics)))
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_data_title)
            .setMessage(R.string.clear_data_message)
            .setPositiveButton(R.string.clear_data_confirm) { _, _ ->
                viewModel.clearLocalData {
                    requireActivity().finishAffinity()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
