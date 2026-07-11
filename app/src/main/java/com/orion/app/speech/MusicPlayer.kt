package com.orion.app.speech

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

class MusicPlayer(private val context: Context) {
    private var player: ExoPlayer? = null
    private var currentIndex = 0
    private var trackList = listOf<Track>()

    data class Track(val title: String, val uri: Uri)

    fun init() {
        if (player == null) {
            player = ExoPlayer.Builder(context).build()
        }
    }

    fun loadLocalTracks(folder: String = "") {
        trackList = scanTracks(folder)
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
        val track = trackList.getOrNull(idx) ?: return
        val item = MediaItem.Builder()
            .setUri(track.uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).build())
            .build()
        player?.setMediaItem(item)
        player?.prepare()
        player?.play()
    }

    fun stop() {
        player?.stop()
        player?.clearMediaItems()
    }

    fun isPlaying(): Boolean = player?.isPlaying == true

    fun release() {
        player?.release()
        player = null
    }
}
