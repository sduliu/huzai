package com.kudo.android

import android.app.Application
import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.kudo.android.data.AppContainer
import com.kudo.android.media.communityAnimations

class KudoApplication : Application(), SingletonImageLoader.Factory, coil.ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // WebSettings in AppContainer must initialize on the application's main thread.
        container = AppContainer(this)
    }

    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context).components {
        add(OkHttpNetworkFetcherFactory(callFactory = { container.imageHttpClient }))
        add(SvgDecoder.Factory())
        communityAnimations()
    }.build()

    // The hupuui subtree still runs on coil2 (coil.compose.AsyncImage), which uses its own
    // singleton. Without this factory its default loader has no animated-image decoder —
    // hupu comment stickers are mostly .GIF and failed to decode ("图片加载失败").
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .callFactory(container.imageHttpClient)
        .components {
            if (Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory())
            else add(coil.decode.GifDecoder.Factory())
        }
        .build()
}
