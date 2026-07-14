package com.ice.iwaramanager.data.repository

internal data class CleanupScope(
    val targetSourceIds: Set<String>,
    val coversAllConfiguredSources: Boolean
)

internal fun resolveCleanupScope(
    configuredSourceIds: Collection<String>,
    requestedSourceIds: Collection<String>
): CleanupScope {
    val configured = configuredSourceIds.toSet()
    val requested = requestedSourceIds.toSet()
    return CleanupScope(
        targetSourceIds = if (requested.isEmpty()) configured else configured.intersect(requested),
        coversAllConfiguredSources = requested.isEmpty() || requested == configured
    )
}
