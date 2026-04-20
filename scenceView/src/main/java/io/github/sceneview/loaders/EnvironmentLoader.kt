package io.github.sceneview.loaders

import android.content.Context
import androidx.annotation.RawRes
import com.google.android.filament.Engine
import com.google.android.filament.IndirectLight
import com.google.android.filament.Skybox
import com.google.android.filament.Texture
import com.google.android.filament.utils.HDRLoader
import com.google.android.filament.utils.KTX1Loader
import io.github.sceneview.environment.Environment
import io.github.sceneview.environment.IBLPrefilter
import io.github.sceneview.safeDestroyIndirectLight
import io.github.sceneview.safeDestroySkybox
import io.github.sceneview.safeDestroyTexture
import io.github.sceneview.texture.use
import io.github.sceneview.utils.loadFileBuffer
import io.github.sceneview.utils.readBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import java.io.File
import java.nio.Buffer

/**
 * Utility for decoding an HDR file or consuming KTX1 files and producing Filament textures, IBLs,
 * and sky boxes.
 *
 * KTX is a simple container format that makes it easy to bundle miplevels and cubemap faces into a
 * single file.
 *
 * Consuming the content of an HDR file and produces a [Texture] object to enerates a prefiltered
 * indirect light cubemap with specular filter is a GPU based implementation of the specular
 * probe pre-integration filter.
 * ** Launch the heaver computation. Expect 100-100ms on the GPU.**
 */
class EnvironmentLoader(
    val engine: Engine,
    internal val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    val iblPrefilter = IBLPrefilter(engine)

    private val environments = mutableListOf<Environment>()

    fun createEnvironment(
        indirectLight: IndirectLight? = null,
        skybox: Skybox? = null,
    ) = Environment(
        indirectLight = indirectLight,
        skybox = skybox,
    ).also {
        environments += it
    }

    /**
     * Utility for decoding and producing environment resources from an HDR file.
     *
     * Consumes the content of an HDR file and produces an [IndirectLight] and a [Skybox].
     *
     * @param buffer The content of the HDR File.
     * @param indirectLightSpecularFilter Generates a prefiltered indirect light cubemap.
     * SpecularFilter is a GPU based implementation of the specular probe pre-integration filter.
     * ** Launch the heaver computation. Expect 100-100ms on the GPU.**
     * @param textureOptions texture loader options
     * @param createSkybox Disable the skybox creation if you don't need it.
     *
     * @return the generated environment indirect light and skybox from the hdr.
     *
     * @see HDRLoader.createTexture
     */
    fun createHDREnvironment(
        buffer: Buffer,
        indirectLightSpecularFilter: Boolean = true,
        indirectLightApply: IndirectLight.Builder.() -> Unit = {},
        textureOptions: HDRLoader.Options = HDRLoader.Options(),
        createSkybox: Boolean = true
    ): Environment? {
        // Since we directly destroy the texture we call the `use` function and so don't pass lifecycle
        // to createTexture because it can be destroyed immediately.
        val textureCubemap = HDRLoader.createTexture(
            engine = engine,
            buffer = buffer,
            options = textureOptions
        )?.use(engine) { hdrTexture ->
            iblPrefilter.equirectangularToCubemap(equirect = hdrTexture)
        } ?: return null

        val reflections = if (indirectLightSpecularFilter) {
            iblPrefilter.specularFilter(textureCubemap).also {
                if (!createSkybox) {
                    engine.safeDestroyTexture(textureCubemap)
                }
            }
        } else {
            textureCubemap
        }
        val indirectLight = IndirectLight.Builder()
            .reflections(reflections)
            .apply(indirectLightApply)
            .build(engine)

        val skybox = textureCubemap.takeIf { createSkybox }?.let {
            Skybox.Builder()
                .environment(it)
                .build(engine)
        }
        return createEnvironment(indirectLight = indirectLight, skybox = skybox)
    }

    /**
     * Utility for decoding and producing environment resources from an HDR file.
     *
     * Consumes the content of an HDR file and produces an [IndirectLight] and a [Skybox].
     *
     * @param assetFileLocation The HDR asset file location.
     * @param indirectLightSpecularFilter Generates a prefiltered indirect light cubemap.
     * SpecularFilter is a GPU based implementation of the specular probe pre-integration filter.
     * ** Launch the heaver computation. Expect 100-100ms on the GPU.**
     * @param textureOptions texture loader options
     * @param createSkybox Disable the skybox creation if you don't need it.
     *
     * @return the generated environment indirect light and skybox from the hdr.
     *
     * @see HDRLoader.createTexture
     */
    fun createHDREnvironment(
        assetFileLocation: String,
        indirectLightSpecularFilter: Boolean = true,
        textureOptions: HDRLoader.Options = HDRLoader.Options(),
        createSkybox: Boolean = true,
    ): Environment? = createHDREnvironment(
        buffer = context.assets.readBuffer(assetFileLocation),
        indirectLightSpecularFilter = indirectLightSpecularFilter,
        textureOptions = textureOptions,
        createSkybox = createSkybox
    )

    /**
     * Utility for decoding and producing environment resources from an HDR file.
     *
     * Consumes the content of an HDR file and produces an [IndirectLight] and a [Skybox].
     *
     * @param rawResId The HDR File raw resource id.
     * @param indirectLightSpecularFilter Generates a prefiltered indirect light cubemap.
     * SpecularFilter is a GPU based implementation of the specular probe pre-integration filter.
     * ** Launch the heaver computation. Expect 100-100ms on the GPU.**
     * @param textureOptions texture loader options
     * @param createSkybox Disable the skybox creation if you don't need it.
     *
     * @return the generated environment indirect light and skybox from the hdr.
     *
     * @see HDRLoader.createTexture
     */
    fun createHDREnvironment(
        @RawRes rawResId: Int,
        indirectLightSpecularFilter: Boolean = true,
        textureOptions: HDRLoader.Options = HDRLoader.Options(),
        createSkybox: Boolean = true,
    ): Environment? = createHDREnvironment(
        buffer = context.resources.readBuffer(rawResId),
        indirectLightSpecularFilter = indirectLightSpecularFilter,
        textureOptions = textureOptions,
        createSkybox = createSkybox
    )

    /**
     * Utility for decoding and producing environment resources from an HDR file.
     *
     * Consumes the content of an HDR file and produces an [IndirectLight] and a [Skybox].
     *
     * @param file The HDR File.
     * @param indirectLightSpecularFilter Generates a prefiltered indirect light cubemap.
     * SpecularFilter is a GPU based implementation of the specular probe pre-integration filter.
     * ** Launch the heaver computation. Expect 100-100ms on the GPU.**
     * @param textureOptions texture loader options
     * @param createSkybox Disable the skybox creation if you don't need it.
     *
     * @return the generated environment indirect light and skybox from the hdr.
     *
     * @see HDRLoader.createTexture
     */
    fun createHDREnvironment(
        file: File,
        indirectLightSpecularFilter: Boolean = true,
        textureOptions: HDRLoader.Options = HDRLoader.Options(),
        createSkybox: Boolean = true,
    ): Environment? = createHDREnvironment(
        buffer = file.readBuffer(),
        indirectLightSpecularFilter = indirectLightSpecularFilter,
        textureOptions = textureOptions,
        createSkybox = createSkybox
    )

    /**
     * Utility for decoding and producing environment resources from an HDR file.
     *
     * Consumes the content of an HDR file and produces an [IndirectLight] and a [Skybox].
     *
     * @param url The HDR File url.
     * @param indirectLightSpecularFilter Generates a prefiltered indirect light cubemap.
     * SpecularFilter is a GPU based implementation of the specular probe pre-integration filter.
     * ** Launch the heaver computation. Expect 100-100ms on the GPU.**
     * @param textureOptions texture loader options
     * @param createSkybox Disable the skybox creation if you don't need it.
     *
     * @return the generated environment indirect light and skybox from the hdr.
     *
     * @see HDRLoader.createTexture
     */
    suspend fun loadHDREnvironment(
        url: String,
        indirectLightSpecularFilter: Boolean = true,
        textureOptions: HDRLoader.Options = HDRLoader.Options(),
        createSkybox: Boolean = true,
    ): Environment? = context.loadFileBuffer(url)?.let { buffer ->
        createHDREnvironment(
            buffer = buffer,
            indirectLightSpecularFilter = indirectLightSpecularFilter,
            textureOptions = textureOptions,
            createSkybox = createSkybox
        )
    }









    /**
     * Utility for decoding and producing environment resources from an HDR file.
     *
     * Consumes the content of an HDR file and produces an [IndirectLight] and a [Skybox].
     *
     * @param url The HDR File url.
     * @param indirectLightSpecularFilter Generates a prefiltered indirect light cubemap.
     * SpecularFilter is a GPU based implementation of the specular probe pre-integration filter.
     * ** Launch the heaver computation. Expect 100-100ms on the GPU.**
     * @param textureOptions texture loader options
     * @param createSkybox Disable the skybox creation if you don't need it.
     *
     * @return the generated environment indirect light and skybox from the hdr.
     *
     * @see HDRLoader.createTexture
     */
    suspend fun loadKTX1Environment(
        url: String,
        indirectLightSpecularFilter: Boolean = true,
        textureOptions: HDRLoader.Options = HDRLoader.Options(),
        createSkybox: Boolean = true,
    ): Environment? = context.loadFileBuffer(url)?.let { buffer ->
        createHDREnvironment(
            buffer = buffer,
            indirectLightSpecularFilter = indirectLightSpecularFilter,
            textureOptions = textureOptions,
            createSkybox = createSkybox
        )
    }

    fun destroyEnvironment(environment: Environment) {
        environment.indirectLight?.let { engine.safeDestroyIndirectLight(it) }
        environment.skybox?.let { engine.safeDestroySkybox(it) }
        environments -= environment
    }

    fun clear() {
        runCatching { coroutineScope.cancel() }
        environments.toList().forEach { destroyEnvironment(it) }
        environments.clear()
    }

    fun destroy() {
        clear()

        iblPrefilter.destroy()
    }
}