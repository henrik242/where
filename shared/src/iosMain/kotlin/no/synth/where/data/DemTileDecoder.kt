package no.synth.where.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual object DemTileDecoder {
    actual fun decodeElevation(pngData: ByteArray, pixelX: Int, pixelY: Int): Double? {
        val nsData = pngData.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = pngData.size.toULong())
        }
        val image = UIImage.imageWithData(nsData) ?: return null
        val cgImage = image.CGImage ?: return null

        val width = CGImageGetWidth(cgImage).toInt()
        val height = CGImageGetHeight(cgImage).toInt()
        if (pixelX < 0 || pixelX >= width || pixelY < 0 || pixelY >= height) return null

        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val bytesPerPixel = 4
        val bytesPerRow = bytesPerPixel * width
        val pixelData = ByteArray(height * bytesPerRow)

        val context = pixelData.usePinned { pinned ->
            CGBitmapContextCreate(
                pinned.addressOf(0),
                width.toULong(),
                height.toULong(),
                8u,
                bytesPerRow.toULong(),
                colorSpace,
                5u // kCGImageAlphaNoneSkipLast
            )
        } ?: return null

        CGContextDrawImage(
            context,
            CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()),
            cgImage
        )
        CGContextRelease(context)

        val offset = (pixelY * bytesPerRow) + (pixelX * bytesPerPixel)
        val r = pixelData[offset].toInt() and 0xFF
        val g = pixelData[offset + 1].toInt() and 0xFF
        val b = pixelData[offset + 2].toInt() and 0xFF

        // Terrarium encoding: elevation = (r * 256 + g + b / 256) - 32768
        return (r * 256.0 + g + b / 256.0) - 32768.0
    }
}
