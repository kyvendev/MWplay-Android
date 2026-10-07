package com.stremio.mobile.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/** Lightweight LibVLC backend used between ExoPlayer and MPV in the automatic fallback chain. */
class VlcStreamPlayer(context:Context, private val settings:com.stremio.core.types.profile.Profile.Settings?=null):Player{
    override val engine=PlayerEngine.VLC
    private val appContext=context.applicationContext
    private val state=MutableStateFlow(PlayerRuntimeState())
    override val runtimeState:StateFlow<PlayerRuntimeState> = state
    private val libVlc=LibVLC(appContext, arrayListOf("--network-caching=1500","--clock-jitter=0","--clock-synchro=0"))
    private val vlc=MediaPlayer(libVlc)
    private var surface:SurfaceView?=null
    private var currentUri:Uri?=null
    private var startMs=0L

    init { vlc.setEventListener { e -> when(e.type){
        MediaPlayer.Event.Playing -> publish(isPlaying=true,isBuffering=false,error=null)
        MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> publish(isPlaying=false,isBuffering=false)
        MediaPlayer.Event.Buffering -> publish(isBuffering=e.buffering<100f)
        MediaPlayer.Event.TimeChanged, MediaPlayer.Event.LengthChanged -> publish()
        MediaPlayer.Event.EndReached -> publish(isPlaying=false,isBuffering=false,ended=true)
        MediaPlayer.Event.EncounteredError -> publish(isPlaying=false,isBuffering=false,error="VLC não conseguiu reproduzir esta transmissão")
    } } }

    override fun createView(context:Context):View{
        val view=SurfaceView(context); surface=view
        vlc.vlcVout.setVideoView(view); vlc.vlcVout.attachViews()
        return FrameLayout(context).apply{addView(view,FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT))}
    }
    override fun load(uri:Uri,startPositionMs:Long,subtitles:List<ExternalSubtitle>,preferredSubtitleLang:String?,settings:com.stremio.core.types.profile.Profile.Settings?){
        currentUri=uri;startMs=startPositionMs
        val media=Media(libVlc,uri)
        try { vlc.media=media } finally { media.release() }
        if(startPositionMs>0) vlc.time=startPositionMs
        state.value=PlayerRuntimeState(isBuffering=true,positionMs=startPositionMs)
    }
    override fun retry(){currentUri?.let{load(it,startMs,emptyList(),null,settings);play()}}
    override fun play(){vlc.play()}
    override fun pause(){vlc.pause()}
    override fun seekTo(positionMs:Long){vlc.time=positionMs;publish()}
    override fun setPlaybackSpeed(speed:Float){vlc.rate=speed;publish(speed=speed)}
    override fun setResizeMode(mode:PlayerResizeMode)=Unit
    override fun selectAudioTrack(id:String)=Unit
    override fun selectSubtitleTrack(id:String)=Unit
    override fun disableSubtitles(){runCatching{vlc.spuTrack=-1}}
    override fun setSubtitleStyle(style:PlayerSubtitleStyle)=Unit
    override fun addExternalSubtitleTracks(tracks:List<ExternalSubtitle>)=Unit
    override fun addLocalSubtitle(track:ExternalSubtitle)=Unit
    override fun release(){runCatching{vlc.stop()};runCatching{vlc.vlcVout.detachViews()};vlc.release();libVlc.release();surface=null}
    private fun publish(isPlaying:Boolean=vlc.isPlaying,isBuffering:Boolean=state.value.isBuffering,error:String?=state.value.error,ended:Boolean=false,speed:Float=vlc.rate){
        state.value=state.value.copy(isPlaying=isPlaying,isBuffering=isBuffering,positionMs=vlc.time.coerceAtLeast(0),durationMs=vlc.length.coerceAtLeast(0),bufferedPositionMs=vlc.time.coerceAtLeast(0),speed=speed,error=error,ended=ended)
    }
}
