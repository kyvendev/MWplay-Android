package com.stremio.mobile.player

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.stremio.mobile.player.failover.PlaybackStartDetector

data class PlaybackState(val activeUri:String?=null,val title:String?=null,val isPlaying:Boolean=false,val playerRevision:Long=0L,val castUri:String?=null,val castRequiresHeaders:Boolean=false,val activeSelectionId:Long?=null,val selectedSelectionId:Long?=null,
    /** The current stream really played (position moving forward); reset by every [PlaybackManager.load]. */
    val playbackStarted:Boolean=false,
    /** EXO -> VLC -> MPV all failed for the current stream; reset by every [PlaybackManager.load]. */
    val enginesExhausted:Boolean=false)

class PlaybackManager(private val context:Context,private val createPlayer:(Context,PlayerEngine,com.stremio.core.types.profile.Profile.Settings?)->Player=PlayerFactory::create){
    private val mutableState=MutableStateFlow(PlaybackState()); val state:StateFlow<PlaybackState> = mutableState
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var player:Player?=null;private var observer:Job?=null;private var revision=0L;private var selectionRevision=0L
    private data class LoadRequest(val uri:Uri,val title:String?,val start:Long,val subtitles:List<ExternalSubtitle>,val lang:String?,val settings:com.stremio.core.types.profile.Profile.Settings?,val castUri:String?,val castRequiresHeaders:Boolean,val selectionId:Long)
    private var request:LoadRequest?=null;private var attempted=mutableSetOf<PlayerEngine>()
    private val startDetector=PlaybackStartDetector()

    // Core changes selection while resolving a stream, before a local engine is ready.
    fun beginSelection():Long {val id=++selectionRevision;mutableState.value=mutableState.value.copy(selectedSelectionId=id);return id}
    fun load(uri:Uri,title:String?=null,startPositionMs:Long=0,subtitles:List<ExternalSubtitle> = emptyList(),preferredSubtitleLang:String?=null,engine:PlayerEngine=PlayerEngine.EXO,settings:com.stremio.core.types.profile.Profile.Settings?=null,castUri:String?=null,castRequiresHeaders:Boolean=false,selectionId:Long=beginSelection()){
        if(selectionId!=mutableState.value.selectedSelectionId)return
        observer?.cancel();player?.release();request=LoadRequest(uri,title,startPositionMs,subtitles,preferredSubtitleLang,settings,castUri,castRequiresHeaders,selectionId);attempted=mutableSetOf()
        startDetector.reset();mutableState.value=mutableState.value.copy(playbackStarted=false,enginesExhausted=false)
        startEngine(engine,startPositionMs)
    }
    private fun startEngine(engine:PlayerEngine,position:Long){
        val r=request?:return;attempted.add(engine)
        val p=runCatching{createPlayer(context,engine,r.settings).also{it.load(r.uri,position,r.subtitles,r.lang,r.settings);it.play()}}.getOrElse{fallbackFrom(engine,position,null);return}
        player=p;publish(r.uri,r.title,true);observe(p)
    }
    private fun observe(p:Player){observer?.cancel();observer=scope.launch{p.runtimeState.collect{runtime->
        if(player!==p)return@collect
        if(startDetector.onUpdate(runtime.isPlaying,runtime.isBuffering,runtime.positionMs,runtime.error!=null))mutableState.value=mutableState.value.copy(playbackStarted=true)
        val error=runtime.error?:return@collect
        if(!isFatal(error))return@collect
        fallbackFrom(p.engine,runtime.positionMs.takeIf{it>0}?:request?.start?:0,p)
    }}}
    private fun fallbackFrom(engine:PlayerEngine,position:Long,old:Player?){
        val next=when(engine){PlayerEngine.EXO->PlayerEngine.VLC;PlayerEngine.VLC->PlayerEngine.MPV;PlayerEngine.MPV->null}
        if(next==null||next in attempted){if(!mutableState.value.playbackStarted)mutableState.value=mutableState.value.copy(enginesExhausted=true);return}
        observer?.cancel();if(old!=null&&player===old){old.release();player=null}
        startEngine(next,position)
    }
    private fun isFatal(message:String):Boolean{val s=message.lowercase();return s.contains("não suport")||s.contains("unsupported")||s.contains("error")||s.contains("erro")||s.contains("falhou")||s.contains("failed")||s.contains("vlc não conseguiu")}
    private fun publish(uri:Uri,title:String?,playing:Boolean){revision++;mutableState.value=PlaybackState(uri.toString(),title,playing,revision,request?.castUri,request?.castRequiresHeaders?:false,request?.selectionId,mutableState.value.selectedSelectionId,mutableState.value.playbackStarted,mutableState.value.enginesExhausted)}
    fun attachView(view:android.view.View)=Unit;fun detachView()=Unit
    fun play(){player?.play();mutableState.value=mutableState.value.copy(isPlaying=true)};fun pause(){player?.pause();mutableState.value=mutableState.value.copy(isPlaying=false)}
    fun addExternalSubtitleTracks(tracks:List<ExternalSubtitle>){player?.addExternalSubtitleTracks(tracks)};fun addLocalSubtitle(track:ExternalSubtitle){player?.addLocalSubtitle(track)}
    fun release(){observer?.cancel();observer=null;player?.release();player=null;request=null;attempted.clear();startDetector.reset();revision++;mutableState.value=PlaybackState(playerRevision=revision,selectedSelectionId=mutableState.value.selectedSelectionId)}
    fun getPlayer():Player?=player
}

