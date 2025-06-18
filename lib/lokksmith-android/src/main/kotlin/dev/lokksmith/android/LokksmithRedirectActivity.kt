/*
 * Copyright 2025 Sven Jacobs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
