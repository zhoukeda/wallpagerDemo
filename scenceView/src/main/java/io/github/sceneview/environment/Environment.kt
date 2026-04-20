package io.github.sceneview.environment

import com.google.android.filament.IndirectLight
import com.google.android.filament.Skybox
import io.github.sceneview.loaders.EnvironmentLoader

/**
 *
 * Indirect light and skybox environment for a [Scene]
 *
 * Environments are usually captured as high-resolution HDR equirectangular images and processed by
 * the cmgen tool to generate the data needed by IndirectLight.
 *
 * You can also process an hdr at runtime but this is more consuming.
 *
 * - Currently IndirectLight is intended to be used for "distant probes", that is, to represent
 * global illumination from a distant (i.e. at infinity) environment, such as the sky or distant
 * mountains. Only a single IndirectLight can be used in a Scene. This limitation will be lifted in
 * the future.
 * - When added to a Scene, the Skybox fills all untouched pixels.
 *
 * @see [EnvironmentLoader]
 * @see [IndirectLight.Builder]
 * @see [Skybox.Builder]
 */
data class Environment(
    val indirectLight: IndirectLight? = null,
    val skybox: Skybox? = null
)