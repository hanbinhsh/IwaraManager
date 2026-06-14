package com.ice.iwaramanager.domain.parser

object IwaraFilenameParser {
    private val videoExtensions = setOf(
        "mp4",
        "mkv",
        "webm",
        "mov",
        "m4v"
    )

    private val standardPattern = Regex(
        pattern = """^Iwara\s*-\s*(.*?)\s*\[([A-Za-z0-9_-]{8,})\]\s*\[([^]]+)\]$""",
        option = RegexOption.IGNORE_CASE
    )

    private val titleIdQualityPattern = Regex(
        pattern = """^(.*?)\s*\[([A-Za-z0-9_-]{8,})\]\s*\[([^]]+)\]$"""
    )

    private val titleIdPattern = Regex(
        pattern = """^(.*?)\s*\[([A-Za-z0-9_-]{8,})\]$"""
    )

    private val bracketIdPattern = Regex(
        pattern = """\[([A-Za-z0-9_-]{8,})\]"""
    )

    fun parse(fileName: String): IwaraFilenameParseResult {
        val extension = extractExtension(fileName)

        val nameWithoutExt = if (extension != null) {
            fileName.removeSuffix(".$extension")
        } else {
            fileName
        }.trim()

        standardPattern.matchEntire(nameWithoutExt)?.let { match ->
            return IwaraFilenameParseResult(
                rawName = fileName,
                cleanedName = nameWithoutExt,
                titleFromFilename = cleanTitle(match.groupValues[1]),
                videoId = match.groupValues[2].trim().ifBlank { null },
                quality = match.groupValues[3].trim().ifBlank { null },
                extension = extension
            )
        }

        titleIdQualityPattern.matchEntire(nameWithoutExt)?.let { match ->
            return IwaraFilenameParseResult(
                rawName = fileName,
                cleanedName = nameWithoutExt,
                titleFromFilename = cleanTitle(match.groupValues[1]),
                videoId = match.groupValues[2].trim().ifBlank { null },
                quality = match.groupValues[3].trim().ifBlank { null },
                extension = extension
            )
        }

        titleIdPattern.matchEntire(nameWithoutExt)?.let { match ->
            return IwaraFilenameParseResult(
                rawName = fileName,
                cleanedName = nameWithoutExt,
                titleFromFilename = cleanTitle(match.groupValues[1]),
                videoId = match.groupValues[2].trim().ifBlank { null },
                quality = extractQuality(nameWithoutExt),
                extension = extension
            )
        }

        val bracketId = bracketIdPattern.find(nameWithoutExt)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.ifBlank { null }

        if (bracketId != null) {
            val title = nameWithoutExt
                .replace("[$bracketId]", "")
                .replace(Regex("""\[[^]]+\]"""), "")
                .let { cleanTitle(it) }

            return IwaraFilenameParseResult(
                rawName = fileName,
                cleanedName = nameWithoutExt,
                titleFromFilename = title,
                videoId = bracketId,
                quality = extractQuality(nameWithoutExt),
                extension = extension
            )
        }

        return IwaraFilenameParseResult(
            rawName = fileName,
            cleanedName = nameWithoutExt,
            titleFromFilename = cleanTitle(nameWithoutExt),
            videoId = null,
            quality = extractQuality(nameWithoutExt),
            extension = extension
        )
    }

    private fun extractExtension(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .trim()
            .lowercase()

        return ext.takeIf {
            it in videoExtensions
        }
    }

    private fun cleanTitle(value: String): String? {
        return value
            .replace(
                regex = Regex("""^\s*Iwara\s*-\s*""", RegexOption.IGNORE_CASE),
                replacement = ""
            )
            .trim()
            .ifBlank { null }
    }

    private fun extractQuality(name: String): String? {
        val qualities = listOf(
            "Source",
            "2160p",
            "1440p",
            "1080p",
            "720p",
            "540p",
            "360p",
            "Preview"
        )

        return qualities.firstOrNull { quality ->
            name.contains("[$quality]", ignoreCase = true) ||
                    name.contains(quality, ignoreCase = true)
        }
    }
}