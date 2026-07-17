package no.synth.where.data

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Test fixtures embedded from shared/src/commonTest/resources/.
 * Canonical source files live there; these constants make them accessible in KMP tests
 * without platform-specific resource loading.
 */
object TestFixtures {

    /** Activity.fit from github.com/polyvertex/fitdecode — 14 GPS records from a Garmin device. */
    @OptIn(ExperimentalEncodingApi::class)
    val activityFit: ByteArray by lazy {
        Base64.decode(ACTIVITY_FIT_BASE64)
    }

    /** Zip made with `zip(1)`: DEFLATE-compressed a.gpx (name "Alpha") + b.gpx ("Beta") + readme.txt. */
    @OptIn(ExperimentalEncodingApi::class)
    val tripZip: ByteArray by lazy {
        Base64.decode(TRIP_ZIP_BASE64)
    }

    /** Zip of a 124 KB file (one line repeated 4000x) so DEFLATE spans several blocks / long matches. */
    @OptIn(ExperimentalEncodingApi::class)
    val bigZip: ByteArray by lazy {
        Base64.decode(BIG_ZIP_BASE64)
    }

    /** The exact content packed into [bigZip] as "big.gpx". */
    val bigZipContent: String = "The quick brown fox 0123456789\n".repeat(4000)

    /** Finder-style zip: track at trip/2020/a.gpx, directory entries, and __MACOSX/._a.gpx junk. */
    @OptIn(ExperimentalEncodingApi::class)
    val nestedZip: ByteArray by lazy {
        Base64.decode(NESTED_ZIP_BASE64)
    }

    private const val NESTED_ZIP_BASE64 = "UEsDBAoAAAAAABJQ8VwAAAAAAAAAAAAAAAAFABwAdHJpcC9VVAkAAyThWWok4VlqdXgLAAEE9QEAAAQAAAAAUEsDBAoAAAAAABJQ8VwAAAAAAAAAAAAAAAAKABwAdHJpcC8yMDIwL1VUCQADJOFZaiThWWp1eAsAAQT1AQAABAAAAABQSwMEFAAAAAgAElDxXDuA89uDAAAA7gAAAA8AHAB0cmlwLzIwMjAvYS5ncHhVVAkAAyThWWok4VlqdXgLAAEE9QEAAAQAAAAAs7GvyM1RKEstKs7Mz7NVMtQzULK347JJL6hAFjRUsrMpKcq2s8lLzE2180stLklNsdEHc0DixanpQD1ARkGJQk5iia2SqSXQHIUcsGYDIBOoKhOo1sjAyFDXAIRCDA2sDEAoykYfLAWkQNoxjDFEGGOIwxhTrMboQ90FZgBJoI/suABQSwMECgAAAAAAElDxXAAAAAAAAAAAAAAAAAkAHABfX01BQ09TWC9VVAkAAyThWWok4VlqdXgLAAEE9QEAAAQAAAAAUEsDBAoAAAAAABJQ8Vx5LeY9FgAAABYAAAAQABwAX19NQUNPU1gvLl9hLmdweFVUCQADJOFZaiThWWp1eAsAAQT1AQAABAAAAABNYWMgcmVzb3VyY2UgZm9yayBqdW5rUEsBAh4DCgAAAAAAElDxXAAAAAAAAAAAAAAAAAUAGAAAAAAAAAAQAO1BAAAAAHRyaXAvVVQFAAMk4VlqdXgLAAEE9QEAAAQAAAAAUEsBAh4DCgAAAAAAElDxXAAAAAAAAAAAAAAAAAoAGAAAAAAAAAAQAO1BPwAAAHRyaXAvMjAyMC9VVAUAAyThWWp1eAsAAQT1AQAABAAAAABQSwECHgMUAAAACAASUPFcO4Dz24MAAADuAAAADwAYAAAAAAABAAAApIGDAAAAdHJpcC8yMDIwL2EuZ3B4VVQFAAMk4VlqdXgLAAEE9QEAAAQAAAAAUEsBAh4DCgAAAAAAElDxXAAAAAAAAAAAAAAAAAkAGAAAAAAAAAAQAO1BTwEAAF9fTUFDT1NYL1VUBQADJOFZanV4CwABBPUBAAAEAAAAAFBLAQIeAwoAAAAAABJQ8Vx5LeY9FgAAABYAAAAQABgAAAAAAAEAAACkgZIBAABfX01BQ09TWC8uX2EuZ3B4VVQFAAMk4VlqdXgLAAEE9QEAAAQAAAAAUEsFBgAAAAAFAAUAlQEAAPIBAAAAAA=="

    private const val BIG_ZIP_BASE64 =
        "UEsDBBQAAAAIAJxK8VzQtpnoYwEAAGDkAQAHABwAYmlnLmdweFVUCQAD19dZatfXWWp1eAsAAQT1AQAABAAAAADtyckNQEAAAMC/KrYE99HHVkAIkdiQCOUrw2fmO3Gdw3lv0x7GKz1HWNIb8qKs6qbt+iGLWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWWmuttdZaa6211lprrbXWf/cHUEsBAh4DFAAAAAgAnErxXNC2mehjAQAAYOQBAAcAGAAAAAAAAQAAAKSBAAAAAGJpZy5ncHhVVAUAA9fXWWp1eAsAAQT1AQAABAAAAABQSwUGAAAAAAEAAQBNAAAApAEAAAAA"

    private const val TRIP_ZIP_BASE64 =
        "UEsDBBQAAAAIAONI8VzlnSswjQAAAAkBAAAFABwAYS5ncHhVVAkAA5rUWWqa1FlqdXgLAAEE9QEA" +
        "AAQAAAAAfY/BCsIwDIbve4rSuzYp6EGyDN/Bk7ceyhx2s2xF9vimQVFQhND8JHwfKXXrmMw9zst" +
        "wm1qLW7AdN9Tn9XOIlqnMV6YpjJGPKV8COc11vMReEAm5mBRKa/cghEnKgsIxRUYAcjVQGYT04G" +
        "EDKHVCOECtMzldSauyL6l/S/1Liv+ku59S97xZg7zyWW4eUEsDBBQAAAAIAONI8VwYB9EagAAA" +
        "AOwAAAAFABwAYi5ncHhVVAkAA5rUWWqa1FlqdXgLAAEE9QEAAAQAAAAAs7GvyM1RKEstKs7Mz7NV" +
        "MtQzULK347JJL6hAFjRUsrMpKcq2s8lLzE21c0otSbTRBzNBosWp6UAdQEZBiUJOYomtkhlIg0I" +
        "OWCtUbyZQrZGBkYGugZGugWGIoYGVAQhF2eiDpYAUSDuGMUYIY4xwGGOK1Rh9qLvADCAJ9I8dFwB" +
        "QSwMECgAAAAAA40jxXGpnQ2sMAAAADAAAAAoAHAByZWFkbWUudHh0VVQJAAOa1FlqmtRZanV4CwAB" +
        "BPUBAAAEAAAAAG5vdCBhIHRyYWNrClBLAQIeAxQAAAAIAONI8VzlnSswjQAAAAkBAAAFABgAAAAA" +
        "AAEAAACkgQAAAABhLmdweFVUBQADmtRZanV4CwABBPUBAAAEAAAAAFBLAQIeAxQAAAAIAONI8VwY" +
        "B9EagAAAAOwAAAAFABgAAAAAAAEAAACkgcwAAABiLmdweFVUBQADmtRZanV4CwABBPUBAAAEAAAA" +
        "AFBLAQIeAwoAAAAAAONI8VxqZ0NrDAAAAAwAAAAKABgAAAAAAAEAAACkgYsBAAByZWFkbWUudHh0" +
        "VVQFAAOa1FlqdXgLAAEE9QEAAAQAAAAAUEsFBgAAAAADAAMA5gAAANsBAAAAAA=="

    /** 10-point GPX track based on Unionsleden Karlstad–Moss, with elevation and timestamps. */
    val trackGpx: String = """
<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Where test fixture"
  xmlns="http://www.topografix.com/GPX/1/1">
  <metadata>
    <name>Unionsleden Karlstad - Moss</name>
    <time>2021-05-26T12:59:58Z</time>
  </metadata>
  <trk>
    <name>Unionsleden Karlstad - Moss</name>
    <trkseg>
      <trkpt lat="59.3803" lon="13.49668">
        <ele>51.9</ele>
        <time>2021-05-26T13:00:00Z</time>
      </trkpt>
      <trkpt lat="59.38027" lon="13.49385">
        <ele>51.9</ele>
        <time>2021-05-26T13:05:00Z</time>
      </trkpt>
      <trkpt lat="59.38055" lon="13.49074">
        <ele>51.0</ele>
        <time>2021-05-26T13:10:00Z</time>
      </trkpt>
      <trkpt lat="59.38042" lon="13.48974">
        <ele>51.3</ele>
        <time>2021-05-26T13:15:00Z</time>
      </trkpt>
      <trkpt lat="59.38032" lon="13.48861">
        <ele>50.6</ele>
        <time>2021-05-26T13:20:00Z</time>
      </trkpt>
      <trkpt lat="59.38001" lon="13.48753">
        <ele>50.3</ele>
        <time>2021-05-26T13:25:00Z</time>
      </trkpt>
      <trkpt lat="59.37978" lon="13.48678">
        <ele>50.1</ele>
        <time>2021-05-26T13:30:00Z</time>
      </trkpt>
      <trkpt lat="59.37944" lon="13.48591">
        <ele>49.8</ele>
        <time>2021-05-26T13:35:00Z</time>
      </trkpt>
      <trkpt lat="59.37921" lon="13.48513">
        <ele>50.2</ele>
        <time>2021-05-26T13:40:00Z</time>
      </trkpt>
      <trkpt lat="59.37895" lon="13.48432">
        <ele>50.5</ele>
        <time>2021-05-26T13:45:00Z</time>
      </trkpt>
    </trkseg>
  </trk>
</gpx>
""".trimIndent()

    // Base64 of shared/src/commonTest/resources/test_activity.fit (771 bytes)
    private const val ACTIVITY_FIT_BASE64 =
        "DBBkAPUCAAAuRklUQAABAAAFAwSMBASGAQKEAgKEAAEAAH////8p5gcSAA8AAQRAAAEAMQIAAoQB" +
        "AQJAAAEAMQEAAoQAAPBBAAEAFQX9BIYDBIYAAQABAQAEAQJBAAEAFQX9BIYDAQAAAQABAQAEAQIBKeYH" +
        "EgAAAABCAAEAFAb9BIYABIUBBIUFBIYCAoQGAoQCKeYHEh2FYS7L+7SXAAAAAg8zAAACKeYHEx2FYS7L" +
        "+7SYAAAAAg8zAAACKeYHFB2FYS7L+7SYAAAAAg8zAAACKeYHFR2FYUDL+7SCAAAAFQ8zAAACKeYHFh2F" +
        "YUDL+7R5AAAAHA8zAAACKeYHFx2FYUbL+7RyAAAAIw8zAAACKeYHGB2FYUrL+7RsAAAAKQ8zAAACKeYH" +
        "GR2FYXfL+7QUAAAAcg8zAAACKeYHGh2FYY3L+7O0AAAAuQ8zAFwCKeYHGx2FYa7L+7M8AAABEw8zAJgC" +
        "KeYHHB2FYczL+7LXAAABXw8zANECKeYHHR2FYarL+7J5AAABpg8zAQYCKeYHHh2FYV/L+7KNAAAB7Q8z" +
        "ATMCKeYHHx2FYRLL+7JXAAACPQ8zAXABKeYHHwAABABDAAEAExT9BIYCBIYDBIUEBIUFBIUGBIUHBIYI" +
        "BIYJBIb+AoQLAoQMAoQNAoQOAoQVAoQWAoQAAQABAQAYAQAZAQADKeYHoynmBxIdhWEuy/u0lx2FYRLL" +
        "+7JXAAA1tQAANbUAAAI9AAAAAAAAAaEBcAAAAAAJAQcBQQABABUF/QSGAwSGAAEAAQEABAECASnmB6MA" +
        "AAABCAkBRAABABIV/QSGAgSGAwSFBASFBwSGCASGCQSG/gKECwKEDQKEDgKEDwKEFgKEFwKEGQKEGgKE" +
        "AAEAAQEABQEABgEAHAEABCnmB6Mp5gcSHYVhLsv7tJcAADW1AAA1tQAAAj0AAAAAAAABoQFwAAAAAAAA" +
        "AAEJAQEAAEUAAQAiB/0EhgAEhgUEhgEChAIBAAMBAAQBAAUp5gejAAA1tSnlz2MAAQAaAdWh"
}
