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
