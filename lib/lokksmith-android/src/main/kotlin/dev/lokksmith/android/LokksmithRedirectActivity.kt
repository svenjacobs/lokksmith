package dev.lokksmith.android

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Activity that receives the redirect URI from the auth flow and redirects the result to
 * [LokksmithAuthFlowActivity]. This construct is required to be able to clear the Custom Tabs
 * activity from the backstack after the redirect URI has been called.
 *
 * @see LokksmithAuthFlowActivity
 */
public class LokksmithRedirectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivity(
            LokksmithAuthFlowActivity.createRedirectIntent(this, intent.data)
        )

        finish()
    }
}
