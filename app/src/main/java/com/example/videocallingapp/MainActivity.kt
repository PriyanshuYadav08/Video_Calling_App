package com.example.videocallingapp

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.videocallingapp.databinding.ActivityMainBinding
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val appID = "a85185504c954b868ed9fd410564d7c7"
    private val channelName = "sample_video_call_app"
    private val token = "007eJxTYEgNvZPJJ3HnmFitc5wy39ovlbWHzwss4iwvlN3aP4vp0wkFhkQLU0MLU1MDk2RLU5MkCzOL1BTLtBQTQwNTM5MU82Tzcw/XpjcEMjKEnj7MzMgAgSC+KENxYm5BTmp8WWZKan58cmJOTnxiQQEDAwBbriYw"
    private val uid = 0

    private var isJoined = false
    private var agoraEngine: RtcEngine? = null
    private var localSurfaceView: SurfaceView? = null
    private var remoteSurfaceView: SurfaceView? = null

    private val PERMISSION_ID = 22
    private val REQUESTED_PERMISSION = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.INTERNET  // Added Internet permission
    )

    private fun checkPermissions(): Boolean {
        return REQUESTED_PERMISSION.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun makeToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        Log.d("AgoraDebug", message)
    }

    private fun setUpVideoSdkEngine() {
        try {
            val config = RtcEngineConfig().apply {
                mContext = baseContext
                mAppId = appID
                mEventHandler = mRtcEngineEventHandler
            }
            agoraEngine = RtcEngine.create(config)
            // Configure the video encoding parameters
            agoraEngine?.apply {
                enableVideo()
                setVideoEncoderConfiguration(
                    VideoEncoderConfiguration(
                        VideoEncoderConfiguration.VD_640x360,
                        VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                        VideoEncoderConfiguration.STANDARD_BITRATE,
                        VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
                    )
                )
            }
        } catch (e: Exception) {
            makeToast("Error initializing Agora SDK: ${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!checkPermissions()) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSION, PERMISSION_ID)
        }

        setUpVideoSdkEngine()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.joinButton.setOnClickListener {
            joinCall()
        }
        binding.leaveButton.setOnClickListener {
            leaveCall()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        agoraEngine?.stopPreview()
        agoraEngine?.leaveChannel()
        Thread {
            RtcEngine.destroy()
            agoraEngine = null
        }.start()
    }

    private fun joinCall() {
        if (checkPermissions()) {
            val options = ChannelMediaOptions().apply {
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                publishCameraTrack = true  // Ensure camera track is published
                publishMicrophoneTrack = true  // Ensure audio track is published
            }

            setUpLocalVideo()
            localSurfaceView?.visibility = View.VISIBLE
            agoraEngine?.startPreview()
            agoraEngine?.joinChannel(token, channelName, uid, options)

            makeToast("Joining channel: $channelName")
        } else {
            makeToast("Permission not granted")
        }
    }

    private fun leaveCall() {
        if (!isJoined) {
            makeToast("Join a channel first")
        } else {
            agoraEngine?.leaveChannel()
            makeToast("You left the channel")
            if (remoteSurfaceView?.parent != null) {
                binding.remoteVideo.removeAllViews()
            }
            if (localSurfaceView?.parent != null) {
                binding.localUserVideo.removeAllViews()
            }
            remoteSurfaceView?.visibility = View.GONE
            localSurfaceView?.visibility = View.GONE
            remoteSurfaceView = null
            localSurfaceView = null
            isJoined = false
        }
    }

    private val mRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onUserJoined(uid: Int, elapsed: Int) {
            makeToast("Remote user joined: $uid")
            runOnUiThread { setUpRemoteVideo(uid) }
        }

        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            isJoined = true
            makeToast("Joined Channel: $channel")
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            makeToast("User Offline: $uid")
            runOnUiThread {
                binding.remoteVideo.removeAllViews()
                remoteSurfaceView = null
            }
        }

        // Add connection state callback
        override fun onConnectionStateChanged(state: Int, reason: Int) {
            makeToast("Connection state changed: $state, reason: $reason")
        }

        // Add error callback
        override fun onError(err: Int) {
            makeToast("Error occurred: $err")
        }
    }

    private fun setUpRemoteVideo(uid: Int) {
        runOnUiThread {
            // Remove any existing remote view
            binding.remoteVideo.removeAllViews()

            remoteSurfaceView = SurfaceView(baseContext).apply {
                setZOrderMediaOverlay(true)
            }
            binding.remoteVideo.addView(remoteSurfaceView)

            makeToast("Setting up remote video for UID: $uid")

            agoraEngine?.setupRemoteVideo(
                VideoCanvas(
                    remoteSurfaceView,
                    VideoCanvas.RENDER_MODE_FIT,  // Changed to FIT mode
                    uid
                )
            )

            remoteSurfaceView!!.visibility = View.VISIBLE
        }
    }

    private fun setUpLocalVideo() {
        runOnUiThread {
            // Remove any existing local view
            binding.localUserVideo.removeAllViews()

            localSurfaceView = SurfaceView(baseContext)
            binding.localUserVideo.addView(localSurfaceView)

            makeToast("Setting up local video")

            agoraEngine?.setupLocalVideo(
                VideoCanvas(
                    localSurfaceView,
                    VideoCanvas.RENDER_MODE_FIT,  // Changed to FIT mode
                    uid
                )
            )
        }
    }
}