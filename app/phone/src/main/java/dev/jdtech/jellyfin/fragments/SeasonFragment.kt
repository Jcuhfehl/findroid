package dev.jdtech.jellyfin.fragments

import android.app.DownloadManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.adapters.EpisodeListAdapter
import dev.jdtech.jellyfin.databinding.FragmentSeasonBinding
import dev.jdtech.jellyfin.dialogs.ErrorDialogFragment
import dev.jdtech.jellyfin.dialogs.getStorageSelectionDialog
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.PlayerItem
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.utils.checkIfLoginRequired
import dev.jdtech.jellyfin.viewmodels.PlayerViewModel
import dev.jdtech.jellyfin.viewmodels.SeasonViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class SeasonFragment : Fragment() {

    private lateinit var binding: FragmentSeasonBinding
    private val viewModel: SeasonViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val args: SeasonFragmentArgs by navArgs()

    private lateinit var errorDialog: ErrorDialogFragment
    private lateinit var downloadPreparingDialog: AlertDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentSeasonBinding.inflate(inflater, container, false)
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(dev.jdtech.jellyfin.core.R.menu.season_menu, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        dev.jdtech.jellyfin.core.R.id.action_download_season -> {
                            if (requireContext().getExternalFilesDirs(null).filterNotNull().size > 1) {
                                val storageDialog = getStorageSelectionDialog(
                                    requireContext(),
                                    onItemSelected = { storageIndex ->
                                        createDownloadPreparingDialog()
                                        viewModel.download(storageIndex = storageIndex)
                                    },
                                    onCancel = {
                                    },
                                )
                                storageDialog.show()
                                return true
                            }
                            createDownloadPreparingDialog()
                            viewModel.download()
                            return true
                        }
                        else -> false
                    }
                }
            },
            viewLifecycleOwner, Lifecycle.State.RESUMED
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { uiState ->
                        Timber.d("$uiState")
                        when (uiState) {
                            is SeasonViewModel.UiState.Normal -> bindUiStateNormal(uiState)
                            is SeasonViewModel.UiState.Loading -> bindUiStateLoading()
                            is SeasonViewModel.UiState.Error -> bindUiStateError(uiState)
                        }
                    }
                }

                launch {

                    viewModel.downloadStatus.collect { (status, progress) ->
                        when (status) {
                            10 -> {
                                downloadPreparingDialog.dismiss()
                            }
                            /* TODO Fix the ui feedback for this stuff
                            DownloadManager.STATUS_PENDING -> {
                                binding.itemActions.downloadButton.setIconResource(android.R.color.transparent)
                                binding.itemActions.progressDownload.isIndeterminate = true
                                binding.itemActions.progressDownload.isVisible = true
                            }

                            DownloadManager.STATUS_RUNNING -> {
                                binding.itemActions.downloadButton.setIconResource(android.R.color.transparent)
                                binding.itemActions.progressDownload.isVisible = true
                                if (progress < 5) {
                                    binding.itemActions.progressDownload.isIndeterminate = true
                                } else {
                                    binding.itemActions.progressDownload.isIndeterminate = false
                                    binding.itemActions.progressDownload.setProgressCompat(progress, true)
                                }
                            }
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                binding.itemActions.downloadButton.setIconResource(dev.jdtech.jellyfin.core.R.drawable.ic_trash)
                                binding.itemActions.progressDownload.isVisible = false
                            }
                            else -> {
                                binding.itemActions.progressDownload.isVisible = false
                                binding.itemActions.downloadButton.setIconResource(dev.jdtech.jellyfin.core.R.drawable.ic_download)
                            } */
                        }
                    }
                }

                launch {
                    viewModel.downloadError.collect { uiText ->
                        createErrorDialog(uiText)
                    }
                }

                launch {
                    viewModel.navigateBack.collect {
                        if (it) findNavController().navigateUp()
                    }
                }
            }
        }

        binding.errorLayout.errorRetryButton.setOnClickListener {
            viewModel.loadEpisodes(args.seriesId, args.seasonId, args.offline)
        }

        playerViewModel.onPlaybackRequested(lifecycleScope) { playerItems ->
            when (playerItems) {
                is PlayerViewModel.PlayerItems -> {
                    navigateToPlayerActivity(playerItems.items.toTypedArray())
                }
                is PlayerViewModel.PlayerItemError -> {}
            }
        }

        binding.errorLayout.errorDetailsButton.setOnClickListener {
            errorDialog.show(parentFragmentManager, ErrorDialogFragment.TAG)
        }

        binding.episodesRecyclerView.adapter =
            EpisodeListAdapter(
                EpisodeListAdapter.OnClickListener { episode ->
                    navigateToEpisodeBottomSheetFragment(episode)
                },
            )
    }

    override fun onResume() {
        super.onResume()

        viewModel.loadEpisodes(args.seriesId, args.seasonId, args.offline)
    }

    private fun bindUiStateNormal(uiState: SeasonViewModel.UiState.Normal) {
        uiState.apply {
            val adapter = binding.episodesRecyclerView.adapter as EpisodeListAdapter
            adapter.submitList(uiState.episodes)
        }
        binding.loadingIndicator.isVisible = false
        binding.episodesRecyclerView.isVisible = true
        binding.errorLayout.errorPanel.isVisible = false
    }

    private fun bindUiStateLoading() {
        binding.loadingIndicator.isVisible = true
        binding.errorLayout.errorPanel.isVisible = false
    }

    private fun bindUiStateError(uiState: SeasonViewModel.UiState.Error) {
        errorDialog = ErrorDialogFragment.newInstance(uiState.error)
        binding.loadingIndicator.isVisible = false
        binding.episodesRecyclerView.isVisible = false
        binding.errorLayout.errorPanel.isVisible = true
        checkIfLoginRequired(uiState.error.message)
    }

    private fun createDownloadPreparingDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext())
        downloadPreparingDialog = builder
            .setTitle(dev.jdtech.jellyfin.core.R.string.preparing_download)
            .setView(R.layout.preparing_download_dialog)
            .setCancelable(false)
            .create()
        downloadPreparingDialog.show()
    }

    private fun createErrorDialog(uiText: UiText) {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder
            .setTitle(dev.jdtech.jellyfin.core.R.string.downloading_error)
            .setMessage(uiText.asString(requireContext().resources))
            .setPositiveButton(getString(dev.jdtech.jellyfin.core.R.string.close)) { _, _ ->
            }
        builder.show()
    }

    private fun navigateToEpisodeBottomSheetFragment(episode: FindroidEpisode) {
        findNavController().navigate(
            SeasonFragmentDirections.actionSeasonFragmentToEpisodeBottomSheetFragment(
                episode.id,
            ),
        )
    }

    private fun navigateToPlayerActivity(
        playerItems: Array<PlayerItem>,
    ) {
        findNavController().navigate(
            SeasonFragmentDirections.actionSeasonFragmentToPlayerActivity(
                playerItems,
            ),
        )
    }
}
