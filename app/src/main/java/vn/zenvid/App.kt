package vn.zenvid

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.OkHttpClient
import vn.zenvid.data.AppDb
import vn.zenvid.data.HttpDownloader
import vn.zenvid.data.Library
import vn.zenvid.data.Prefs
import vn.zenvid.data.YouTube
import vn.zenvid.player.PlayerManager

/** Các thành phần dùng chung trong app (không dùng thư viện tiêm phụ thuộc cho gọn). */
object Services {
    lateinit var http: OkHttpClient
    lateinit var prefs: Prefs
    lateinit var db: AppDb
    lateinit var library: Library
    lateinit var updates: vn.zenvid.support.Updates
}

class App : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        vn.zenvid.support.CrashReporter.install(this, BuildConfig.VERSION_NAME)
        Services.http = HttpDownloader.newClient()
        Services.prefs = Prefs(this)
        Services.db = AppDb.create(this)
        Services.library = Library(Services.db, Services.prefs)
        Services.updates = vn.zenvid.support.Updates(Services.http, BuildConfig.UPDATE_REPO, BuildConfig.VERSION_NAME)
        YouTube.init(HttpDownloader(Services.http), Services.prefs.language.value, Services.prefs.country.value)
        vn.zenvid.download.Downloads.init(this)
        PlayerManager.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { Services.http })) }
            .crossfade(true)
            .build()
}
