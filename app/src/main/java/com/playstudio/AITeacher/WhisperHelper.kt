import android.content.Context
import android.util.Log
import com.playstudio.aiteacher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File

interface WhisperApiService {
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribeAudio(
        @Part file: MultipartBody.Part,
        @Part("model") model: okhttp3.RequestBody
    ): TranscriptionResponse
}

data class TranscriptionResponse(
    val text: String
)

class WhisperHelper(private val context: Context) {
    private val apiKey = BuildConfig.API_KEY // From your gradle.properties

    private val service: WhisperApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("Authorization", "Bearer $apiKey")
                            .build()
                        chain.proceed(request)
                    }
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WhisperApiService::class.java)
    }

    suspend fun transcribeAudio(audioFile: File): String = withContext(Dispatchers.IO) {
        try {
            val requestFile = audioFile.asRequestBody("audio/mpeg".toMediaTypeOrNull())
            val response = service.transcribeAudio(
                MultipartBody.Part.createFormData(
                    "file",
                    audioFile.name,
                    requestFile
                ),
                "whisper-1".toRequestBody("text/plain".toMediaTypeOrNull())
            )

            response.text.ifEmpty {
                throw Exception("Empty transcription response")
            }
        } catch (e: Exception) {
            Log.e("WhisperHelper", "Transcription failed", e)
            throw Exception("Transcription failed: ${e.message}")
        }
    }
}