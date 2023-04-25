package com.kingtomoya.twicasting.bot

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread


class StartLiveObserveService : Service() {

    private val QUESTIONS: List<String> = listOf(
        "[質問] 特技はなんですか？⸜( ´ ꒳ ` )⸝",
        "[質問] 子供の頃の将来の夢は何ですか？( ˙꒳˙ᐢ )",
        "[質問] もし年齢を好きな時に止められるなら何歳ですか？( ᐢ˙꒳˙ᐢ )",
        "[質問] 自分の好きなところをあえて一つあげるなら何ですか？(*∩ω∩)",
        "[質問] 反抗期の思い出は何かありましたか？꒰๑•௰•๑꒱",
        "[質問] ずばり家族の好きなところは何ですか？(⑅•ᴗ•⑅)"
    )

    private val BASE_URL = "https://apiv2.twitcasting.tv/"

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private var questions: MutableList<String> = mutableListOf()

    private lateinit var usersUrl: String

    private var userId: String? = null
    private var userComment: String? = null

    private lateinit var timer: Timer

    private var isLiveFlag: Boolean = false

    private var lastMovieId: String? = null

    private var sliceCommentId: String? = null

    override fun onCreate() {
        super.onCreate()
        questions.addAll(QUESTIONS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            return START_NOT_STICKY
        }
        userId = intent.getStringExtra("userId")
        userComment = intent.getStringExtra("userComment")
        usersUrl = BASE_URL + "users/${userId}"

        timer = Timer()
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                request(usersUrl, "")
            }
        }, 0, 5000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotification()
        }
        return START_NOT_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onDestroy() {
        super.onDestroy()
        timer.cancel()
    }

    private fun request(url: String, param: String) {
        // video apiを叩く
        requestGetApi(url, param, object : Callback {
            override fun onResponse(call: Call, response: Response) {
                var responseBody: String? = null
                try {
                    responseBody = response.body?.string().toString()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (responseBody == null) {
                    return
                }
                // Jsonデータを取得
                val jsonData = JSONObject(responseBody)
                setLiveInfoFromJson(jsonData)
            }

            override fun onFailure(call: Call, e: IOException) {
            }
        })
    }

    /**
     * APIを叩く(GET)
     * @param url URL
     * @param param パラメータ
     * @param callback Callback
     */
    private fun requestGetApi(url: String, param: String?, callback: Callback) {
        val okHttpClient = OkHttpClient()
        // APIリクエストを取得する
        val requestBuilder = Request.Builder()
            .header("X-Api-Version", "2.0")
            .header(
                "Authorization",
                "Bearer {ACCESS_TOKEN}"
            )
            .header("Accept-Encoding", "gzip")

        val request = requestBuilder.url(url + param).get().build()
        // 通信はバックグラウンドスレッドで行う
        thread {
            // APIを叩く
            okHttpClient.newCall(request).enqueue(callback)
        }
    }

    private fun requestCommentApi(lastMovieId: String?, userComment: String?) {
        val url = BASE_URL + "movies/${lastMovieId}/comments"
        requestCommentPostApi(url, userComment, object : Callback {
            override fun onResponse(call: Call, response: Response) {
            }

            override fun onFailure(call: Call, e: IOException) {
            }
        })
    }

    /**
     * APIを叩く(post)
     * @param url URL
     * @param param パラメータ
     * @param callback Callback
     */
    private fun requestCommentPostApi(url: String, comment: String?, callback: Callback) {
        val okHttpClient = OkHttpClient()
        // APIリクエストを取得する
        val requestBuilder = Request.Builder()
            .header("X-Api-Version", "2.0")
            .header(
                "Authorization",
                "Bearer {ACCESS_TOKEN}"
            )
            .header("Accept-Encoding", "gzip")

        val sendDataJson = "{\"comment\":\"${comment}\",\"sns\":\"none\"}"
        val request = requestBuilder.url(url).post(sendDataJson.toRequestBody(JSON_MEDIA)).build()
        // 通信はバックグラウンドスレッドで行う
        thread {
            // APIを叩く
            okHttpClient.newCall(request).enqueue(callback)
        }
    }

    private fun setLiveInfoFromJson(responseJsonData: JSONObject) {
        try {
            val user = responseJsonData.getJSONObject("user")
            val lastMovieId = user.getString("last_movie_id")
            val isLive = user.getBoolean("is_live")
            if (!isLiveFlag && isLive) {
                // ライブ中に切り替わった時、コメントを送信する
                isLiveFlag = true
                if (this.lastMovieId == lastMovieId) {
                    // 同じ配信だったら何もしない
                    return
                }

                this.lastMovieId = lastMovieId

                requestCommentApi(lastMovieId, userComment)
            } else if (isLiveFlag && !isLive) {
                // ライブが終わった場合、フラグを戻す
                isLiveFlag = false
                questions.clear()
                questions.addAll(QUESTIONS)
            }

            if (isLive) {
                // ライブ中の場合、配信者さんのコメントを監視
                val url = BASE_URL + "movies/${lastMovieId}/comments"
                val param = if (sliceCommentId == null) "" else "?slice_id=${sliceCommentId}"
                requestGetApi(url, param, object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        var responseBody: String? = null
                        try {
                            responseBody = response.body?.string().toString()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        if (responseBody == null) {
                            return
                        }
                        // Jsonデータを取得
                        val jsonData = JSONObject(responseBody)
                        setLiveCommentFromJson(jsonData)
                    }

                    override fun onFailure(call: Call, e: IOException) {
                    }
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setLiveCommentFromJson(responseJsonData: JSONObject) {
        try {
            val itemsArray = responseJsonData.getJSONArray("comments")
            for (i in 0 until itemsArray.length()) {
                val commentInfo = itemsArray.getJSONObject(i)
                // ユーザーIDを取得する
                val fromUser = commentInfo.getJSONObject("from_user")
                val userId = fromUser.getString("screen_id")

                if (userId != this.userId) {
                    return
                }

                sliceCommentId = commentInfo.getString("id")

                val message = commentInfo.getString("message")

                if (message == "/q") {
                    // 質問メッセージの場合、質問コメントを送信する
                    val question = getRandomQuestion() ?: return
                    requestCommentApi(lastMovieId, question)
                }
            }
        } catch (e: Exception) {

        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotification() {
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Notification　Channel 設定
        val channel = NotificationChannel(
            "ServiceId", "BOT起動サービス！", NotificationManager.IMPORTANCE_HIGH
        )

        notificationManager.createNotificationChannel(channel)
        val notification = Notification.Builder(this, "ServiceId")
            .setContentTitle("BOT起動！")
            .setContentText("ライブ開始したらコメント投げるよー")
            .setSmallIcon(R.drawable.ic_media_play)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .build()

        // startForeground
        startForeground(1, notification)
    }

    private fun getRandomQuestion(): String? {
        if (questions.size == 0) {
            return "ごめんなさい…質問が切れちゃいました…許してください…｡°(´∩ω∩`)°｡"
        }
        val random = (0 until questions.size).random()

        val question = questions[random]
        questions.removeAt(random)

        return question
    }
}