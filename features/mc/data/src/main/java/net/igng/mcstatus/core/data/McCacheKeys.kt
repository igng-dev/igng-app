package net.igng.mcstatus.core.data

import kotlinx.serialization.builtins.ListSerializer
import net.igng.core.data.IgngFreshnessPolicy
import net.igng.core.data.IgngSwrDiskCodec
import net.igng.core.data.jsonDiskCodec
import net.igng.mcstatus.core.model.BootstrapResponse

object McCacheTags {
    const val BOOTSTRAP = "mc:bootstrap"
    const val SERVERS = "mc:servers"
    const val STATUS = "mc:status"
    const val CHAT_SOURCES = "mc:chat:sources"
    const val CHAT_MESSAGES = "mc:chat:messages"
    const val WIKI = "mc:wiki"
    const val PLAYER_ACCOUNTS = "mc:player:accounts"
    const val TICKETS = "mc:tickets"
    const val LANDS = "mc:lands"
    const val MEDALS = "mc:medals"
    const val HALL = "mc:hall"
    const val ADMIN = "mc:admin"
    const val DEVICES = "core:devices"
}

object McCacheKeys {
    const val BOOTSTRAP = "mc:bootstrap"
    const val SERVERS = "mc:servers"
    const val STATUS = "mc:status"
    const val CHAT_SOURCES = "mc:chat:sources"
    const val PLAYER_ACCOUNTS = "mc:player:accounts"
    const val PLAYER_TELEPORTS = "mc:player:teleports"
    const val PLAYER_FAKE_PLAYERS = "mc:player:fakes"
    const val PLAYER_MEDALS = "mc:player:medals"
    const val PLAYER_PERFECT = "mc:player:perfect"
    const val PLAYER_PERMISSIONS = "mc:player:permissions"
    const val PLAYER_VAULT_OVERVIEW = "mc:vault:overview"
    const val HALL_LANDS = "mc:hall:lands"
    const val HALL_FAKES = "mc:hall:fakes"
    const val HALL_TICKETS = "mc:hall:tickets"
    const val PERFECT_ROSTER = "mc:perfect:public"
    const val ADMIN_TICKETS = "mc:admin:tickets"
    const val ADMIN_HISTORIAN = "mc:admin:historian"
    const val CORE_DEVICES = "core:devices"

    fun statusHistory(preset: StatusRangePreset): String = "mc:status:history:${preset.name}"
    fun chatLatest(source: String, targetId: String): String = "mc:chat:latest:$source:$targetId"
    fun wikiCatalog(kb: String = "IGNGmc"): String = "mc:wiki:catalog:$kb"
    fun wikiPage(pageId: Int, kb: String = "IGNGmc"): String = "mc:wiki:page:$kb:$pageId"
    fun playerLands(query: String): String = "mc:player:lands:${query.trim()}"
    fun playerLoginRecords(username: String?, page: Int): String = "mc:player:logins:${username.orEmpty()}:$page"
    fun playerStats(serverId: Int?): String = "mc:player:stats:${serverId ?: "all"}"
    fun playerVaultItems(vaultId: String, query: String): String = "mc:vault:items:$vaultId:${query.trim()}"
    fun playerVaultHistory(vaultId: String): String = "mc:vault:history:$vaultId"
    fun ticketsList(relation: String, includeClosed: Boolean, query: String): String =
        "mc:tickets:$relation:$includeClosed:${query.trim()}"
    fun ticketDetail(id: Int): String = "mc:ticket:$id"
    fun playerPassport(igngId: Int): String = "mc:passport:$igngId"
    fun mcCharacter(username: String): String = "mc:character:$username"
    fun adminPunishments(query: String): String = "mc:admin:punishments:${query.trim()}"
    fun adminOps(rangeHours: Long): String = "mc:admin:ops:$rangeHours"
}

object McCachePolicies {
    val Status = IgngFreshnessPolicy(freshTtlMs = 15_000L)
    val Chat = IgngFreshnessPolicy(freshTtlMs = 20_000L)
    val Realtime = IgngFreshnessPolicy(freshTtlMs = 20_000L)
    val Standard = IgngFreshnessPolicy(freshTtlMs = 60_000L)
    val Detail = IgngFreshnessPolicy(freshTtlMs = 180_000L)
    val Static = IgngFreshnessPolicy(freshTtlMs = 900_000L)
    val WikiArticle = IgngFreshnessPolicy(freshTtlMs = 1_800_000L)
}

object McDiskCodecs {
    val bootstrap: IgngSwrDiskCodec<BootstrapResponse> =
        jsonDiskCodec("mc_bootstrap_v1", BootstrapResponse.serializer())

    val servers: IgngSwrDiskCodec<List<ServerFamilyDto>> =
        jsonDiskCodec("mc_servers_v1", ListSerializer(ServerFamilyDto.serializer()))

    val status: IgngSwrDiskCodec<PublicStatusResponse> =
        jsonDiskCodec("mc_status_v1", PublicStatusResponse.serializer())

    val chatSources: IgngSwrDiskCodec<ChatSourcesResponse> =
        jsonDiskCodec("mc_chat_sources_v1", ChatSourcesResponse.serializer())

    fun wikiCatalog(kb: String = "IGNGmc"): IgngSwrDiskCodec<WikiCatalogResponse> =
        jsonDiskCodec("mc_wiki_catalog_v1_$kb", WikiCatalogResponse.serializer())

    fun wikiPage(pageId: Int, kb: String = "IGNGmc"): IgngSwrDiskCodec<WikiPageDetail> =
        jsonDiskCodec("mc_wiki_page_v1_${kb}_$pageId", WikiPageDetail.serializer())
}
