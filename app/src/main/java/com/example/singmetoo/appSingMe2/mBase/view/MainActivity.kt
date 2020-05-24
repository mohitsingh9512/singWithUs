package com.example.singmetoo.appSingMe2.mBase.view

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.example.singmetoo.R
import com.example.singmetoo.appSingMe2.mBase.interfaces.CommonBaseInterface
import com.example.singmetoo.appSingMe2.mBase.interfaces.NavigationDrawerInterface
import com.example.singmetoo.appSingMe2.mBase.util.BaseActivity
import com.example.singmetoo.appSingMe2.mBase.util.BaseFragment
import com.example.singmetoo.appSingMe2.mBase.util.DrawerManager
import com.example.singmetoo.appSingMe2.mUtils.songsRepository.SongsViewModel
import com.example.singmetoo.appSingMe2.mHome.view.HomeFragment
import com.example.singmetoo.appSingMe2.mUtils.helpers.*
import com.example.singmetoo.appSingMe2.mUtils.songsRepository.SongModel
import com.example.singmetoo.audioPlayerHelper.AudioPlayService
import com.example.singmetoo.audioPlayerHelper.PlayerStatus
import com.example.singmetoo.databinding.ActivityMainBinding
import com.example.singmetoo.databinding.BottomAudioPlayerBinding
import com.example.singmetoo.databinding.NavHeaderMainBinding
import com.example.singmetoo.permissionHelper.PermissionModel
import com.example.singmetoo.permissionHelper.PermissionsManager
import com.example.singmetoo.permissionHelper.PermissionsResultInterface
import com.google.android.material.bottomsheet.BottomSheetBehavior

const val TAG = "MAIN_ACTIVITY_TAG"

class MainActivity : BaseActivity(), CommonBaseInterface,NavigationDrawerInterface,PermissionsResultInterface {

    private lateinit var mLayoutBinding: ActivityMainBinding
    private lateinit var mBottomAudioPlayerBinding: BottomAudioPlayerBinding
    private var actionBarDrawerToggle:ActionBarDrawerToggle? = null
    private var drawerManager: DrawerManager? = null
    private var mBottomSheetAudioPlayerBehaviour: BottomSheetBehavior<View>? = null
    private var mPlayerStatusLiveData: LiveData<PlayerStatus>? = null
    private var mSongViewModel : SongsViewModel? = null
    private var mCurrentPlayingSong: SongModel? = null
    private var mAudioPlayService: AudioPlayService? = null
    private var mSongsListFromDevice: ArrayList<SongModel>? = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e(TAG,"onCreate")
        mLayoutBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        mBottomAudioPlayerBinding = mLayoutBinding.appBarMain.includeAudioPlayerLayout

        init()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        Log.e(TAG,"onPostCreate")
        actionBarDrawerToggle?.syncState()
    }

    override fun onStart() {
        Log.e(TAG,"onStart")
        super.onStart()
        bindToAudioService()
    }

    override fun onResume() {
        super.onResume()
        Log.e(TAG,"onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.e(TAG,"onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.e(TAG,"onStop")
        unbindAudioService()
    }

    private fun init() {
        initObj()
        initNavBar()
        initListeners()
        initSetUpAudioPlayerBottomSheet()
        initFetchSongsFromDevice()
        initObserver()
        initOpenHomeFragment()
    }

    private fun initListeners() {
        mBottomAudioPlayerBinding.audioPlayerPreviewPlayIv.setOnClickListener {
            startAudioService()
        }
    }

    private fun initObserver() {
        mSongViewModel?.getSongsLiveData()?.observe(this@MainActivity, Observer { list ->
            Log.e(TAG,"Observer")
            mSongsListFromDevice?.clear()
            mSongsListFromDevice?.addAll(list)
            mCurrentPlayingSong = AppUtil.getPlayingSongFromList(list)
            mCurrentPlayingSong?.let {
                showBottomAudioPlayer(it,!mBottomAudioPlayerBinding.exoPlayerView.player.isPlayingSong())
            }
        })
    }

    private fun initSetUpAudioPlayerBottomSheet() {
        mBottomSheetAudioPlayerBehaviour = BottomSheetBehavior.from(mBottomAudioPlayerBinding.audioPlayerMainCl)
        mBottomSheetAudioPlayerBehaviour?.peekHeight = this.fetchDimen(R.dimen.audio_player_preview_height)
        mBottomSheetAudioPlayerBehaviour?.state = BottomSheetBehavior.STATE_COLLAPSED
        mBottomSheetAudioPlayerBehaviour?.isHideable = false
        addBottomSheetCallbacks()
        hideBottomAudioPlayer()
    }

    private fun addBottomSheetCallbacks() {
        mBottomSheetAudioPlayerBehaviour?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when(newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {

                    }
                    BottomSheetBehavior.STATE_HIDDEN-> {

                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {

                    }
                    else -> {

                    }
                }
            }

        })
    }

    private fun initObj() {
        drawerManager = DrawerManager(this, mLayoutBinding.drawerLayout)
        mSongViewModel = ViewModelProviders.of(this).get(SongsViewModel::class.java)
    }

    private fun initNavBar() {
        actionBarDrawerToggle = ActionBarDrawerToggle(this, mLayoutBinding.drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_open)
        actionBarDrawerToggle?.let { mLayoutBinding.drawerLayout.addDrawerListener(it) }
        mLayoutBinding.navView.setNavigationItemSelectedListener(drawerManager)
        setNavHeaderView()

        //to show hamburger menu on each fragment
        supportFragmentManager.addOnBackStackChangedListener {
            if(supportFragmentManager.backStackEntryCount > 0) {
                actionBarDrawerToggle?.syncState()
            }
        }
    }

    private fun initFetchSongsFromDevice() {
        if(PermissionsManager.checkPermissions(this, arrayOf(AppConstants.PERMISSION_WRITE_STORAGE),permissionCallback = this)) {
            mSongViewModel?.fetchAllSongsFromDevice()
        }
    }

    private fun initOpenHomeFragment() {
        supportFragmentManager.addFragment(fragment = HomeFragment(),container = R.id.main_activity_container,fragmentTag = AppConstants.HOME_FRAGMENT_TAG)
    }

    private fun setNavHeaderView() {
        val headerLayoutBinding: NavHeaderMainBinding? = NavHeaderMainBinding.bind(mLayoutBinding.navView.getHeaderView(0))

        //set profile name using extension method (setProfileName)
        headerLayoutBinding?.navProfileName?.setProfileName(mUserInfo.userName)

        //setting binding variables
        headerLayoutBinding?.profilePhotoUrl = "${mUserInfo.userName?.get(0)}"
        headerLayoutBinding?.hasUserProfilePhoto = AppUtil.checkIsNotNull(mUserInfo.userProfilePicUrl)
        headerLayoutBinding?.navBarCallback = this
        headerLayoutBinding?.userInfo = mUserInfo
    }

    private fun updateAudioPlayerDetails(songToPlay: SongModel?, songPaused: Boolean) {
        songToPlay?.let {
            mBottomAudioPlayerBinding.audioPlayerPreviewTitleTv.text = it.songTitle ?: AppConstants.DEFAULT_TITLE
            mBottomAudioPlayerBinding.audioPlayerPreviewArtistTv.text = it.songArtist ?: AppConstants.DEFAULT_ARTIST
            mBottomAudioPlayerBinding.audioPlayerPreviewTitleTv.isSelected = true
            mBottomAudioPlayerBinding.audioPlayerPreviewArtistTv.isSelected = true
            if(songPaused) {
                mBottomAudioPlayerBinding.audioPlayerPreviewPlayIv.setImageResource(R.drawable.exo_icon_play)
                mBottomAudioPlayerBinding.audioPlayerPreviewPlayIv.tag = AppConstants.SONG_TAG_PLAY
            } else {
                mBottomAudioPlayerBinding.audioPlayerPreviewPlayIv.setImageResource(R.drawable.exo_icon_pause)
                mBottomAudioPlayerBinding.audioPlayerPreviewPlayIv.tag = AppConstants.SONG_TAG_PAUSE
            }
            mBottomAudioPlayerBinding.audioPlayerPreviewIv.setAlbumImage(AppUtil.getImageUriFromAlbum(it.songAlbumId))
        }
    }

    //audio service connection
    private val audioServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.e(TAG,"onServiceConnected")
            val binder = service as AudioPlayService.PlayMusicServiceBinder
            mAudioPlayService = binder.playMusicService

            // Attach the ExoPlayer to the PlayerView.
            mBottomAudioPlayerBinding.exoPlayerView.player = binder.exoPlayer
            mPlayerStatusLiveData = mAudioPlayService?.playerStatusLiveData

            mPlayerStatusLiveData?.observe(this@MainActivity, Observer {
                handlePlayerStatusChangeFromService(it)
            })
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mAudioPlayService = null
        }
    }

    private fun handlePlayerStatusChangeFromService(playerStatus: PlayerStatus) {
        val songModel = AppUtil.getPlayingSongFromList(mSongsListFromDevice,songId = playerStatus.songId?.toLong())
        when(playerStatus) {
            is PlayerStatus.Playing -> {
                songModel?.let { updateAudioPlayerDetails(songModel,false) }
            }
            is PlayerStatus.Ended -> {
                songModel?.let { updateAudioPlayerDetails(it,true) }
            }
            is PlayerStatus.Cancelled -> {
                songModel?.let { updateAudioPlayerDetails(it,true) }
            }
            is PlayerStatus.Paused -> {
                songModel?.let { updateAudioPlayerDetails(it,true) }
            }
            is PlayerStatus.Error -> {
                songModel?.let { updateAudioPlayerDetails(it,true) }
            }
        }
    }

    private fun bindToAudioService() {
        if (mAudioPlayService == null) {
            AudioPlayService.newIntent(this).also { intent ->
                bindService(intent, audioServiceConnection, Context.BIND_AUTO_CREATE)
            }
        }
    }

    private fun unbindAudioService() {
        if (mAudioPlayService != null) {
            unbindService(audioServiceConnection)
            mAudioPlayService = null
        }
    }

    private fun startAudioService() {
        if(mBottomAudioPlayerBinding.audioPlayerPreviewPlayIv.tag == AppConstants.SONG_TAG_PLAY) {
            AudioPlayService.newIntent(this, mCurrentPlayingSong).also { intent ->
                startService(intent)
            }
        } else {
            mAudioPlayService?.pause()
        }
    }

    private fun stopAudioService() {
        mAudioPlayService?.pause()
        unbindAudioService()
        stopService(Intent(this, AudioPlayService::class.java))
        mAudioPlayService = null
    }




    //--------------------------------------------------------------------------------------------//


    //overridden methods
    override fun onBackPressed() {
        //if left nav_drawer is open
        if(mLayoutBinding.drawerLayout.isDrawerOpen(GravityCompat.START)){
            mLayoutBinding.drawerLayout.closeDrawers()
            return
        }

        when (val baseFragment: BaseFragment? = supportFragmentManager.findFragmentById(R.id.main_activity_container) as? BaseFragment) {
            is HomeFragment -> {
                (baseFragment as? HomeFragment)?.onBackPressed()
            }
            else -> {
                super.onBackPressed()
            }
        }
    }

    override val playerStatusLiveData: LiveData<PlayerStatus>?
        get() = mPlayerStatusLiveData

    override fun playAudio(songModel: SongModel?,toShowBottomAudioPlayer: Boolean) {
        songModel?.let {
            mCurrentPlayingSong = songModel
            updateAudioPlayerDetails(songModel,true)
            startAudioService()
        }
    }

    override fun pauseAudio() {
        mCurrentPlayingSong?.let {
            mAudioPlayService?.pause()
            updateAudioPlayerDetails(mCurrentPlayingSong,true)
        }
    }

    override fun stopAudio() {
    }

    override fun showBottomAudioPlayer(songToPlay:SongModel?,songPaused: Boolean) {
        songToPlay?.let {
            updateAudioPlayerDetails(it,songPaused)
            mBottomAudioPlayerBinding.audioPlayerMainCl.visibility = View.VISIBLE
            mBottomSheetAudioPlayerBehaviour?.state = BottomSheetBehavior.STATE_COLLAPSED
            mBottomAudioPlayerBinding.exoPlayerView.showController()
        }
    }

    override fun hideBottomAudioPlayer() {
        mBottomAudioPlayerBinding.audioPlayerMainCl.visibility = View.GONE
        mBottomSheetAudioPlayerBehaviour?.state = BottomSheetBehavior.STATE_COLLAPSED
        mBottomAudioPlayerBinding.exoPlayerView.hideController()
    }

    override fun closeDrawer() {
        mLayoutBinding.drawerLayout.closeDrawers()
    }

    override fun openDrawer() {
        mLayoutBinding.drawerLayout.openDrawer(GravityCompat.START)
    }

    override fun lockDrawer() {
        mLayoutBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
    }

    override fun unLockDrawer() {
        mLayoutBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
    }

    override fun isDrawerOpen() : Boolean{
        return mLayoutBinding.drawerLayout.isDrawerOpen(GravityCompat.START)
    }

    override fun onProfileClick(view:View) {
        mLayoutBinding.drawerLayout.closeDrawer(GravityCompat.START)
        AppUtil.showToast(this,"onProfileClick")
    }

    override fun onPermissionResult(isAllGranted: Boolean, permissionResults: ArrayList<PermissionModel>?, requestCode: Int) {
        if(isAllGranted){
            mSongViewModel?.fetchAllSongsFromDevice()
        }
    }
}
