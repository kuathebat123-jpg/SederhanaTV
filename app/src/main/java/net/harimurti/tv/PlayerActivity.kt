package net.harimurti.tv

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.view.animation.AlphaAnimation
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.localbroadcastmanager.content.LocalBroadcastManager

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks

import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer

import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallback

import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource

import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector
import androidx.media3.ui.PlayerView

import net.harimurti.tv.databinding.ActivityPlayerBinding
import net.harimurti.tv.databinding.CustomControlBinding
import net.harimurti.tv.dialog.TrackSelectionDialog
import net.harimurti.tv.extension.*
import net.harimurti.tv.extra.*
import net.harimurti.tv.model.Category
import net.harimurti.tv.model.Channel
import net.harimurti.tv.model.PlayData
import net.harimurti.tv.model.Playlist

import kotlin.math.ceil


class PlayerActivity : AppCompatActivity() {

    private var doubleBackToExitPressedOnce = false

    private var isTelevision = UiMode().isTelevision()

    private val preferences = Preferences()

    private val network = Network()

    private var category: Category? = null

    private var current: Channel? = null

    /**
     * Media3 ExoPlayer.
     */
    private var player: ExoPlayer? = null

    /**
     * MediaSource aktif.
     */
    private lateinit var mediaSource: MediaSource

    /**
     * Track selector Media3.
     */
    private lateinit var trackSelector: DefaultTrackSelector

    /**
     * Media3 memakai Tracks, bukan TrackGroupArray.
     */
    private var lastSeenTracks: Tracks? = null

    private lateinit var bindingRoot: ActivityPlayerBinding

    private lateinit var bindingControl: CustomControlBinding

    private var handlerInfo: Handler? = null

    private var errorCounter = 0

    private var isLocked = false

    private var isControllerVisible = false

    private val broadcastReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent
            ) {

                when (
                    intent.getStringExtra(
                        PLAYER_CALLBACK
                    )
                ) {

                    RETRY_PLAYBACK ->
                        retryPlayback(true)

                    CLOSE_PLAYER ->
                        finish()
                }
            }
        }


    companion object {

        var isFirst = true

        var isPipMode = false

        const val PLAYER_CALLBACK =
            "PLAYER_CALLBACK"

        const val RETRY_PLAYBACK =
            "RETRY_PLAYBACK"

        const val CLOSE_PLAYER =
            "CLOSE_PLAYER"

        private const val CHANNEL_NEXT = 0

        private const val CHANNEL_PREVIOUS = 1

        private const val CATEGORY_UP = 2

        private const val CATEGORY_DOWN = 3
    }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        bindingRoot =
            ActivityPlayerBinding.inflate(
                layoutInflater
            )

        bindingControl =
            CustomControlBinding.bind(
                bindingRoot.root.findViewById(
                    R.id.custom_control
                )
            )

        setContentView(bindingRoot.root)

        isFirst = false


        /*
         * ==========================================
         * VERIFY PLAYLIST
         * ==========================================
         */

        if (Playlist.cached.isCategoriesEmpty()) {

            Log.e(
                "PLAYER",
                getString(
                    R.string.player_no_playlist
                )
            )

            Toast.makeText(
                this,
                R.string.player_no_playlist,
                Toast.LENGTH_SHORT
            ).show()

            finish()

            return
        }


        /*
         * ==========================================
         * GET CATEGORY + CHANNEL
         * ==========================================
         */

        try {

            @Suppress("DEPRECATION")
            val parcel: PlayData? =
                intent.getParcelableExtra(
                    PlayData.VALUE
                )

            category =
                parcel?.let {

                    Playlist.cached.categories[
                        it.catId
                    ]
                }

            current =
                parcel?.let {

                    category
                        ?.channels
                        ?.get(it.chId)
                }

        } catch (e: Exception) {

            Log.e(
                "PLAYER",
                getString(
                    R.string.player_playdata_error
                ),
                e
            )

            Toast.makeText(
                this,
                R.string.player_playdata_error,
                Toast.LENGTH_SHORT
            ).show()

            finish()

            return
        }


        /*
         * ==========================================
         * VERIFY CHANNEL
         * ==========================================
         */

        if (
            category == null ||
            current == null
        ) {

            Log.e(
                "PLAYER",
                getString(
                    R.string.player_no_channel
                )
            )

            Toast.makeText(
                this,
                R.string.player_no_channel,
                Toast.LENGTH_SHORT
            ).show()

            finish()

            return
        }


        /*
         * ==========================================
         * BIND UI
         * ==========================================
         */

        bindingListener()


        /*
         * ==========================================
         * PLAY CHANNEL
         * ==========================================
         */

        playChannel()


        /*
         * ==========================================
         * BROADCAST
         * ==========================================
         */

        LocalBroadcastManager
            .getInstance(this)
            .registerReceiver(
                broadcastReceiver,
                IntentFilter(
                    PLAYER_CALLBACK
                )
            )
    }


    /*
     * ============================================================
     * UI LISTENER
     * ============================================================
     */

    private fun bindingListener() {

        bindingRoot.playerView.setOnTouchListener(
            object : OnSwipeTouchListener(
                bindingRoot.playerView
            ) {

                override fun onSwipeDown() {
                    switchChannel(
                        CATEGORY_UP
                    )
                }

                override fun onSwipeUp() {
                    switchChannel(
                        CATEGORY_DOWN
                    )
                }

                override fun onSwipeLeft() {
                    switchChannel(
                        CHANNEL_NEXT
                    )
                }

                override fun onSwipeRight() {
                    switchChannel(
                        CHANNEL_PREVIOUS
                    )
                }

                override fun onTapDoubleLeft(
                    click: Int
                ) {
                    doubleTapLeft(click)
                }

                override fun onTapDoubleRight(
                    click: Int
                ) {
                    doubleTapRight(click)
                }

                override fun onTapDoubleFinish(
                    click: Int,
                    isLeft: Boolean
                ) {
                    doubleTapFinish(
                        click,
                        isLeft
                    )
                }
            }
        )

        bindingRoot.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->

                isControllerVisible =
                    visibility == View.VISIBLE

                setChannelInformation(
                    visibility == View.VISIBLE
                )
            }
        )


        bindingControl.trackSelection
            .setOnClickListener {

                showTrackSelector()
            }


        bindingControl.buttonExit.apply {

            visibility =
                if (isTelevision)
                    View.GONE
                else
                    View.VISIBLE

            setOnClickListener {
                finish()
            }
        }


        bindingControl.buttonPrevious
            .setOnClickListener {

                switchChannel(
                    CHANNEL_PREVIOUS
                )
            }


        bindingControl.buttonRewind
            .setOnClickListener {

                player?.seekBack()
            }


        bindingControl.buttonForward
            .setOnClickListener {

                player?.seekForward()
            }


        bindingControl.buttonNext
            .setOnClickListener {

                switchChannel(
                    CHANNEL_NEXT
                )
            }


        bindingControl.screenMode
            .setOnClickListener {

                showMenu(it)
            }


        bindingControl.buttonLock.apply {

            visibility =
                if (isTelevision)
                    View.GONE
                else
                    View.VISIBLE

            setOnClickListener {

                if (!isLocked) {

                    (
                        it as ImageButton
                    ).setImageResource(
                        R.drawable.ic_lock
                    )

                    lockControl(true)
                }
            }

            setOnLongClickListener {

                val resId =
                    if (isLocked)
                        R.drawable.ic_lock_open
                    else
                        R.drawable.ic_lock

                (
                    it as ImageButton
                ).setImageResource(
                    resId
                )

                lockControl(
                    !isLocked
                )

                true
            }
        }


        bindingControl.buttonVolume
            .setOnClickListener {

                showVolumeMenu()
            }


        isMute(
            bindingControl.buttonVolume
        )
    }


    /*
     * ============================================================
     * DOUBLE TAP SEEK
     * ============================================================
     */

    @SuppressLint("SetTextI18n")
    private fun doubleTapLeft(
        clicks: Int
    ) {

        if (
            player?.isCurrentWindowLive == false
        ) {

            bindingRoot.seekBack.text =
                "- ${
                    timeToString(
                        (clicks * 10).toDouble()
                    )
                }"

            bindingRoot.seekBack.alpha = 1f

            val seekAnimation =
                AlphaAnimation(
                    0f,
                    1f
                )

            seekAnimation.fillAfter = true

            bindingRoot.seekBack
                .startAnimation(
                    seekAnimation
                )
        }
    }


    @SuppressLint("SetTextI18n")
    private fun doubleTapRight(
        clicks: Int
    ) {

        if (
            player?.isCurrentWindowLive == false
        ) {

            bindingRoot.seekForward.text =
                "+ ${
                    timeToString(
                        (clicks * 10).toDouble()
                    )
                }"

            bindingRoot.seekForward.alpha = 1f

            val seekAnimation =
                AlphaAnimation(
                    0f,
                    1f
                )

            seekAnimation.fillAfter = true

            bindingRoot.seekForward
                .startAnimation(
                    seekAnimation
                )
        }
    }


    private fun doubleTapFinish(
        clicks: Int,
        isLeft: Boolean
    ) {

        if (
            player?.isCurrentWindowLive == false
        ) {

            val click =
                if (isLeft)
                    clicks * -1
                else
                    clicks

            val seekAnimation =
                AlphaAnimation(
                    1f,
                    0f
                )

            seekAnimation.duration = 1800

            seekAnimation.fillAfter = true

            if (isLeft) {

                bindingRoot.seekBack
                    .startAnimation(
                        seekAnimation
                    )

            } else {

                bindingRoot.seekForward
                    .startAnimation(
                        seekAnimation
                    )
            }

            seekTime(
                (click * 10000).toLong()
            )
        }
    }


    private fun seekTime(
        time: Long
    ) {

        val currentPosition =
            player?.currentPosition
                ?: return

        val duration =
            player?.duration
                ?: return

        if (duration <= 0) {
            return
        }

        player?.seekTo(
            maxOf(
                minOf(
                    currentPosition + time,
                    duration
                ),
                0
            )
        )
    }


    private fun timeToString(
        time: Double
    ): String {

        val second =
            time.toInt()

        val rsec =
            second % 60

        val minute =
            ceil(
                (second - rsec) / 60.0
            ).toInt()

        val rmin =
            minute % 60

        val hour =
            ceil(
                (minute - rmin) / 60.0
            ).toInt()

        return (
            if (hour > 0)
                forceTwoDigit(hour) + ":"
            else
                ""
        ) +
                (
                    if (
                        rmin >= 0 ||
                        hour >= 0
                    )
                        forceTwoDigit(rmin) + ":"
                    else
                        ""
                ) +
                forceTwoDigit(rsec)
    }


    private fun forceTwoDigit(
        inp: Int,
        length: Int = 2
    ): String {

        val added =
            length -
                    inp.toString().length

        return if (added > 0) {

            "0".repeat(added) +
                    inp.toString()

        } else {

            inp.toString()
        }
    }


    /*
     * ============================================================
     * CHANNEL INFORMATION
     * ============================================================
     */

    private fun setChannelInformation(
        visible: Boolean
    ) {

        if (isLocked) {
            return
        }

        bindingRoot.layoutInfo.visibility =
            if (
                visible &&
                !isPipMode
            )
                View.VISIBLE
            else
                View.INVISIBLE

        bindingControl.volumeLayout.visibility =
            if (
                visible ||
                isPipMode
            )
                View.INVISIBLE
            else
                View.VISIBLE

        if (isPipMode) {
            return
        }

        if (
            visible ==
            isControllerVisible
        ) {
            return
        }

        if (visible) {

            bindingRoot.playerView
                .clearFocus()

        } else {

            return
        }

        if (handlerInfo == null) {

            handlerInfo =
                Handler(
                    Looper.getMainLooper()
                )
        }

        handlerInfo
            ?.removeCallbacksAndMessages(
                null
            )

        handlerInfo?.postDelayed({

            if (
                isControllerVisible
            ) {
                return@postDelayed
            }

            bindingRoot.layoutInfo.visibility =
                View.INVISIBLE

        },
            bindingRoot.playerView
                .controllerShowTimeoutMs
                .toLong()
        )
    }


    /*
     * ============================================================
     * LOCK CONTROL
     * ============================================================
     */

    private fun lockControl(
        setLocked: Boolean
    ) {

        isLocked = setLocked

        val visibility =
            if (setLocked)
                View.INVISIBLE
            else
                View.VISIBLE

        bindingRoot.layoutInfo.visibility =
            visibility

        bindingControl.buttonExit.visibility =
            visibility

        bindingControl.layoutControl.visibility =
            visibility

        bindingControl.screenMode.visibility =
            visibility

        bindingControl.trackSelection.visibility =
            visibility

        switchLiveOrVideo()
    }


    private fun switchLiveOrVideo() {

        switchLiveOrVideo(false)
    }


    private fun switchLiveOrVideo(
        reset: Boolean
    ) {

        var visibility =
            when {

                reset ->
                    View.GONE

                isLocked ->
                    View.INVISIBLE

                player?.isCurrentWindowLive == true ->
                    View.GONE

                else ->
                    View.VISIBLE
            }

        bindingControl.layoutSeekbar.visibility =
            visibility

        bindingControl.spacerControl.visibility =
            visibility

        if (
            player?.isCurrentWindowSeekable == false
        ) {
            visibility = View.GONE
        }

        bindingControl.buttonRewind.visibility =
            visibility

        bindingControl.buttonForward.visibility =
            visibility
    }


    /*
     * ============================================================
     * DRM DEVICE SUPPORT
     * ============================================================
     */

    private fun isDeviceSupportDrm(
        type: String
    ): Boolean {

        val uuid =
            try {
                type.toUUID()
            } catch (e: Exception) {
                C.UUID_NIL
            }

        if (
            uuid == C.UUID_NIL
        ) {

            return true
        }

        if (
            FrameworkMediaDrm
                .isCryptoSchemeSupported(uuid)
        ) {

            return true
        }

        val message =
            String.format(
                getString(
                    R.string.device_not_support_drm
                ),
                type.uppercase()
            )

        AlertDialog.Builder(this).apply {

            setTitle(
                R.string.player_playback_error
            )

            setMessage(message)

            setCancelable(false)

            setPositiveButton(
                getString(
                    R.string.btn_next_channel
                )
            ) { _, _ ->

                switchChannel(
                    CHANNEL_NEXT
                )
            }

            setNegativeButton(
                R.string.btn_close
            ) { _, _ ->

                finish()
            }

            create()

            show()
        }

        return false
    }


    /*
     * ============================================================
     * CREATE HTTP FACTORY
     * ============================================================
     */

    private fun createHttpDataSourceFactory():
            DefaultHttpDataSource.Factory {

        val userAgent =
            current?.userAgent
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: "NontonTV/${BuildConfig.VERSION_NAME} " +
                "(Android ${Build.VERSION.RELEASE})"

        val factory =
            DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent(userAgent)

        /*
         * Stream Referer.
         */
        current?.referer
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {

                factory.setDefaultRequestProperties(
                    mapOf(
                        "Referer" to it
                    )
                )
            }

        return factory
    }


    /*
     * ============================================================
     * DRM HEADER PARSER
     * ============================================================
     *
     * Format:
     *
     * https://license.example/license
     * |Authorization=Bearer TOKEN
     * |X-Device-ID=123
     *
     * Header tersebut hanya dikirim ke license server.
     * ============================================================
     */

    private fun applyLicenseHeaders(
        callback: HttpMediaDrmCallback,
        headers: Map<String, String>
    ) {

        headers.forEach { entry ->

            if (
                entry.key.isNotBlank() &&
                entry.value.isNotBlank()
            ) {

                callback.setKeyRequestProperty(
                    entry.key,
                    entry.value
                )
            }
        }
    }


    /*
     * ============================================================
     * PLAY CHANNEL
     * ============================================================
     */

    @Suppress("DEPRECATION")
    private fun playChannel() {

        /*
         * Reset controls.
         */
        switchLiveOrVideo(true)


        /*
         * Channel information.
         */
        bindingRoot.categoryName.text =
            category
                ?.name
                ?.trim()

        bindingRoot.channelName.text =
            current
                ?.name
                ?.trim()


        /*
         * ==========================================
         * STREAM URL
         * ==========================================
         */

        val streamUrl =
            current
                ?.streamUrl
                ?.decodeUrl()
                ?.trim()

        if (streamUrl.isNullOrBlank()) {

            showMessage(
                "URL stream kosong.",
                false
            )

            return
        }


        /*
         * ==========================================
         * HTTP
         * ==========================================
         */

        val httpDataSourceFactory =
            createHttpDataSourceFactory()

        val dataSourceFactory =
            DefaultDataSource.Factory(
                this,
                httpDataSourceFactory
            )


        /*
         * ==========================================
         * MEDIA ITEM
         * ==========================================
         */

        val mediaItemBuilder =
            MediaItem.Builder()
                .setUri(
                    Uri.parse(
                        streamUrl
                    )
                )


        /*
         * Kalau parser playlist memberikan MIME,
         * gunakan MIME tersebut.
         *
         * Untuk DASH:
         *
         * application/dash+xml
         *
         * Untuk HLS:
         *
         * application/x-mpegURL
         */

        current?.mimeType
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {

                mediaItemBuilder.setMimeType(
                    it
                )
            }


        val mediaItem =
            mediaItemBuilder.build()


        /*
         * ==========================================
         * MEDIA SOURCE FACTORY
         * ==========================================
         */

        val mediaSourceFactory =
            DefaultMediaSourceFactory(
                dataSourceFactory
            )


        /*
         * ==========================================
         * FIND DRM
         * ==========================================
         */

        val drmLicense =
            Playlist.cached.drmLicenses
                .firstOrNull {

                    current?.drmId
                        ?.equals(
                            it.id
                        ) == true
                }


        /*
         * ==========================================
         * DRM
         * ==========================================
         */

        if (
            drmLicense != null &&
            drmLicense.type
                .toUUID() != C.UUID_NIL
        ) {

            val uuid =
                drmLicense.type
                    .toUUID()


            /*
             * Check device support.
             */

            if (
                !FrameworkMediaDrm
                    .isCryptoSchemeSupported(
                        uuid
                    )
            ) {

                isDeviceSupportDrm(
                    drmLicense.type
                )

                return
            }


            val drmCallback:
                    MediaDrmCallback


            /*
             * ======================================
             * CLEARKEY
             * ======================================
             *
             * Contoh:
             *
             * kid:key
             *
             * atau:
             *
             * kid:key|kid2:key2
             *
             */

            if (
                uuid == C.CLEARKEY_UUID &&
                !drmLicense.key.isLinkUrl()
            ) {

                try {

                    drmCallback =
                        LocalMediaDrmCallback(
                            drmLicense
                                .key
                                .toClearKey()
                        )

                } catch (e: Exception) {

                    Log.e(
                        "PLAYER_DRM",
                        "Invalid ClearKey",
                        e
                    )

                    showMessage(
                        "ClearKey tidak valid: ${
                            e.message
                        }",
                        false
                    )

                    return
                }

            } else {


                /*
                 * ==================================
                 * LICENSE SERVER
                 * ==================================
                 *
                 * Widevine biasanya:
                 *
                 * https://license-server/...
                 */

                if (
                    !drmLicense
                        .key
                        .isLinkUrl()
                ) {

                    showMessage(
                        "License URL tidak valid untuk ${
                            drmLicense.type
                        }",
                        false
                    )

                    return
                }


                val callback =
                    HttpMediaDrmCallback(
                        drmLicense.key,
                        httpDataSourceFactory
                    )


                /*
                 * Tambahkan header license.
                 */

                applyLicenseHeaders(
                    callback,
                    drmLicense.headers
                )


                drmCallback =
                    callback
            }


            /*
             * ======================================
             * DRM SESSION MANAGER
             * ======================================
             */

            val drmSessionManager =
                DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(
                        uuid,
                        FrameworkMediaDrm.DEFAULT_PROVIDER
                    )
                    .setMultiSession(
                        uuid != C.CLEARKEY_UUID
                    )
                    .build(
                        drmCallback
                    )


            /*
             * ======================================
             * MEDIA SOURCE WITH DRM
             * ======================================
             */

            mediaSource =
                mediaSourceFactory
                    .setDrmSessionManagerProvider {

                        drmSessionManager
                    }
                    .createMediaSource(
                        mediaItem
                    )

        } else {


            /*
             * ======================================
             * NORMAL / NON DRM
             * ======================================
             */

            mediaSource =
                mediaSourceFactory
                    .createMediaSource(
                        mediaItem
                    )
        }


        /*
         * ==========================================
         * TRACK SELECTOR
         * ==========================================
         */

        trackSelector =
            DefaultTrackSelector(this)


        /*
         * ==========================================
         * LOAD CONTROL
         * ==========================================
         */

        val loadControl =
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    32_000,
                    64_000,
                    1_000,
                    1_000
                )
                .setPrioritizeTimeOverSizeThresholds(
                    true
                )
                .build()


        /*
         * ==========================================
         * RENDERERS
         * ==========================================
         *
         * Extension renderer OFF.
         *
         * Project lama mempunyai FFmpeg ExoPlayer
         * 2.x yang tidak boleh dicampur sembarangan
         * dengan Media3.
         */

        val renderersFactory =
            DefaultRenderersFactory(this)
                .setExtensionRendererMode(
                    DefaultRenderersFactory
                        .EXTENSION_RENDERER_MODE_OFF
                )


        /*
         * ==========================================
         * PLAYER BUILDER
         * ==========================================
         */

        val playerBuilder =
            ExoPlayer.Builder(
                this,
                renderersFactory
            )
                .setMediaSourceFactory(
                    mediaSourceFactory
                )
                .setTrackSelector(
                    trackSelector
                )


        if (
            preferences.optimizePrebuffer
        ) {

            playerBuilder.setLoadControl(
                loadControl
            )
        }


        /*
         * ==========================================
         * CREATE PLAYER
         * ==========================================
         */

        player =
            playerBuilder.build()


        player?.addListener(
            PlayerListener()
        )


        /*
         * ==========================================
         * PLAYER VIEW
         * ==========================================
         */

        bindingRoot.playerView.player =
            player

        bindingRoot.playerView.resizeMode =
            preferences.resizeMode

        bindingRoot.playerView.requestFocus()


        /*
         * ==========================================
         * START PLAYBACK
         * ==========================================
         */

        player?.setMediaSource(
            mediaSource
        )

        player?.playWhenReady =
            true

        player?.prepare()


        /*
         * ==========================================
         * SPEED
         * ==========================================
         */

        player?.playbackParameters =
            PlaybackParameters(
                preferences.speedMode
            )


        /*
         * ==========================================
         * VOLUME
         * ==========================================
         */

        player?.volume =
            preferences.volume
    }


    /*
     * ============================================================
     * CHANNEL SWITCH
     * ============================================================
     */

    private fun switchChannel(
        mode: Int
    ): Boolean {

        if (isLocked) {
            return true
        }

        switchChannel(
            mode,
            false
        )

        bindingRoot.playerView
            .hideController()

        return true
    }


    private fun switchChannel(
        mode: Int,
        lastCh: Boolean
    ) {

        val catId =
            Playlist.cached.categories
                .indexOf(category)

        val chId =
            category
                ?.channels
                ?.indexOf(current)
                ?: -1


        when (mode) {

            CATEGORY_UP -> {

                val previous =
                    catId - 1

                if (previous > -1) {

                    category =
                        Playlist.cached
                            .categories[previous]

                    current =
                        if (lastCh) {

                            category
                                ?.channels
                                ?.get(
                                    category
                                        ?.channels
                                        ?.size
                                        ?.minus(1)
                                        ?: 0
                                )

                        } else {

                            category
                                ?.channels
                                ?.get(0)
                        }

                } else {

                    Toast.makeText(
                        this,
                        R.string.top_category,
                        Toast.LENGTH_SHORT
                    ).show()

                    return
                }
            }


            CATEGORY_DOWN -> {

                val next =
                    catId + 1

                if (
                    next <
                    Playlist.cached.categories.size
                ) {

                    category =
                        Playlist.cached
                            .categories[next]

                    current =
                        category
                            ?.channels
                            ?.get(0)

                } else {

                    Toast.makeText(
                        this,
                        R.string.bottom_category,
                        Toast.LENGTH_SHORT
                    ).show()

                    return
                }
            }


            CHANNEL_PREVIOUS -> {

                val previous =
                    chId - 1

                if (previous > -1) {

                    current =
                        category
                            ?.channels
                            ?.get(previous)

                } else {

                    switchChannel(
                        CATEGORY_UP,
                        true
                    )

                    return
                }
            }


            CHANNEL_NEXT -> {

                val next =
                    chId + 1

                if (
                    next <
                    (category
                        ?.channels
                        ?.size
                        ?: 0)
                ) {

                    current =
                        category
                            ?.channels
                            ?.get(next)

                } else {

                    switchChannel(
                        CATEGORY_DOWN
                    )

                    return
                }
            }
        }


        /*
         * Reset player.
         */

        errorCounter = 0

        player?.playWhenReady =
            false

        player?.release()

        player = null

        playChannel()
    }


    /*
     * ============================================================
     * RETRY
     * ============================================================
     */

    private fun retryPlayback(
        force: Boolean
    ) {

        if (force) {

            if (!::mediaSource.isInitialized) {
                playChannel()
                return
            }

            player?.playWhenReady =
                true

            player?.setMediaSource(
                mediaSource
            )

            player?.prepare()

            return
        }


        AsyncSleep().task(
            object : AsyncSleep.Task {

                override fun onFinish() {

                    retryPlayback(true)
                }
            }
        ).start(1)
    }


    /*
     * ============================================================
     * PLAYER LISTENER
     * ============================================================
     */

    private inner class PlayerListener :
        Player.Listener {


        override fun onPlaybackStateChanged(
            state: Int
        ) {

            val trackHaveContent =
                try {

                    TrackSelectionDialog
                        .willHaveContent(
                            trackSelector
                        )

                } catch (
                    e: Exception
                ) {

                    false
                }


            bindingControl.trackSelection.visibility =
                if (trackHaveContent)
                    View.VISIBLE
                else
                    View.GONE


            when (state) {

                Player.STATE_READY -> {

                    errorCounter = 0


                    val catId =
                        Playlist.cached
                            .categories
                            .indexOf(category)


                    val chId =
                        category
                            ?.channels
                            ?.indexOf(current)
                            ?: -1


                    if (
                        catId >= 0 &&
                        chId >= 0
                    ) {

                        preferences.watched =
                            PlayData(
                                catId,
                                chId
                            )
                    }


                    switchLiveOrVideo()
                }


                Player.STATE_ENDED -> {

                    retryPlayback(true)
                }


                else -> {
                    // Nothing.
                }
            }
        }


        override fun onIsPlayingChanged(
            isPlaying: Boolean
        ) {

            super.onIsPlayingChanged(
                isPlaying
            )

            if (isPlaying) {

                setChannelInformation(
                    true
                )
            }
        }


        override fun onPlayerError(
            error: PlaybackException
        ) {

            if (
                player?.playWhenReady == false
            ) {
                return
            }


            /*
             * Retry maximum 5x.
             */

            if (
                errorCounter < 5 &&
                network.isConnected()
            ) {

                errorCounter++

                Toast.makeText(
                    applicationContext,
                    error.errorCodeName,
                    Toast.LENGTH_SHORT
                ).show()

                retryPlayback(false)

            } else {

                showMessage(

                    String.format(
                        getString(
                            R.string.player_error_message
                        ),

                        error.errorCode,

                        error.errorCodeName,

                        error.message
                    ),

                    true
                )
            }
        }


        /*
         * ========================================================
         * MEDIA3 TRACK CHANGE
         * ========================================================
         *
         * API lama:
         *
         * onTracksChanged(
         *     TrackGroupArray,
         *     TrackSelectionArray
         * )
         *
         * sudah tidak dipakai.
         *
         * Media3:
         *
         * onTracksChanged(Tracks)
         * ========================================================
         */

        override fun onTracksChanged(
            tracks: Tracks
        ) {

            if (
                tracks == lastSeenTracks
            ) {
                return
            }

            lastSeenTracks =
                tracks


            val mappedTrackInfo =
                trackSelector
                    .currentMappedTrackInfo
                    ?: return


            val videoSupport =
                mappedTrackInfo
                    .getTypeSupport(
                        C.TRACK_TYPE_VIDEO
                    )


            val audioSupport =
                mappedTrackInfo
                    .getTypeSupport(
                        C.TRACK_TYPE_AUDIO
                    )


            val isVideoProblem =
                videoSupport ==
                        MappingTrackSelector
                            .MappedTrackInfo
                            .RENDERER_SUPPORT_UNSUPPORTED_TRACKS


            val isAudioProblem =
                audioSupport ==
                        MappingTrackSelector
                            .MappedTrackInfo
                            .RENDERER_SUPPORT_UNSUPPORTED_TRACKS


            if (
                !isVideoProblem &&
                !isAudioProblem
            ) {
                return
            }


            val problem =
                when {

                    isVideoProblem &&
                            isAudioProblem ->
                        "video & audio"

                    isVideoProblem ->
                        "video"

                    else ->
                        "audio"
                }


            val message =
                String.format(
                    getString(
                        R.string.error_unsupported
                    ),
                    problem
                )


            if (isVideoProblem) {

                showMessage(
                    message,
                    false
                )

            } else if (isAudioProblem) {

                Toast.makeText(
                    applicationContext,
                    message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    /*
     * ============================================================
     * ERROR DIALOG
     * ============================================================
     */

    private fun showMessage(
        message: String,
        autoretry: Boolean
    ) {

        val countdown =
            AsyncSleep()

        val waitInSecond =
            30


        val btnRetryText =
            if (autoretry)

                String.format(
                    getString(
                        R.string.btn_retry_count
                    ),
                    waitInSecond
                )

            else

                getString(
                    R.string.btn_retry
                )


        val builder =
            AlertDialog.Builder(this).apply {

                setTitle(
                    R.string.player_playback_error
                )

                setMessage(
                    message
                )

                setCancelable(false)


                setNegativeButton(
                    getString(
                        R.string.btn_next_channel
                    )
                ) { dialog, _ ->

                    switchChannel(
                        CHANNEL_NEXT
                    )

                    dialog.dismiss()
                }


                setPositiveButton(
                    btnRetryText
                ) { dialog, _ ->

                    retryPlayback(true)

                    dialog.dismiss()
                }


                setNeutralButton(
                    R.string.btn_close
                ) { dialog, _ ->

                    dialog.dismiss()

                    finish()
                }


                setOnDismissListener {

                    countdown.stop()
                }


                create()
            }


        val dialog =
            builder.show()


        if (!autoretry) {
            return
        }


        countdown.task(
            object : AsyncSleep.Task {

                override fun onCountDown(
                    count: Int
                ) {

                    val text =
                        if (count <= 0)

                            getString(
                                R.string.btn_retry
                            )

                        else

                            String.format(
                                getString(
                                    R.string.btn_retry_count
                                ),
                                count
                            )


                    dialog.getButton(
                        AlertDialog.BUTTON_POSITIVE
                    ).text = text
                }


                override fun onFinish() {

                    dialog.dismiss()

                    retryPlayback(true)
                }
            }
        ).start(
            waitInSecond
        )
    }


    /*
     * ============================================================
     * TRACK SELECTOR
     * ============================================================
     */

    private fun showTrackSelector(): Boolean {

        TrackSelectionDialog
            .createForTrackSelector(
                trackSelector,
                player,
        DialogInterface.OnDismissListener {
                   retryPlayback(true)
                }
            )
            .show(
                supportFragmentManager,
                null
            )

        return true
    }


    /*
     * ============================================================
     * SCREEN MENU
     * ============================================================
     */

    private fun showMenu(
        view: View
    ) {

        PopupMenu(
            this,
            view
        ).apply {

            inflate(
                R.menu.setting_mode
            )

            setOnMenuItemClickListener {
                m: MenuItem ->

                when (m.itemId) {

                    R.id.speed_mode ->
                        showSpeedMenu(view)

                    else ->
                        showScreenMenu(view)
                }

                true
            }

            show()
        }
    }


    private fun showScreenMenu(
        view: View
    ) {

        val timeout =
            bindingRoot.playerView
                .controllerShowTimeoutMs

        bindingRoot.playerView
            .controllerShowTimeoutMs = 0


        val popupMenu =
            PopupMenu(
                this,
                view
            ).apply {

                inflate(
                    R.menu.screen_resize_mode
                )


                setOnMenuItemClickListener {
                    m: MenuItem ->

                    val mode =
                        when (m.itemId) {

                            R.id.mode_fit ->
                                0

                            R.id.mode_fixed_width ->
                                1

                            R.id.mode_fixed_height ->
                                2

                            R.id.mode_fill ->
                                3

                            R.id.mode_zoom ->
                                4

                            else ->
                                5
                        }


                    if (
                        bindingRoot.playerView
                            .resizeMode != mode &&
                        mode != 5
                    ) {

                        bindingRoot.playerView
                            .resizeMode = mode

                        preferences.resizeMode =
                            mode
                    }


                    if (
                        m.itemId ==
                        R.id.mode_back
                    ) {

                        showMenu(view)

                    } else {

                        showScreenMenu(view)
                    }

                    true
                }


                when (
                    preferences.resizeMode
                ) {

                    0 ->
                        menu.findItem(
                            R.id.mode_fit
                        ).isChecked = true

                    1 ->
                        menu.findItem(
                            R.id.mode_fixed_width
                        ).isChecked = true

                    2 ->
                        menu.findItem(
                            R.id.mode_fixed_height
                        ).isChecked = true

                    3 ->
                        menu.findItem(
                            R.id.mode_fill
                        ).isChecked = true

                    4 ->
                        menu.findItem(
                            R.id.mode_zoom
                        ).isChecked = true
                }


                setOnDismissListener {

                    bindingRoot.playerView
                        .controllerShowTimeoutMs =
                        timeout
                }
            }


        try {

            val popup =
                PopupMenu::class.java
                    .getDeclaredField(
                        "mPopup"
                    )

            popup.isAccessible = true

            val menu =
                popup.get(
                    popupMenu
                )

            menu.javaClass
                .getDeclaredMethod(
                    "setForceShowIcon",
                    Boolean::class.java
                )
                .invoke(
                    menu,
                    true
                )

        } catch (e: Exception) {

            e.printStackTrace()

        } finally {

            popupMenu.show()
        }
    }


    /*
     * ============================================================
     * SPEED MENU
     * ============================================================
     */

    private fun showSpeedMenu(
        view: View
    ) {

        val timeout =
            bindingRoot.playerView
                .controllerShowTimeoutMs

        bindingRoot.playerView
            .controllerShowTimeoutMs = 0


        val popupMenu =
            PopupMenu(
                this,
                view
            ).apply {

                inflate(
                    R.menu.playback_speed_mode
                )


                setOnMenuItemClickListener {
                    m: MenuItem ->

                    val speed =
                        when (m.itemId) {

                            R.id.speed_0_25 ->
                                0.25F

                            R.id.speed_0_50 ->
                                0.5F

                            R.id.speed_0_75 ->
                                0.75F

                            R.id.speed_1_00 ->
                                1F

                            R.id.speed_1_25 ->
                                1.25F

                            R.id.speed_1_50 ->
                                1.5F

                            R.id.speed_1_75 ->
                                1.75F

                            R.id.speed_2_00 ->
                                2F

                            else ->
                                0F
                        }


                    if (
                        preferences.speedMode !=
                        speed &&
                        speed != 0F
                    ) {

                        player?.playbackParameters =
                            PlaybackParameters(
                                speed
                            )

                        preferences.speedMode =
                            speed

                        showSpeedMenu(view)
                    }


                    if (
                        m.itemId ==
                        R.id.speed_back
                    ) {

                        showMenu(view)
                    }

                    true
                }


                when (
                    preferences.speedMode
                ) {

                    0.25F ->
                        menu.findItem(
                            R.id.speed_0_25
                        ).isChecked = true

                    0.5F ->
                        menu.findItem(
                            R.id.speed_0_50
                        ).isChecked = true

                    0.75F ->
                        menu.findItem(
                            R.id.speed_0_75
                        ).isChecked = true

                    1F ->
                        menu.findItem(
                            R.id.speed_1_00
                        ).isChecked = true

                    1.25F ->
                        menu.findItem(
                            R.id.speed_1_25
                        ).isChecked = true

                    1.5F ->
                        menu.findItem(
                            R.id.speed_1_50
                        ).isChecked = true

                    1.75F ->
                        menu.findItem(
                            R.id.speed_1_75
                        ).isChecked = true

                    2F ->
                        menu.findItem(
                            R.id.speed_2_00
                        ).isChecked = true
                }


                setOnDismissListener {

                    bindingRoot.playerView
                        .controllerShowTimeoutMs =
                        timeout
                }
            }


        try {

            val popup =
                PopupMenu::class.java
                    .getDeclaredField(
                        "mPopup"
                    )

            popup.isAccessible = true

            val menu =
                popup.get(
                    popupMenu
                )

            menu.javaClass
                .getDeclaredMethod(
                    "setForceShowIcon",
                    Boolean::class.java
                )
                .invoke(
                    menu,
                    true
                )

        } catch (e: Exception) {

            e.printStackTrace()

        } finally {

            popupMenu.show()
        }
    }


    /*
     * ============================================================
     * VOLUME
     * ============================================================
     */

    @SuppressLint("ClickableViewAccessibility")
    private fun showVolumeMenu() {

        bindingControl.volumeLayout.visibility =
            View.VISIBLE


        bindingControl.volumeSeek.apply {

            progress =
                (
                    preferences.volume *
                            100
                    ).toInt()


            setOnSeekBarChangeListener(
                object :
                    SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                        seekBar: SeekBar,
                        progress: Int,
                        fromUser: Boolean
                    ) {

                        preferences.volume =
                            progress.toFloat() /
                                    100

                        player?.volume =
                            preferences.volume

                        isMute(
                            bindingControl.buttonVolume
                        )
                    }


                    override fun onStartTrackingTouch(
                        seekBar: SeekBar
                    ) {
                    }


                    override fun onStopTrackingTouch(
                        seekBar: SeekBar
                    ) {
                    }
                }
            )
        }
    }


    private fun isMute(
        view: ImageButton
    ) {

        when (preferences.volume) {

            0F ->

                view.setImageResource(
                    R.drawable.ic_volume_off
                )

            else ->

                view.setImageResource(
                    R.drawable.ic_volume_on
                )
        }
    }


    /*
     * ============================================================
     * LIFECYCLE
     * ============================================================
     */

    override fun onResume() {

        super.onResume()

        player?.playWhenReady =
            true
    }


    override fun onPause() {

        player?.playWhenReady =
            false

        super.onPause()
    }


    @Suppress("DEPRECATION")
    override fun onUserLeaveHint() {

        super.onUserLeaveHint()

        if (
            player?.isPlaying == false
        ) {
            return
        }


        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.N
        ) {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                val params =
                    PictureInPictureParams
                        .Builder()
                        .build()

                enterPictureInPictureMode(
                    params
                )

            } else {

                enterPictureInPictureMode()
            }
        }
    }


    override fun onPictureInPictureModeChanged(
        pip: Boolean,
        config: Configuration
    ) {

        super.onPictureInPictureModeChanged(
            pip,
            config
        )

        isPipMode = pip

        setChannelInformation(
            !pip
        )

        bindingRoot.playerView
            .useController = !pip

        player?.playWhenReady =
            true
    }


    override fun onWindowFocusChanged(
        hasFocus: Boolean
    ) {

        super.onWindowFocusChanged(
            hasFocus
        )

        if (hasFocus) {

            window.setFullScreenFlags()
        }
    }


    /*
     * ============================================================
     * KEY EVENTS
     * ============================================================
     */

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent
    ): Boolean {

        if (
            !isControllerVisible &&
            keyCode ==
            KeyEvent.KEYCODE_DPAD_CENTER
        ) {

            bindingRoot.playerView
                .showController()

            return true
        }


        if (isLocked) {
            return true
        }


        when (keyCode) {

            KeyEvent.KEYCODE_MENU ->

                return showTrackSelector()


            KeyEvent.KEYCODE_PAGE_UP ->

                return switchChannel(
                    CATEGORY_UP
                )


            KeyEvent.KEYCODE_PAGE_DOWN ->

                return switchChannel(
                    CATEGORY_DOWN
                )


            KeyEvent.KEYCODE_MEDIA_PREVIOUS ->

                return switchChannel(
                    CHANNEL_PREVIOUS
                )


            KeyEvent.KEYCODE_MEDIA_NEXT ->

                return switchChannel(
                    CHANNEL_NEXT
                )


            KeyEvent.KEYCODE_MEDIA_PLAY -> {

                player?.play()

                return true
            }


            KeyEvent.KEYCODE_MEDIA_PAUSE -> {

                player?.pause()

                return true
            }


            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {

                if (
                    player?.isPlaying == false
                ) {

                    player?.play()

                } else {

                    player?.pause()
                }

                return true
            }
        }


        if (
            player?.isCurrentWindowLive == false
        ) {

            when (keyCode) {

                KeyEvent.KEYCODE_MEDIA_REWIND -> {

                    player?.seekBack()

                    return true
                }


                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {

                    player?.seekForward()

                    return true
                }
            }
        }


        if (
            isControllerVisible
        ) {

            return super.onKeyUp(
                keyCode,
                event
            )
        }


        if (
            !preferences.reverseNavigation
        ) {

            when (keyCode) {

                KeyEvent.KEYCODE_DPAD_UP ->

                    return switchChannel(
                        CATEGORY_UP
                    )


                KeyEvent.KEYCODE_DPAD_DOWN ->

                    return switchChannel(
                        CATEGORY_DOWN
                    )


                KeyEvent.KEYCODE_DPAD_LEFT ->

                    return switchChannel(
                        CHANNEL_PREVIOUS
                    )


                KeyEvent.KEYCODE_DPAD_RIGHT ->

                    return switchChannel(
                        CHANNEL_NEXT
                    )
            }

        } else {

            when (keyCode) {

                KeyEvent.KEYCODE_DPAD_UP ->

                    return switchChannel(
                        CHANNEL_NEXT
                    )


                KeyEvent.KEYCODE_DPAD_DOWN ->

                    return switchChannel(
                        CHANNEL_PREVIOUS
                    )


                KeyEvent.KEYCODE_DPAD_LEFT ->

                    return switchChannel(
                        CATEGORY_UP
                    )


                KeyEvent.KEYCODE_DPAD_RIGHT ->

                    return switchChannel(
                        CATEGORY_DOWN
                    )
            }
        }


        return super.onKeyUp(
            keyCode,
            event
        )
    }


    /*
     * ============================================================
     * BACK
     * ============================================================
     */

    @Suppress("DEPRECATION")
    override fun onBackPressed() {

        if (isLocked) {
            return
        }


        if (
            isTelevision ||
            doubleBackToExitPressedOnce
        ) {

            super.onBackPressed()

            finish()

            return
        }


        doubleBackToExitPressedOnce =
            true


        Toast.makeText(
            this,
            getString(
                R.string.press_back_twice_exit_player
            ),
            Toast.LENGTH_SHORT
        ).show()


        Handler(
            Looper.getMainLooper()
        ).postDelayed({

            doubleBackToExitPressedOnce =
                false

        }, 2000)
    }


    /*
     * ============================================================
     * DESTROY
     * ============================================================
     */

    override fun onDestroy() {

        handlerInfo
            ?.removeCallbacksAndMessages(
                null
            )

        player?.release()

        player = null

        LocalBroadcastManager
            .getInstance(this)
            .unregisterReceiver(
                broadcastReceiver
            )

        super.onDestroy()
    }
}
