package li.biq.sdk.sample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import li.biq.sdk.Biqli
import li.biq.sdk.BiqliAttributionCallback
import li.biq.sdk.BiqliAttributionResult
import li.biq.sdk.BiqliException

class MainActivity : Activity() {
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply { text = "Waiting for attribution…" }
        val retry = Button(this).apply {
            text = "Resolve attribution"
            setOnClickListener { resolve(null) }
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(48, 48, 48, 48)
                addView(output)
                addView(retry)
            },
        )

        Biqli.configure(
            context = applicationContext,
            appId = BuildConfig.BIQLI_APP_ID,
            publishableKey = BuildConfig.BIQLI_PUBLISHABLE_KEY,
            diagnosticsEnabled = true,
        )
        resolve(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolve(intent)
    }

    private fun resolve(intent: Intent?) {
        Biqli.resolveAttribution(
            intent = intent,
            callback = object : BiqliAttributionCallback {
                override fun onSuccess(result: BiqliAttributionResult) {
                    val referral = result.attribution?.referralCode ?: "none"
                    val route = result.link?.route ?: "none"
                    output.text = "Match: ${result.open.matchedBy.wireValue}\nReferral: $referral\nRoute: $route"
                }

                override fun onError(error: BiqliException) {
                    output.text = "Could not resolve: ${error.message}"
                }
            },
        )
    }
}
