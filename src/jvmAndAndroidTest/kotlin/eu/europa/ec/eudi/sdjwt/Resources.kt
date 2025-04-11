/*
 * Copyright (c) 2023 European Commission
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
package eu.europa.ec.eudi.sdjwt

import java.io.File
import java.io.InputStream

private object Resources

internal fun loadResource(name: String): String {
    val inputStream: InputStream? = try {
        // Try JVM-style resource loading first
        Resources.javaClass.getResourceAsStream(name)
    } catch (e: Exception) {
        null
    }

    // If JVM-style loading failed, try Android-style loading
    if (inputStream != null) {
        return inputStream.bufferedReader().readText()
    }

    // Try Android-style loading
    try {
        // For Android, try to load from assets
        val assetManager = Class.forName("android.content.res.AssetManager")
        val context = Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null)
            ?.javaClass
            ?.getMethod("getApplicationContext")
            ?.invoke(null)

        val assets = context?.javaClass
            ?.getMethod("getAssets")
            ?.invoke(context)

        val openMethod = assetManager.getMethod("open", String::class.java)
        val resourceName = name.removePrefix("/")
        val assetStream = openMethod.invoke(assets, resourceName) as? InputStream

        if (assetStream != null) {
            return assetStream.bufferedReader().readText()
        }
    } catch (e: Exception) {
        // Fall through to next approach
    }

    // If all else fails, try to load from the file system
    val jvmResourceFile = File("src/jvmAndAndroidTest/resources$name")
    if (jvmResourceFile.exists()) {
        return jvmResourceFile.readText()
    }

    // Try in commonTest resources
    val commonResourceFile = File("src/commonTest/resources$name")
    if (commonResourceFile.exists()) {
        return commonResourceFile.readText()
    }

    // If we get here, we couldn't load the resource
    error("Unable to load resource $name")
}
