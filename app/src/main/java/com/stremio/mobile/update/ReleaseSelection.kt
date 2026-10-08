package com.stremio.mobile.update

internal enum class ReleaseChannel(val tagPrefix: String, val assetPrefix: String) {
    MOBILE("v", "MW-Play-Mobile"),
    TV("tv-v", "MW-Play-TV"),
}

internal data class ReleaseAsset(val name: String, val url: String)

internal data class ReleaseCandidate(
    val tagName: String,
    val assets: List<ReleaseAsset>,
    val releaseNotes: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

internal data class SelectedRelease(
    val versionName: String,
    val tagName: String,
    val apk: ReleaseAsset,
    val checksum: ReleaseAsset,
    val releaseNotes: String,
)

private data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
}

private val versionPattern = Regex("""\d+\.\d+\.\d+""")

private fun parseVersion(value: String): ReleaseVersion? {
    if (!versionPattern.matches(value)) return null
    val parts = value.split('.').map { it.toIntOrNull() ?: return null }
    return ReleaseVersion(parts[0], parts[1], parts[2])
}

internal fun selectUpdateRelease(
    releases: List<ReleaseCandidate>,
    channel: ReleaseChannel,
    currentVersion: String,
    supportedAbis: List<String>,
): SelectedRelease? {
    val installed = parseVersion(currentVersion)
    val candidates = releases.mapNotNull { release ->
        if (release.draft || release.prerelease || !release.tagName.startsWith(channel.tagPrefix)) return@mapNotNull null
        val versionName = release.tagName.removePrefix(channel.tagPrefix)
        val version = parseVersion(versionName) ?: return@mapNotNull null
        if (installed != null && version <= installed) return@mapNotNull null
        Triple(release, versionName, version)
    }.sortedByDescending { it.third }

    for ((release, versionName) in candidates) {
        val prefixes = buildList {
            add("${channel.assetPrefix}-v$versionName")
            // Releases before platform-specific names belonged to the Mobile channel.
            if (channel == ReleaseChannel.MOBILE) add("MW-Play-v$versionName")
        }
        for (abi in (supportedAbis + "universal").distinct()) {
            for (prefix in prefixes) {
                val apk = release.assets.firstOrNull { it.name == "$prefix-$abi-release.apk" } ?: continue
                val checksum = release.assets.firstOrNull { it.name == "$prefix-SHA256SUMS.txt" } ?: continue
                return SelectedRelease(versionName, release.tagName, apk, checksum, release.releaseNotes)
            }
        }
    }
    return null
}

internal fun expectedReleaseChecksum(checksumText: String, apkName: String): String? {
    val linePattern = Regex("""^([0-9a-fA-F]{64})[ \t]+\*?(.+)$""")
    val matches = checksumText.lineSequence().mapNotNull { line ->
        val match = linePattern.matchEntire(line.trim()) ?: return@mapNotNull null
        match.groupValues[1].takeIf { match.groupValues[2] == apkName }
    }.toList()
    return matches.singleOrNull()
}
