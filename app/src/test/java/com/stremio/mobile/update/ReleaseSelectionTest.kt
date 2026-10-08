package com.stremio.mobile.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseSelectionTest {
    @Test
    fun mobileAndTvChooseOnlyTheirOwnTagsFromTheSameResponse() {
        val mobile = release(ReleaseChannel.MOBILE, "1.0.3")
        val tv = release(ReleaseChannel.TV, "1.0.4")
        val releases = listOf(tv, mobile)
        assertEquals("v1.0.3", select(releases, ReleaseChannel.MOBILE)?.tagName)
        assertEquals("tv-v1.0.4", select(releases, ReleaseChannel.TV)?.tagName)
    }

    @Test
    fun matchingAbiCannotSelectAnAssetFromTheOtherPlatform() {
        for (channel in ReleaseChannel.entries) {
            val other = ReleaseChannel.entries.first { it != channel }
            val wrongAssets = release(other, "1.0.3").assets
            assertNull(select(listOf(release(channel, "1.0.3").copy(assets = wrongAssets)), channel))
        }
    }

    @Test
    fun legacyApksAreAcceptedOnlyOnTheMobileChannel() {
        val legacy = release(ReleaseChannel.MOBILE, "1.0.3", legacy = true)
        assertEquals("MW-Play-v1.0.3-arm64-v8a-release.apk", select(listOf(legacy), ReleaseChannel.MOBILE)?.apk?.name)
        assertNull(select(listOf(legacy.copy(tagName = "tv-v1.0.3")), ReleaseChannel.TV))
    }

    @Test
    fun newMobileNamesArePreferredOverLegacyNamesForTheSameAbi() {
        val mobile = release(ReleaseChannel.MOBILE, "1.0.3")
        val legacy = release(ReleaseChannel.MOBILE, "1.0.3", legacy = true)
        val selected = select(listOf(mobile.copy(assets = legacy.assets + mobile.assets)), ReleaseChannel.MOBILE)
        assertEquals("MW-Play-Mobile-v1.0.3-arm64-v8a-release.apk", selected?.apk?.name)
        assertEquals("MW-Play-Mobile-v1.0.3-SHA256SUMS.txt", selected?.checksum?.name)
    }

    @Test
    fun versionsAreSortedNumericallyInsteadOfLexicallyOrByResponseOrder() {
        for (channel in ReleaseChannel.entries) {
            val releases = listOf("1.0.9", "1.0.10", "1.0.3").map { release(channel, it) }
            assertEquals("1.0.10", select(releases, channel)?.versionName)
        }
    }

    @Test
    fun draftAndPrereleaseVersionsAreIgnored() {
        for (channel in ReleaseChannel.entries) {
            val releases = listOf(
                release(channel, "9.0.0").copy(draft = true),
                release(channel, "8.0.0").copy(prerelease = true),
                release(channel, "1.0.3"),
            )
            assertEquals("1.0.3", select(releases, channel)?.versionName)
        }
    }

    @Test
    fun invalidTagsAndOverflowingVersionsAreIgnored() {
        for (channel in ReleaseChannel.entries) {
            val invalidVersions = listOf("1.0", "1.0.3-beta", "1.0.3.0", "-1.0.3", "2147483648.0.0", "1.0.3 ")
            for (version in invalidVersions) {
                assertNull(select(listOf(release(channel, version)), channel))
            }
        }
    }

    @Test
    fun sameOrOlderVersionIsNotAnUpdate() {
        for (channel in ReleaseChannel.entries) {
            assertNull(select(listOf(release(channel, "1.0.2"), release(channel, "1.0.1")), channel))
        }
    }

    @Test
    fun supportedAbiOrderIsPreservedAndUniversalIsTheFallback() {
        for (channel in ReleaseChannel.entries) {
            val candidate = release(channel, "1.0.3", abis = listOf("arm64-v8a", "armeabi-v7a", "universal"))
            val preferred = select(listOf(candidate), channel, listOf("armeabi-v7a", "arm64-v8a"))
            assertEquals("${channel.assetPrefix}-v1.0.3-armeabi-v7a-release.apk", preferred?.apk?.name)
            val fallback = select(listOf(candidate), channel, listOf("x86_64", "x86"))
            assertEquals("${channel.assetPrefix}-v1.0.3-universal-release.apk", fallback?.apk?.name)
        }
    }

    @Test
    fun noCompatibleAbiOrUniversalMeansNoUpdate() {
        for (channel in ReleaseChannel.entries) {
            assertNull(select(listOf(release(channel, "1.0.3", abis = listOf("x86"))), channel))
        }
    }

    @Test
    fun missingOrDifferentPlatformChecksumCannotOfferAnApk() {
        for (channel in ReleaseChannel.entries) {
            val candidate = release(channel, "1.0.3")
            val apkOnly = candidate.assets.filter { it.name.endsWith(".apk") }
            assertNull(select(listOf(candidate.copy(assets = apkOnly)), channel))
            val other = ReleaseChannel.entries.first { it != channel }
            val wrongChecksum = release(other, "1.0.3").assets.filter { it.name.endsWith(".txt") }
            assertNull(select(listOf(candidate.copy(assets = apkOnly + wrongChecksum)), channel))
        }
    }

    @Test
    fun newestCompatibleCompleteReleaseIsUsedWhileAnotherIsIncomplete() {
        for (channel in ReleaseChannel.entries) {
            val incomplete = release(channel, "1.0.4").copy(assets = emptyList())
            assertEquals("1.0.3", select(listOf(incomplete, release(channel, "1.0.3")), channel)?.versionName)
        }
    }

    @Test
    fun checksumMatchesTheExactApkNameAndSupportsSha256sumTextAndBinaryFormat() {
        val apk = "MW-Play-TV-v1.0.3-arm64-v8a-release.apk"
        val hash = "aB".repeat(32)
        assertEquals(hash, expectedReleaseChecksum("$hash  $apk\n", apk))
        assertEquals(hash, expectedReleaseChecksum("$hash *$apk\r\n", apk))
        assertNull(expectedReleaseChecksum("$hash  prefix-$apk", apk))
        assertNull(expectedReleaseChecksum("$hash  $apk.extra", apk))
    }

    @Test
    fun absentMalformedOrAmbiguousChecksumsAreRejected() {
        val apk = "MW-Play-Mobile-v1.0.3-universal-release.apk"
        assertNull(expectedReleaseChecksum("", apk))
        assertNull(expectedReleaseChecksum("${"a".repeat(63)}  $apk", apk))
        assertNull(expectedReleaseChecksum("${"g".repeat(64)}  $apk", apk))
        val entry = "${"a".repeat(64)}  $apk"
        assertNull(expectedReleaseChecksum("$entry\n$entry", apk))
    }

    private fun select(
        releases: List<ReleaseCandidate>,
        channel: ReleaseChannel,
        abis: List<String> = listOf("arm64-v8a", "armeabi-v7a"),
    ) = selectUpdateRelease(releases, channel, "1.0.2", abis)

    private fun release(
        channel: ReleaseChannel,
        version: String,
        abis: List<String> = listOf("arm64-v8a"),
        legacy: Boolean = false,
    ): ReleaseCandidate {
        val prefix = if (legacy) "MW-Play-v$version" else "${channel.assetPrefix}-v$version"
        val names = abis.map { "$prefix-$it-release.apk" } + "$prefix-SHA256SUMS.txt"
        return ReleaseCandidate(
            tagName = "${channel.tagPrefix}$version",
            assets = names.map { ReleaseAsset(it, "https://example.test/$it") },
        )
    }
}
