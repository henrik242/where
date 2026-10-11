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
    private const val BYTES_PER_PIXEL = 4

    actual fun decodeElevation(pngData: ByteArray, pixelX: Int, pixelY: Int): Double? {
        val rgba = decodeRgba(pngData) ?: return null
        if (pixelX < 0 || pixelX >= rgba.width || pixelY < 0 || pixelY >= rgba.height) return null
        return rgba.elevationAt(pixelY * rgba.width + pixelX)
    }

    actual fun decodeTile(pngData: ByteArray): DemTile? {
        val rgba = decodeRgba(pngData) ?: return null
        val out = FloatArray(rgba.width * rgba.height) { i -> rgba.elevationAt(i).toFloat() }
        return DemTile(rgba.width, rgba.height, out)
    }

    private class Rgba(val width: Int, val height: Int, val bytes: ByteArray) {
        fun elevationAt(pixel: Int): Double {
            val offset = pixel * BYTES_PER_PIXEL
            return terrariumElevation(
                bytes[offset].toInt() and 0xFF,
                bytes[offset + 1].toInt() and 0xFF,
                bytes[offset + 2].toInt() and 0xFF,
            )
        }
    }

    private fun decodeRgba(pngData: ByteArray): Rgba? {
        val nsData = pngData.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = pngData.size.toULong())
        }
        val image = UIImage.imageWithData(nsData) ?: return null
        val cgImage = image.CGImage ?: return null

        val width = CGImageGetWidth(cgImage).toInt()
        val height = CGImageGetHeight(cgImage).toInt()
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val bytesPerRow = BYTES_PER_PIXEL * width
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
        return Rgba(width, height, pixelData)
    }
}
