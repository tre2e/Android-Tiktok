package com.example.tiktok


import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
import java.io.File
import androidx.core.net.toUri


@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private var simpleCache: SimpleCache? = null
    private lateinit var cacheDataSourceFactory: CacheDataSource.Factory
    private val PRELOAD_RANGE = 3


    private val videoUrls by lazy {
        // 把下面这行字符串里的链接全换成你自己的就行（支持任意数量，换行随意）
        """
        https://cdn.pixabay.com/video/2024/05/31/214669_large.mp4
        https://cdn.pixabay.com/video/2025/10/04/307864_large.mp4
        https://cdn.pixabay.com/video/2024/10/13/236256_large.mp4
        https://cdn.pixabay.com/video/2025/08/20/298643_tiny.mp4
        https://cdn.pixabay.com/video/2025/08/12/296958_large.mp4
        https://cdn.pixabay.com/video/2025/01/10/251873_large.mp4
        https://cdn.pixabay.com/video/2025/06/24/287510_large.mp4
        https://cdn.pixabay.com/video/2023/03/08/153821-806526710_large.mp4
        https://cdn.pixabay.com/video/2025/03/23/266987_large.mp4
        https://cdn.pixabay.com/video/2025/09/24/306155_large.mp4
        https://cdn.pixabay.com/video/2025/10/05/308073_large.mp4
        https://cdn.pixabay.com/video/2025/10/31/313145_large.mp4
        https://cdn.pixabay.com/video/2024/05/18/212404_large.mp4
        https://cdn.pixabay.com/video/2025/08/18/298103_large.mp4
        https://cdn.pixabay.com/video/2025/09/06/302098_large.mp4
        https://cdn.pixabay.com/video/2025/02/23/260397_large.mp4
        https://cdn.pixabay.com/video/2025/05/06/277097_large.mp4
        https://cdn.pixabay.com/video/2024/12/15/246856_large.mp4
        https://cdn.pixabay.com/video/2025/03/29/268528_large.mp4
        https://cdn.pixabay.com/video/2024/11/17/241802_tiny.mp4
        https://cdn.pixabay.com/video/2024/06/02/214940_large.mp4
        https://cdn.pixabay.com/video/2025/02/17/258799_large.mp4
    """.trimIndent()
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.startsWith("http") }
            .toList()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        val btnClearCache = findViewById<Button>(R.id.btnClearCache)
        val tvCacheSize = findViewById<TextView>(R.id.tvCacheSize)

        // 缓存处理逻辑
        fun updateCacheSize() {
            val size = simpleCache?.cacheSpace ?:0
            val sizeMb = size / (1024 * 1024)
            tvCacheSize.text = "缓存：${sizeMb} MB"
        }

        updateCacheSize() // 初始显示

        btnClearCache.setOnClickListener {
            lifecycleScope.launch {
                simpleCache?.let { cache ->
                    cache.release()
                    File(cacheDir, "MediaCache").deleteRecursively()

                    simpleCache = SimpleCache(
                        File(cacheDir, "MediaCache"),
                        LeastRecentlyUsedCacheEvictor(200 * 1024 * 1024L),
                        StandaloneDatabaseProvider(this@MainActivity)
                    )
                    cacheDataSourceFactory.setCache(simpleCache!!)

                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "缓存已清除", Toast.LENGTH_SHORT).show()
                        updateCacheSize()
                    }
                }
            }
        }

        simpleCache = SimpleCache(
            File(cacheDir, "MediaCache"),
            LeastRecentlyUsedCacheEvictor(200 * 1024 * 1024L),
            StandaloneDatabaseProvider(this)
        )

        cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache!!)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory()
                .setUserAgent("ExoPlayerDemo")
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(10000)
                .setAllowCrossProtocolRedirects(true))


        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()

        // 监听player切换
        player.addListener(object : Player.Listener{
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                onVideoChanged(index)
                updateCacheSize()
            }
        })

        // PlayerView播放器设置
        playerView.player = player
        playerView.useController = true
        playerView.keepScreenOn = true


        // 使用setMediaItems自动绑定播放列表，并开始播放
        val items = videoUrls.map { MediaItem.fromUri(it) }
        player.setMediaItems(items, 0, 0)
        player.prepare()
        player.playWhenReady = true

        onVideoChanged(0)
    }

    private fun preloadVideo(url: String) {
        val dataSpec = DataSpec(
            url.toUri(),
            0,
            1024 * 1024L,
            null
        )
        val cacheWriter = CacheWriter(
            cacheDataSourceFactory.createDataSource(),
            dataSpec,
            null,
            null
        )
        try {
            cacheWriter.cache()
        } catch (e:Exception) { }
    }

    // 动态预加载函数
    private fun onVideoChanged (currentIndex : Int) {
        // 直接用 lifecycleScope，自动随页面销毁取消，不需要手动管理 Job 列表
        lifecycleScope.launch {
            for (i in (currentIndex - PRELOAD_RANGE)..(currentIndex + PRELOAD_RANGE)) {
                if (i in videoUrls.indices && i != currentIndex) {
                    launch {
                        preloadVideo(videoUrls[i])
                    }
                }
            }
        }
    }


    override fun onStop() {
        player.playWhenReady = false
        super.onStop()
    }

    override fun onDestroy() {
        player.release()
        simpleCache?.release()
        super.onDestroy()
    }
}