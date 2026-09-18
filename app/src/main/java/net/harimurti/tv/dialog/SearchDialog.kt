package net.harimurti.tv.dialog

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager

import net.harimurti.tv.R
import net.harimurti.tv.adapter.SearchAdapter
import net.harimurti.tv.databinding.SearchDialogBinding
import net.harimurti.tv.extension.isFavorite
import net.harimurti.tv.extension.setFullScreenFlags
import net.harimurti.tv.model.Channel
import net.harimurti.tv.model.PlayData
import net.harimurti.tv.model.Playlist


class SearchDialog :
    DialogFragment() {

    private var _binding:
            SearchDialogBinding? = null

    private val binding:
            SearchDialogBinding
        get() = _binding!!

    lateinit var searchAdapter:
            SearchAdapter


    override fun onStart() {

        super.onStart()

        dialog?.let {

            it.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            it.window?.setFullScreenFlags()
        }
    }


    override fun onCreateDialog(
        savedInstanceState: Bundle?
    ): Dialog {

        val dialog =
            AppCompatDialog(
                requireContext(),
                R.style.SettingsDialogThemeOverlay
            )

        dialog.setTitle(
            R.string.search_channel
        )

        dialog.setCanceledOnTouchOutside(
            false
        )

        return dialog
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding =
            SearchDialogBinding.inflate(
                inflater,
                container,
                false
            )


        val channels =
            ArrayList<Channel>()

        val listdata =
            ArrayList<PlayData>()


        val playlist =
            Playlist.cached


        for (
            catId in playlist
                .categories
                .indices
        ) {

            val cat =
                playlist.categories[catId]


            if (
                catId == 0 &&
                cat.isFavorite()
            ) {
                continue
            }


            val ch =
                cat.channels
                    ?: continue


            for (
                chId in ch.indices
            ) {

                channels.add(
                    ch[chId]
                )

                listdata.add(
                    PlayData(
                        catId,
                        chId
                    )
                )
            }
        }


        searchAdapter =
            SearchAdapter(
                channels,
                listdata
            )


        binding.searchAdapter =
            searchAdapter


        /*
         * FIX:
         *
         * Jangan memakai Context? di sini.
         * requireContext() menjamin Context non-null.
         */

        binding.searchList.layoutManager =
            GridLayoutManager(
                requireContext(),
                spanColumn()
            )


        binding.searchInput.apply {

            addTextChangedListener(
                object : TextWatcher {

                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }


                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                    }


                    override fun afterTextChanged(
                        s: Editable?
                    ) {

                        searchAdapter
                            .filter
                            .filter(s)


                        val hasText =
                            !s.isNullOrEmpty()


                        binding.searchList.visibility =
                            if (hasText)
                                View.VISIBLE
                            else
                                View.GONE


                        binding.searchReset.visibility =
                            if (hasText)
                                View.VISIBLE
                            else
                                View.GONE
                    }
                }
            )
        }


        binding.searchReset
            .setOnClickListener {

                binding.searchInput
                    .text
                    ?.clear()
            }


        binding.searchClose
            .setOnClickListener {

                dismiss()
            }


        return binding.root
    }


    override fun onDestroyView() {

        super.onDestroyView()

        _binding = null
    }


    private fun spanColumn(): Int {

        val screenWidthDp =
            resources.displayMetrics.widthPixels /
                    resources.displayMetrics.density


        return maxOf(
            1,
            (
                screenWidthDp / 150f +
                        0.5f
            ).toInt()
        )
    }
}
