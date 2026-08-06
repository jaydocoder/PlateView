package com.jaydocoder.plateview.feature.search

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.components.ViewModelComponent
import java.util.Locale
import javax.inject.Inject

interface VoiceRecognizer {
    fun start(
        onResult: (String) -> Unit,
        onFailure: (VoiceInputFailure) -> Unit,
    )

    fun release()
}

enum class VoiceInputFailure {
    PermissionDenied,
    ServiceUnavailable,
    NoMatch,
    RecognitionFailed,
}

class AndroidVoiceRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
) : VoiceRecognizer {
    private var speechRecognizer: SpeechRecognizer? = null

    override fun start(
        onResult: (String) -> Unit,
        onFailure: (VoiceInputFailure) -> Unit,
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onFailure(VoiceInputFailure.ServiceUnavailable)
            return
        }

        release()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) = Unit

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(rmsdB: Float) = Unit

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() = Unit

                    override fun onError(error: Int) {
                        onFailure(error.toVoiceInputFailure())
                        release()
                    }

                    override fun onResults(results: android.os.Bundle?) {
                        val recognizedText = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                        if (recognizedText.isNullOrEmpty()) {
                            onFailure(VoiceInputFailure.NoMatch)
                        } else {
                            onResult(recognizedText)
                        }
                        release()
                    }

                    override fun onPartialResults(partialResults: android.os.Bundle?) = Unit

                    override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
                },
            )
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toLanguageTag())
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false),
            )
        }
    }

    override fun release() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}

private fun Int.toVoiceInputFailure(): VoiceInputFailure = when (this) {
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    -> VoiceInputFailure.NoMatch

    else -> VoiceInputFailure.RecognitionFailed
}

@Module
@InstallIn(ViewModelComponent::class)
abstract class VoiceRecognizerModule {
    @Binds
    abstract fun bindVoiceRecognizer(recognizer: AndroidVoiceRecognizer): VoiceRecognizer
}
