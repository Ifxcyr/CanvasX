package com.wuyr.canvasx

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Bitmap_Delegate
import android.graphics.Canvas
import android.graphics.FontFamily_Delegate
import com.android.ide.common.rendering.api.XmlParserFactory
import com.android.layoutlib.bridge.Bridge
import com.android.layoutlib.bridge.impl.ParserFactory
import com.android.resources.Density
import libcore.io.MemoryMappedFile_Delegate
import libcore.util.NativeAllocationRegistry
import libcore.util.XmlObjectFactory
import org.xmlpull.v1.XmlPullParser
import java.awt.image.BufferedImage
import java.io.File


class CanvasX @JvmOverloads constructor(width: Int, height: Int, density: Density = Density.XXXHIGH) : Canvas() {

    @Suppress("MemberVisibilityCanBePrivate")
    val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    init {
        Bitmap.setDefaultDensity(density.dpiValue)
        initBitmap(Bitmap_Delegate.createBitmap(bufferedImage, true, density))
    }

    private fun initBitmap(bitmap: Bitmap) {
        check(bitmap.isMutable) { "Immutable bitmap passed to Canvas constructor" }
        throwIfCannotDraw(bitmap)
        Canvas::class.java.run {
            mNativeCanvasWrapper = getDeclaredMethod("nInitRaster", Long::class.java).run {
                isAccessible = true
                invoke(null, bitmap.nativeInstance) as Long
            }
            getDeclaredField("mFinalizer").run {
                isAccessible = true
                set(
                    this@CanvasX, NativeAllocationRegistry.createMalloced(
                        classLoader, getDeclaredMethod("nGetNativeFinalizer").run {
                            isAccessible = true
                            invoke(null) as Long
                        }
                    ).registerNativeAllocation(this@CanvasX, mNativeCanvasWrapper)
                )
            }
            getDeclaredField("mBitmap").run {
                isAccessible = true
                set(this@CanvasX, bitmap)
            }
        }
        mDensity = bitmap.mDensity
    }

    private companion object {

        init {
            initSystemProperties()
        }

        private fun initSystemProperties() {
            Bridge::class.java.getDeclaredField("sPlatformProperties").run {
                isAccessible = true
                set(null, mapOf("debug.trace_resource_preload" to "0"))
            }
            File(System.getProperty("user.dir"), "fonts").run {
                if (!exists()) {
                    throw Resources.NotFoundException("Fonts not found! Please copy the \"fonts\" folder into the project root directory: $parent")
                }
                FontFamily_Delegate.setFontLocation(absolutePath)
                MemoryMappedFile_Delegate.setDataDir(absoluteFile.parentFile)
            }
            ParserFactory.setParserFactory(object : XmlParserFactory {
                private val parser = XmlObjectFactory.newXmlPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, true)
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                }

                override fun createXmlParserForPsiFile(s: String) = parser
                override fun createXmlParserForFile(s: String) = parser
                override fun createXmlParser() = parser
            })
        }
    }

}