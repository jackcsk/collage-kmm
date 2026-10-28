package com.jackcheung.collage.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIGraphicsPopContext
import platform.UIKit.UIGraphicsPushContext
import platform.UIKit.UIImage
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.max
import kotlin.math.roundToInt

/** Width in pixels, as displayed (orientation applied). */
internal val UIImage.pixelWidth: Int get() = (size.useContents { width } * scale).roundToInt()

/** Height in pixels, as displayed (orientation applied). */
internal val UIImage.pixelHeight: Int get() = (size.useContents { height } * scale).roundToInt()

/** [width]×[height] scaled by [scale] (never up), at least 1×1. */
internal fun scaledSize(width: Int, height: Int, scale: Float): Pair<Int, Int> {
    val s = scale.coerceAtMost(1f)
    return max(1, (width * s).roundToInt()) to max(1, (height * s).roundToInt())
}

/**
 * Draws the image upright (its orientation applied) into a [width]×[height] premultiplied RGBA
 * buffer whose first row is the top of the image, then passes the context and buffer to [use].
 */
private fun <T> UIImage.renderUpright(width: Int, height: Int, use: (CGContextRef, ByteArray) -> T): T {
    val rowBytes = width * 4
    val pixels = ByteArray(rowBytes * height)
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    try {
        return pixels.usePinned { pinned ->
            val context = CGBitmapContextCreate(
                pinned.addressOf(0),
                width.toULong(),
                height.toULong(),
                8uL,
                rowBytes.toULong(),
                colorSpace,
                CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
            ) ?: error("Can't create a ${width}x$height bitmap context")
            try {
                // UIKit drawing expects a top-left origin.
                CGContextTranslateCTM(context, 0.0, height.toDouble())
                CGContextScaleCTM(context, 1.0, -1.0)
                UIGraphicsPushContext(context)
                drawInRect(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
                UIGraphicsPopContext()
                use(context, pixels)
            } finally {
                CGContextRelease(context)
            }
        }
    } finally {
        CGColorSpaceRelease(colorSpace)
    }
}

internal fun UIImage.toImageBitmap(width: Int, height: Int): ImageBitmap {
    val pixels = renderUpright(width, height) { _, bytes -> bytes }
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
    bitmap.installPixels(pixels)
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}

/** Upright copy for image analysis; the caller releases it with `CGImageRelease`. */
internal fun UIImage.toUprightCGImage(width: Int, height: Int): CGImageRef? =
    renderUpright(width, height) { context, _ -> CGBitmapContextCreateImage(context) }

internal fun ByteArray.toNSData(): NSData = usePinned { NSData.dataWithBytes(it.addressOf(0), size.toULong()) }

internal fun onMain(block: () -> Unit) = dispatch_async(dispatch_get_main_queue(), block)

/** The controller currently on top, which can present another one. */
internal fun UIViewController.topPresented(): UIViewController {
    var top = this
    while (true) {
        top = top.presentedViewController ?: return top
    }
}
