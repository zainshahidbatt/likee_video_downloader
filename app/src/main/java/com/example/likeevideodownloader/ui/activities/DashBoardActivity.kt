package com.example.likeevideodownloader.ui.activities

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ads.GoogleManager
import com.example.ads.databinding.NativeAdBannerLayoutBinding
import com.example.ads.newStrategy.types.GoogleInterstitialType
import com.example.ads.ui.binding.loadNativeAd
import com.example.analytics.dependencies.Analytics
import com.example.analytics.events.AnalyticsEvent
import com.example.analytics.qualifiers.GoogleAnalytics
import com.example.likeevideodownloader.R
import com.example.likeevideodownloader.databinding.ActivityHomeBinding
import com.example.likeevideodownloader.db.Database
import com.example.likeevideodownloader.di.ApiClient
import com.example.likeevideodownloader.di.DownloadAPIInterface
import com.example.likeevideodownloader.models.FVideo
import com.example.likeevideodownloader.models.FacebookReel
import com.example.likeevideodownloader.models.FacebookVideo
import com.example.likeevideodownloader.models.InstaVideo
import com.example.likeevideodownloader.models.LikeeVideo
import com.example.likeevideodownloader.models.ListAdapter
import com.example.likeevideodownloader.remote.RemoteConfig
import com.example.likeevideodownloader.util.Constants.FACEBOOK_URL
import com.example.likeevideodownloader.util.Constants.INSTA_URL
import com.example.likeevideodownloader.util.Constants.LIKEE_url
import com.example.likeevideodownloader.util.Constants.MOZ_URL
import com.example.likeevideodownloader.util.Constants.SNAPCHAT_URL
import com.example.likeevideodownloader.util.Constants.downloadVideos
import com.example.likeevideodownloader.util.Utils
import com.example.likeevideodownloader.util.Utils.createLikeeFolder
import com.example.likeevideodownloader.util.Utils.startDownload
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.remoteconfig.BuildConfig
import com.permissionx.guolindev.PermissionX
import dagger.hilt.android.AndroidEntryPoint
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.net.URL
import javax.inject.Inject
import kotlin.system.exitProcess

@Suppress("DEPRECATION", "IMPLICIT_BOXING_IN_IDENTITY_EQUALS")
@AndroidEntryPoint
class DashBoardActivity : AppCompatActivity() {

    lateinit var binding: ActivityHomeBinding

    private var nativeAd: NativeAd? = null

    @Inject
    lateinit var googleManager: GoogleManager

    @Inject
    @GoogleAnalytics
    lateinit var analytics: Analytics

    @Inject
    lateinit var remoteConfig: RemoteConfig

    private val TAG = "MainActivity"
    private val strName = "facebook"
    private val strNameSecond = "fb"
    private var actionBarDrawerToggle: ActionBarDrawerToggle? = null
    private var urlType = 0
    private var adapter: ListAdapter? = null
    var videos: ArrayList<FVideo>? = null
    var db: Database? = null
    private var downloadAPIInterface: DownloadAPIInterface? = null
    private var activity: Activity? = null
    private var onCreateIsCalled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)


        activity = this
        onCreateIsCalled = true
        //checkPermission()
        downloadAPIInterface = ApiClient.getInstance(
            resources
                .getString(R.string.download_api_base_url)
        )
            .create(DownloadAPIInterface::class.java)




        db = Database.init(this)
        db?.setCallback {
            Log.d(TAG, "onUpdateDatabase: MainActivity")
            updateListData()
        }
        registerReceiver(
            downloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )

        initViews()
        handleIntent()
        observe()
        showNativeAd()
        drawerClicks()

        this@DashBoardActivity.onBackPressedDispatcher.addCallback(this) {
            finishAffinity()
            exitProcess(0)
        }
    }

    private fun observe() {
        binding.apply {

            edLink.addTextChangedListener(object : TextWatcher {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    if (s.toString().trim { it <= ' ' }.isEmpty()) {
                        btnDownload.visibility = View.GONE
                    } else {
                        btnDownload.visibility = View.VISIBLE
                    }
                }

                override fun beforeTextChanged(
                    s: CharSequence, start: Int, count: Int,
                    after: Int
                ) {
                    Log.d("jejeText", "before")
                }

                override fun afterTextChanged(s: Editable) {
                    Log.d("jejeYes", "after")
                }
            })


            btnDownload.setOnClickListener {

                val ll = edLink.text.toString().trim { it <= ' ' }
                analytics.logEvent(
                    AnalyticsEvent.LINK(
                        status = ll
                    )
                )

              if (!Patterns.WEB_URL.matcher(ll).matches()) {
                    enterValidLink()
                    Utils.setToast(
                        this@DashBoardActivity,
                        resources.getString(R.string.enter_valid_url)
                    )
                } else {
                    //Recheck url type if it previously no checked
                    if (urlType == 0) {
                        urlType =
                            if (Utils.isInstaUrl(ll)) INSTA_URL else if (Utils.isSnapChatUrl(
                                    ll
                                )
                            ) {
                                SNAPCHAT_URL
                            } else if (Utils.isLikeeUrl(ll)) {
                                LIKEE_url
                            } else if (Utils.isMojUrl(ll)) {
                                MOZ_URL
                            } else FACEBOOK_URL
                    }
                    when (urlType) {
                        FACEBOOK_URL -> {
                           enterValidLink()
                        }
                        INSTA_URL -> {
                        enterValidLink()
                        }
                        SNAPCHAT_URL -> {
                          enterValidLink()
                        }
                        LIKEE_url -> getlikeeData()
                        MOZ_URL -> {
                            enterValidLink()
                        }
                    }
                }

                analytics.logEvent(
                    AnalyticsEvent.BTNDownload(
                        status = "Clicked"
                    )
                )


            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: is called")
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (actionBarDrawerToggle!!.onOptionsItemSelected(item)) {
            true
        } else super.onOptionsItemSelected(item)
    }

    private fun drawerClicks() {
        binding.apply {
            navView.setNavigationItemSelectedListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.nav_terms -> {
                        Toast.makeText(
                            this@DashBoardActivity,
                            "Here will be the Link",
                            Toast.LENGTH_SHORT
                        ).show()
                        true
                    }

                    R.id.nav_privacy -> {
                        Toast.makeText(
                            this@DashBoardActivity,
                            "Here will be the Link",
                            Toast.LENGTH_SHORT
                        ).show()
                        true
                    }

                    R.id.nav_about -> {
                        Toast.makeText(
                            this@DashBoardActivity,
                            "Here will be the Link",
                            Toast.LENGTH_SHORT
                        ).show()
                        true
                    }

                    R.id.nav_contact -> {
                        val emailIntent = Intent(
                            Intent.ACTION_SENDTO,
                            Uri.parse("mailto:zainshahidbuttt@gamail.com")
                        )
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "FB Reel Downloader")
                        emailIntent.putExtra(Intent.EXTRA_TEXT, "your message here")
                        startActivity(Intent.createChooser(emailIntent, "Chooser Title"))
                        true
                    }

                    R.id.nav_share -> {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND)
                            shareIntent.type = "text/plain"
                            shareIntent.putExtra(Intent.EXTRA_SUBJECT, R.string.app_name)
                            var shareMessage = "\nLet me recommend you this application\n\n"
                            shareMessage =
                                """
                            ${shareMessage + "https://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID}
                            """.trimIndent()
                            shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage)
                            startActivity(Intent.createChooser(shareIntent, "choose one"))
                        } catch (e: java.lang.Exception) {
                            //e.toString();
                        }
                        true
                    }

                    else -> {
                        false
                    }
                }
            }
        }
    }

    private fun initViews() {
        binding.apply {

            btnDrawer.setOnClickListener {
                myDrawerLayout.openDrawer(GravityCompat.START)
            }


            askReadPermission()
            askWritePermission()
            adapter = ListAdapter(
                this@DashBoardActivity
            ) { video ->
                when (video.state) {
                    FVideo.DOWNLOADING ->                         //video is in download state
                        Toast.makeText(
                            applicationContext,
                            "Video Downloading",
                            Toast.LENGTH_LONG
                        )
                            .show()

                    FVideo.PROCESSING ->                         //Video is processing
                        Toast.makeText(
                            applicationContext,
                            "Video Processing",
                            Toast.LENGTH_LONG
                        )
                            .show()

                    FVideo.COMPLETE -> {
                        //complete download and processing ready to use
                        val location: String = video.fileUri


                        //Downloaded video play into video player
                        val file = File(location)
                        if (file.exists()) {
                            val uri = Uri.parse(location)
                            val intent1 = Intent(Intent.ACTION_VIEW)
                            if (Utils.isVideoFile(
                                    applicationContext,
                                    video.fileUri
                                )
                            ) {
                                intent1.setDataAndType(
                                    uri,
                                    "video/*"
                                )
                            } else intent1.setDataAndType(uri, "image/*")
                            if (intent1.resolveActivity(packageManager) != null) startActivity(
                                intent1
                            ) else Toast.makeText(
                                applicationContext,
                                "No application can view this",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {

                            //File doesn't exists
                            Toast.makeText(
                                applicationContext,
                                "File doesn't exists",
                                Toast.LENGTH_LONG
                            ).show()
                            Log.d(TAG, "onItemClickListener: file " + file.path)

                            //Delete the video instance from the list
                            db?.deleteAVideo(video.downloadId)
                        }
                    }
                }
            }
            recyclerView.layoutManager = LinearLayoutManager(this@DashBoardActivity)
            updateListData()
            recyclerView.adapter = adapter
            ItemTouchHelper(object :
                ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return false
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val adapterPosition = viewHolder.adapterPosition
                    db?.deleteAVideo(videos!![adapterPosition].downloadId)
                }
            }).attachToRecyclerView(recyclerView)

        }
    }

    /**
     * this function update the listAdapter data form the database
     */
    private fun updateListData() {
        binding.apply {
            videos = db?.recentVideos
        }
    }



    private fun handleIntent() {
        val intent = intent

        if (intent == null || intent.action == null) {
            Log.d(TAG, "handleIntent: intent is null")
            return
        }
        if (intent.action == Intent.ACTION_SEND && intent.type != null) {
            if (intent.type == "text/plain") {
                // Extract the shared video URL from the intent's extras bundle
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                Log.d(TAG, "handleIntent: sharedText $sharedText")
                onCreateIsCalled = false
            }
        }
    }

    private fun getFacebookData() {
        binding.apply {
            try {
                Utils.createFacebookFolder()
                if (Utils.isFacebookReelsUrl(edLink.text.toString().trim { it <= ' ' })) {
                    getFacebookReelsData()
                    return
                }
                val url = URL(edLink.text.toString())
                val host = url.host

                if (host.contains(strName) || host.contains(strNameSecond)) {
                    Utils.showProgressDialog(this@DashBoardActivity)

                    val videoLink: String = edLink.text.toString().trim { it <= ' ' }
                    val video = downloadAPIInterface?.getFacebookVideos(videoLink)
                    video?.enqueue(object : Callback<FacebookVideo?> {
                        override fun onResponse(
                            call: Call<FacebookVideo?>,
                            response: Response<FacebookVideo?>
                        ) {
                            Utils.hideProgressDialog(activity)
                            if (response.isSuccessful) {
                                val facebookVideo = response.body()
                                if (facebookVideo == null) {
                                    showStartDownloadDialogR("", FACEBOOK_URL)
                                    return
                                }
                                if (!facebookVideo.error) {
                                    val dataArrayList = facebookVideo.data
                                    val length = dataArrayList.size
                                    val map: MutableMap<String, String> = HashMap()
                                    for (i in 0 until length) {
                                        val data = dataArrayList[i]
                                        if (data.format_id == "hd" || data.format_id == "sd"
                                        ) {
                                            map[data.format_id] = data.url
                                        }
                                    }
                                    if (map.containsKey("hd")) {
                                        showStartDownloadDialogR(map["hd"], FACEBOOK_URL)
                                    } else if (map.containsKey("sd")) {
                                        showStartDownloadDialogR(map["sd"], FACEBOOK_URL)
                                    } else Log.d("MainActivity.TAG", "onResponse: map is null")
                                } else {
                                    showStartDownloadDialogR("", FACEBOOK_URL)
                                }
                            }
                        }

                        override fun onFailure(call: Call<FacebookVideo?>, t: Throwable) {
                            Utils.hideProgressDialog(activity)
                            showStartDownloadDialogR("", FACEBOOK_URL)
                        }
                    })
                } else {
                    Utils.setToast(
                        this@DashBoardActivity,
                        resources.getString(R.string.enter_valid_url)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getFacebookReelsData() {
        binding.apply {
            Utils.showProgressDialog(this@DashBoardActivity)
            val videoLink: String = edLink.text.toString().trim { it <= ' ' }
            val video = downloadAPIInterface?.getFacebookReels(videoLink)
            video?.enqueue(object : Callback<FacebookReel?> {
                override fun onResponse(
                    call: Call<FacebookReel?>,
                    response: Response<FacebookReel?>
                ) {
                    Utils.hideProgressDialog(activity)
                    if (response.isSuccessful) {
                        val facebookVideo = response.body()
                        if (facebookVideo == null) {
                            showStartDownloadDialogR("", FACEBOOK_URL)
                            return
                        }
                        if (!facebookVideo.error) {
                            val dataArrayList = facebookVideo.data
                            val length = dataArrayList.size
                            val map: MutableMap<String, String> = HashMap()
                            for (i in 0 until length) {
                                val data = dataArrayList[i]
                                if (data.format_id == "hd" || data.format_id == "sd"
                                ) {
                                    map[data.format_id] = data.url
                                }
                            }
                            if (map.containsKey("hd")) {
                                showStartDownloadDialogR(map["hd"], FACEBOOK_URL)
                            } else if (map.containsKey("sd")) {
                                showStartDownloadDialogR(map["sd"], FACEBOOK_URL)
                            } else Log.d("MainActivity.TAG", "onResponse: map is null")
                        } else {
                            showStartDownloadDialogR("", FACEBOOK_URL)
                        }
                    }
                }

                override fun onFailure(call: Call<FacebookReel?>, t: Throwable) {
                    Utils.hideProgressDialog(activity)
                    showStartDownloadDialogR("", FACEBOOK_URL)
                }
            })
        }
    }


    private fun getInstaData() {
        binding.apply {
            Utils.createInstaFolder()
            Utils.showProgressDialog(this@DashBoardActivity)
            val videoLink: String = edLink.text.toString().trim { it <= ' ' }
            val video = downloadAPIInterface?.getInstaVideos(videoLink)
            video?.enqueue(object : Callback<InstaVideo?> {
                override fun onResponse(call: Call<InstaVideo?>, response: Response<InstaVideo?>) {
                    Utils.hideProgressDialog(activity)
                    if (response.isSuccessful) {
                        val instaVideo = response.body()
                        if (instaVideo == null) {
                            showStartDownloadDialogR("", FACEBOOK_URL)
                            return
                        }
                        if (!instaVideo.error) {
                            val dataArrayList = instaVideo.data
                            val length = dataArrayList.size
                            var linkIndex = -1
                            for (i in 0 until length) {
                                val format = dataArrayList[i].format
                                if (!format.startsWith("dash")) {
                                    try {
                                        val formatSArray =
                                            format.split(" ".toRegex())
                                                .dropLastWhile { it.isEmpty() }
                                                .toTypedArray()
                                        val array =
                                            formatSArray[formatSArray.size - 1].split("x".toRegex())
                                                .dropLastWhile { it.isEmpty() }
                                                .toTypedArray()
                                        val res = array[0].toInt()
                                        if (res > linkIndex) {
                                            linkIndex = i
                                        }
                                    } catch (e: Exception) {
                                        showStartDownloadDialogR("", INSTA_URL)
                                    }
                                }
                            }
                            if (linkIndex != -1) {
                                showStartDownloadDialogR(
                                    dataArrayList[linkIndex].url,
                                    INSTA_URL
                                )
                            } else {
                                showStartDownloadDialogR("", INSTA_URL)
                            }
                        } else {
                            showStartDownloadDialogR("", INSTA_URL)
                        }
                    }
                }

                override fun onFailure(call: Call<InstaVideo?>, t: Throwable) {
                    Utils.hideProgressDialog(activity)
                    showStartDownloadDialogR("", INSTA_URL)
                }
            })
        }
    }


    private fun getlikeeData() {
        binding.apply {
            createLikeeFolder()
            val urlString: String = edLink.getText().toString()
            Utils.showProgressDialog(this@DashBoardActivity)
            val videoLink: String = edLink.getText().toString().trim { it <= ' ' }
            val video = downloadAPIInterface!!.getLikeeVideos(videoLink)
            video.enqueue(object : Callback<LikeeVideo?> {
                override fun onResponse(call: Call<LikeeVideo?>, response: Response<LikeeVideo?>) {
                    Utils.hideProgressDialog(activity)
                    if (response.isSuccessful) {
                        val likeeVideo = response.body()
                        if (likeeVideo == null) {
                            Log.d("jeje_res_null", "onResponse: response is null")
                            showStartDownloadDialogR("", FACEBOOK_URL)
                            return
                        }
                        if (!likeeVideo.error) {
                            val data = likeeVideo.data
                            if (data.size == 1) {
                                showStartDownloadDialogR(data[0].url, LIKEE_url)
                            } else if (data.size > 1) {
                                for (i in data.indices) {
                                    if (data[i].format_id.equals("mp4-without-watermark")) showStartDownloadDialogR(
                                        data[i].url, LIKEE_url
                                    )
                                }
                            }
                        } else {
                            showStartDownloadDialogR("", SNAPCHAT_URL)
                        }
                    }
                }

                override fun onFailure(call: Call<LikeeVideo?>, t: Throwable) {
                    Utils.hideProgressDialog(activity)
                    Log.d("jeje_fail", "onFailure: is called")
                    showStartDownloadDialogR("", LIKEE_url)
                }
            })
        }
    }

    private fun enterValidLink() {
        val dialog = BottomSheetDialog(this, R.style.SheetDialog)
        dialog.setContentView(R.layout.dialog_invalid_link)
        val btnOk = dialog.findViewById<Button>(R.id.btn_clear)
        val cross = dialog.findViewById<ImageView>(R.id.ivCross)
        val adView = dialog.findViewById<FrameLayout>(R.id.nativeViewInvalid)
        if (remoteConfig.nativeAd) {
            nativeAd = googleManager.createNativeAdSmall()
            nativeAd?.let {
                val nativeAdLayoutBinding =
                    NativeAdBannerLayoutBinding.inflate(layoutInflater)
                nativeAdLayoutBinding.nativeAdView.loadNativeAd(ad = it)
                adView?.removeAllViews()
                adView?.addView(nativeAdLayoutBinding.root)
                adView?.visibility = View.VISIBLE
            }
        }

        dialog.behavior.isDraggable = false
        dialog.setCanceledOnTouchOutside(false)

        btnOk?.setOnClickListener {
            showInterstitialAd {
                dialog.dismiss()
                recreate();
            }
        }
        cross?.setOnClickListener {
            showInterstitialAd {
                dialog.dismiss()
                recreate();
            }
        }

        dialog.show()
    }

    private fun showStartDownloadDialogR(link: String?, urlType: Int) {
        try {
            Log.d("jejeDR", "showStartDownloadDialogR: link $link")
            //if link not found
            if (link == null || link == "") {
                Log.d("jejeDRS", "Empty $link")
                val dialog = BottomSheetDialog(this,R.style.SheetDialog)
                dialog.setContentView(R.layout.dialog_bottom_video_not_found_)
                val btnOk = dialog.findViewById<Button>(R.id.btn_clear)

                val adView = dialog.findViewById<FrameLayout>(R.id.nativeViewNot)
                if (remoteConfig.nativeAd) {
                    nativeAd = googleManager.createNativeAdSmall()
                    nativeAd?.let {
                        val nativeAdLayoutBinding =
                            NativeAdBannerLayoutBinding.inflate(layoutInflater)
                        nativeAdLayoutBinding.nativeAdView.loadNativeAd(ad = it)
                        adView?.removeAllViews()
                        adView?.addView(nativeAdLayoutBinding.root)
                        adView?.visibility = View.VISIBLE
                    }
                }

                dialog.behavior.isDraggable = false
                dialog.setCanceledOnTouchOutside(false)

                btnOk?.setOnClickListener {
                    showInterstitialAd {
                        recreate();
                        dialog.dismiss()
                    }
                }
                dialog.show()
                return
            }
            val dialog = BottomSheetDialog(this, R.style.SheetDialog)
            dialog.setContentView(R.layout.dialog_bottom_start_download)
            val videoQualityTv = dialog.findViewById<Button>(R.id.btn_clear)
            val adView = dialog.findViewById<FrameLayout>(R.id.nativeViewAdDownload)
            if (remoteConfig.nativeAd) {
                nativeAd = googleManager.createNativeAdSmall()
                nativeAd?.let {
                    val nativeAdLayoutBinding = NativeAdBannerLayoutBinding.inflate(layoutInflater)
                    nativeAdLayoutBinding.nativeAdView.loadNativeAd(ad = it)
                    adView?.removeAllViews()
                    adView?.addView(nativeAdLayoutBinding.root)
                    adView?.visibility = View.VISIBLE
                }
            }

            dialog.behavior.isDraggable = false
            dialog.setCanceledOnTouchOutside(false)
            dialog.setOnDismissListener { recreate(); }
            videoQualityTv?.setOnClickListener {
                showInterstitialAd {
                    videoDownloadR(link, urlType)
                    recreate();
                    dialog.dismiss()
                }
            }
            dialog.show()
        } catch (e: NullPointerException) {
            e.printStackTrace()
            Log.d(TAG, "onPostExecute: error!!!$e")
            Toast.makeText(applicationContext, "Video Not Found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askWritePermission() {
        val result =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        if (Build.VERSION.SDK_INT < 32 && result != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                1
            )
        }
    }

    private fun askReadPermission() {
        val result =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        if (Build.VERSION.SDK_INT < 32 && result != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                1
            )
        }
    }

    private fun videoDownloadR(videoUrl: String?, urlType: Int) {
        binding.apply {
            //Log.d(TAG, "onPostExecute: " + result);
            Log.d(TAG, "video url: $videoUrl")
            if (videoUrl == null || videoUrl == "") {
                Toast.makeText(activity, "This video quality is not available", Toast.LENGTH_SHORT)
                    .show()
                return
            }
            val fVideo: FVideo = startDownload(activity, videoUrl, urlType) ?: return
            downloadVideos[fVideo.downloadId] = fVideo
            edLink.setText("")
        }

    }


    private fun showNativeAd() {
        if (remoteConfig.nativeAd) {
            nativeAd = googleManager.createNativeAdSmall()
            nativeAd?.let {
                val nativeAdLayoutBinding = NativeAdBannerLayoutBinding.inflate(layoutInflater)
                nativeAdLayoutBinding.nativeAdView.loadNativeAd(ad = it)
                binding.nativeView.removeAllViews()
                binding.nativeView.addView(nativeAdLayoutBinding.root)
                binding.nativeView.visibility = View.VISIBLE
            }
        }
    }

    private fun showInterstitialAd(callback: () -> Unit) {

        val ad: InterstitialAd? =
            googleManager.createInterstitialAd(GoogleInterstitialType.MEDIUM)

        if (ad == null) {
            callback.invoke()
            return
        } else {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    super.onAdDismissedFullScreenContent()
                    callback.invoke()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    super.onAdFailedToShowFullScreenContent(error)
                    callback.invoke()
                }
            }
            ad.show(this)
        }

    }

    private val downloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadVideos.containsKey(id)) {
                Log.d("receiver", "onReceive: download complete")
                val fVideo: FVideo? = db?.getVideo(id)
                var videoPath: String? = null
                if (fVideo?.videoSource === FVideo.FACEBOOK) {
                    videoPath = Environment.getExternalStorageDirectory().toString() +
                            "/Download" + Utils.RootDirectoryFacebook + fVideo.fileName
                } else if (fVideo?.videoSource === FVideo.INSTAGRAM) {
                    videoPath = Environment.getExternalStorageDirectory().toString() +
                            "/Download" + Utils.RootDirectoryInsta + fVideo.fileName
                } else if (fVideo?.videoSource === FVideo.SNAPCHAT) {
                    videoPath = Environment.getExternalStorageDirectory().toString() +
                            "/Download" + Utils.RootDirectorySnapchat + fVideo.fileName
                } else if (fVideo?.videoSource === FVideo.LIKEE) {
                    videoPath = Environment.getExternalStorageDirectory().toString() +
                            "/Download" + Utils.RootDirectoryLikee + fVideo.fileName
                } else if (fVideo?.videoSource === FVideo.MOZ) {
                    videoPath = Environment.getExternalStorageDirectory().toString() +
                            "/Download" + Utils.RootDirectoryMoz + fVideo.fileName
                }

                val dialog = BottomSheetDialog(this@DashBoardActivity, R.style.SheetDialog)
                dialog.setContentView(R.layout.dialog_download_success)
                val btnOk = dialog.findViewById<Button>(R.id.btn_clear)
                val btnClose = dialog.findViewById<ImageView>(R.id.ivCross)
                val adView = dialog.findViewById<FrameLayout>(R.id.nativeViewAdSuccess)
                dialog.behavior.isDraggable = false
                dialog.setCanceledOnTouchOutside(false)
                if (showNatAd()) {
                    nativeAd = googleManager.createNativeAdSmall()
                    nativeAd?.let {
                        val nativeAdLayoutBinding =
                            NativeAdBannerLayoutBinding.inflate(layoutInflater)
                        nativeAdLayoutBinding.nativeAdView.loadNativeAd(ad = it)
                        adView?.removeAllViews()
                        adView?.addView(nativeAdLayoutBinding.root)
                        adView?.visibility = View.VISIBLE
                    }
                }

                btnOk?.setOnClickListener {
                    if (remoteConfig.showInterstitial) {
                        showInterstitialAd {
                            dialog.dismiss()
                        }
                    }
                }
                btnClose?.setOnClickListener {
                    if (remoteConfig.showInterstitial) {
                        showInterstitialAd {
                            dialog.dismiss()
                        }
                    }
                }

                dialog.show()

                db?.updateState(id, FVideo.COMPLETE)
                if (videoPath != null) db?.setUri(id, videoPath)
                downloadVideos.remove(id)
            }
        }
    }

    fun showNatAd(): Boolean {
        return remoteConfig.nativeAd
    }
}