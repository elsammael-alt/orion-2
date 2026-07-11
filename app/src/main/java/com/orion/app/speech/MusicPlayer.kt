package com.orion.app.speech

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer

class MusicPlayer(private val context: Context) {
    private var player: ExoPlayer? = null
    private var currentIndex = 0
    private var trackList = listOf<Track>()

    data class Track(val title: String, val uri: Uri)

    fun init() {
        try {
            if (player == null) {
                player = ExoPlayer.Builder(context).build()
            }
        } catch (e: Exception) {
            Log.e("MusicPlayer", "init failed", e)
        }
    }

    fun loadLocalTracks(folder: String = "") {
        try {
            trackList = scanTracks(folder)
        } catch (e: Exception) {
            Log.e("MusicPlayer", "loadLocalTracks failed", e)
        }
    }

    private fun scanTracks(folder: String): List<Track> {
        val tracks = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA
        )
        val selection = if (folder.isNotEmpty()) {
            "${MediaStore.Audio.Media.DATA} LIKE ?"
        } else null
        val selArgs = if (folder.isNotEmpty()) arrayOf("$folder%") else null

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selArgs,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )
        cursor?.use {
            val titleIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val idIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (it.moveToNext()) {
                val title = it.getString(titleIdx) ?: "Unknown"
                val id = it.getLong(idIdx)
                val uri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                tracks.add(Track(title, uri))
            }
        }
        return tracks
    }

    fun playRandom() {
        init()
        if (trackList.isEmpty()) loadLocalTracks()
        if (trackList.isEmpty()) return
        currentIndex = (trackList.indices).random()
        playAt(currentIndex)
    }

    fun playNext() {
        if (trackList.isEmpty()) return
        currentIndex = (currentIndex + 1) % trackList.size
        playAt(currentIndex)
    }

    fun playPrevious() {
        if (trackList.isEmpty()) return
        currentIndex = (currentIndex - 1 + trackList.size) % trackList.size
        playAt(currentIndex)
    }

    private fun playAt(idx: Int) {
        try {
            init()
            val track = trackList.getOrNull(idx) ?: return
            val item = MediaItem.Builder()
                .setUri(track.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).build())
                .build()
            player?.setMediaItem(item)
            player?.prepare()
            player?.play()
        } catch (e: Exception) {
            Log.e("MusicPlayer", "playAt failed", e)
        }
    }

    fun stop() {
        try {
            player?.stop()
            player?.clearMediaItems()
        } catch (e: Exception) {
            Log.e("MusicPlayer", "stop failed", e)
        }
    }

    fun isPlaying(): Boolean = try { player?.isPlaying == true } catch (e: Exception) { false }

    fun release() {
        try {
            player?.release()
            player = null
        } catch (e: Exception) {
            Log.e("MusicPlayer", "release failed", e)
        }
    }
}
