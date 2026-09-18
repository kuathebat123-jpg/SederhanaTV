package net.harimurti.tv.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle

import androidx.fragment.app.DialogFragment

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

import androidx.media3.ui.TrackSelectionDialogBuilder

import net.harimurti.tv.R

@OptIn(UnstableApi::class)
class TrackSelectionDialog : DialogFragment() {

    private var trackSelector:
            DefaultTrackSelector? = null

    private var player:
            Player? = null

    private var dismissListener:
            DialogInterface.OnDismissListener? = null


    override fun onCreateDialog(
        savedInstanceState: Bundle?
    ): Dialog {

        val context =
            requireContext()

        val currentPlayer =
            player


        if (currentPlayer == null) {

            return AlertDialog.Builder(
                context
            )
                .setTitle(
                    R.string.track_selection_title
                )
                .setMessage(
                    R.string.track_selection_no_content
                )
                .setPositiveButton(
                    android.R.string.ok,
                    null
                )
                .create()
        }


        val availableTypes =
            mutableListOf<Int>()


        for (
            group in currentPlayer
                .currentTracks
                .groups
        ) {

            if (!group.isSupported) {
                continue
            }


            if (
                group.type ==
                    C.TRACK_TYPE_VIDEO ||

                group.type ==
                    C.TRACK_TYPE_AUDIO ||

                group.type ==
                    C.TRACK_TYPE_TEXT
            ) {

                if (
                    !availableTypes
                        .contains(group.type)
                ) {

                    availableTypes.add(
                        group.type
                    )
                }
            }
        }


        if (
            availableTypes.isEmpty()
        ) {

            return AlertDialog.Builder(
                context
            )
                .setTitle(
                    R.string.track_selection_title
                )
                .setMessage(
                    R.string.track_selection_no_content
                )
                .setPositiveButton(
                    android.R.string.ok,
                    null
                )
                .create()
        }


        val names =
            availableTypes
                .map { type ->

                    when (type) {

                        C.TRACK_TYPE_VIDEO ->
                            getString(
                                R.string.track_selection_video
                            )

                        C.TRACK_TYPE_AUDIO ->
                            getString(
                                R.string.track_selection_audio
                            )

                        C.TRACK_TYPE_TEXT ->
                            getString(
                                R.string.track_selection_text
                            )

                        else ->
                            type.toString()
                    }

                }
                .toTypedArray()


        return AlertDialog.Builder(
            context
        )
            .setTitle(
                R.string.track_selection_title
            )
            .setItems(
                names
            ) { _, which ->

                val type =
                    availableTypes[which]

                val title =
                    names[which]


                TrackSelectionDialogBuilder(
                    context,
                    title,
                    currentPlayer,
                    type
                )
                    .setAllowAdaptiveSelections(
                        type ==
                            C.TRACK_TYPE_VIDEO
                    )
                    .setAllowMultipleOverrides(
                        false
                    )
                    .setShowDisableOption(
                        type !=
                            C.TRACK_TYPE_VIDEO
                    )
                    .build()
                    .show()
            }
            .setNegativeButton(
                android.R.string.cancel,
                null
            )
            .create()
    }


    override fun onDismiss(
        dialog: DialogInterface
    ) {

        super.onDismiss(dialog)

        dismissListener
            ?.onDismiss(dialog)
    }


    companion object {

        fun willHaveContent(
            trackSelector:
                DefaultTrackSelector?
        ): Boolean {

            val info =
                trackSelector
                    ?.currentMappedTrackInfo
                    ?: return false


            for (
                i in 0 until
                    info.rendererCount
            ) {

                val type =
                    info.getRendererType(i)


                if (
                    type ==
                        C.TRACK_TYPE_VIDEO ||

                    type ==
                        C.TRACK_TYPE_AUDIO ||

                    type ==
                        C.TRACK_TYPE_TEXT
                ) {

                    if (
                        info
                            .getTrackGroups(i)
                            .length > 0
                    ) {

                        return true
                    }
                }
            }


            return false
        }


        fun createForTrackSelector(
            trackSelector:
                DefaultTrackSelector?,

            player:
                Player?,

            onDismissListener:
                DialogInterface.OnDismissListener
        ): TrackSelectionDialog {

            return TrackSelectionDialog().apply {

                this.trackSelector =
                    trackSelector

                this.player =
                    player

                this.dismissListener =
                    onDismissListener
            }
        }
    }
}
