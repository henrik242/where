package no.synth.where.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual object PngDecoder {
    private const val BYTES_PER_PIXEL = 4

    actual fun decode(png: ByteArray): RgbImage? {
        val nsData = png.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = png.size.toULong())
        }
        val cgImage = UIImage.imageWithData(nsData)?.CGImage ?: return null

        val width = CGImageGetWidth(cgImage).toInt()
        val height = CGImageGetHeight(cgImage).toInt()
        val bytesPerRow = BYTES_PER_PIXEL * width
        val bytes = ByteArray(height * bytesPerRow)

        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val context = bytes.usePinned { pinned ->
            CGBitmapContextCreate(
                pinned.addressOf(0),
                width.toULong(),
                height.toULong(),
                8u,
                bytesPerRow.toULong(),
                colorSpace,
                // kCGImageAlphaNoneSkipLast: bitmap contexts can't hold unpremultiplied alpha,
                // so translucent pixels end up blended onto the zeroed buffer.
                5u
            )
        }
        CGColorSpaceRelease(colorSpace)
        if (context == null) return null

        bytes.usePinned {
            CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), cgImage)
        }
        CGContextRelease(context)

        val pixels = IntArray(width * height) { i ->
            val o = i * BYTES_PER_PIXEL
            ((bytes[o].toInt() and 0xFF) shl 16) or
                ((bytes[o + 1].toInt() and 0xFF) shl 8) or
                (bytes[o + 2].toInt() and 0xFF)
        }
        return RgbImage(width, height, pixels)
    }
}
