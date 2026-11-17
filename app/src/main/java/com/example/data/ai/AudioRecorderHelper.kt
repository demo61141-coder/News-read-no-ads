package com.example.data.ai

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): File {
        stopAndRelease()
        val file = File(context.cacheDir, "cleanread_voice_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(96000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        recorder = mediaRecorder
        outputFile = file
        return file
    }

    fun stopAndReadBytes(): ByteArray? {
        val file = outputFile
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            if (file != null && file.exists() && file.length() > 0L) {
                file.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            stopAndRelease()
            throw e
        } finally {
            file?.delete()
            outputFile = null
        }
    }

    fun stopAndRelease() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        } finally {
            recorder = null
        }
        outputFile?.delete()
        outputFile = null
    }
}
