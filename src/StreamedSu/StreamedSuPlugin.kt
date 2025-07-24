package com.recloudstream.streamedsu

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.json.JSONObject

class StreamedSuPlugin : MainAPI() {
    override var name = "Streamed.su"
    override var mainUrl = "https://streamed.su"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    private val imageBase = "$mainUrl/images/"

    // === FILTERS ===
    override val mainPage = listOf(
        MainPageData(
            request = "",  // We'll handle everything in getMainPage
            name = "Live Sports",
            type = TvType.Live
        )
    )

    override val searchFilters = listOf(
        SelectSearchFilter(
            name = "Sport",
            options = listOf("all") + getSportNames(),
            default = 0
        ),
        CheckBoxSearchFilter(
            name = "Live Only",
            default = true
        )
    )

    // === GET MAIN PAGE ===
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val sports = getSports()

        val rows = sports.map { sport ->
            val matches = getMatches(sport.id, liveOnly = true)
            HomePageList(sport.name, matches, isHorizontalImages = true)
        }

        return HomePageResponse(rows)
    }

    // === SEARCH ===
    override suspend fun search(query: String, filters: List<SearchFilter>): List<SearchResponse> {
        val sportFilter = filters.getOrNull(0) as? SelectSearchFilter
        val liveOnly = (filters.getOrNull(1) as? CheckBoxSearchFilter)?.state ?: true

        val selectedSport = sportFilter?.selected?.toLowerCase() ?: "all"

        val sportId = if (selectedSport == "all") null else getSports().find {
            it.name.equals(selectedSport, true)
        }?.id

        return getMatches(sportId, liveOnly)
            .filter { it.name.contains(query, ignoreCase = true) }
    }

    // === LOAD STREAM ===
    override suspend fun load(url: String): LoadResponse {
        val streamData = app.get("$mainUrl/api/streams?id=$url").text
        val streamArray = JSONObject("{\"streams\":$streamData}").getJSONArray("streams")

        if (streamArray.length() == 0) throw ErrorLoadingException("No streams found")

        val streamUrl = streamArray.getJSONObject(0).getString("url")

        return LiveStreamLoadResponse(
            name = "Streamed.su",
            url = url,
            streamUrl = streamUrl,
            referer = mainUrl
        )
    }

    // === GET MATCHES ===
    private suspend fun getMatches(sportId: String?, liveOnly: Boolean): List<SearchResponse> {
        val endpoint = if (sportId != null) "$mainUrl/api/matches?sport=$sportId"
                      else "$mainUrl/api/matches"

        val matchData = app.get(endpoint).text
        val matches = parseJson<List<Match>>(matchData)

        return matches.filter { if (liveOnly) it.streams != 0 else true }.map {
            LiveSearchResponse(
                name = "${it.team1} vs ${it.team2}",
                url = it.id.toString(),
                apiName = name,
                posterUrl = "$imageBase${it.logo1}",
                backgroundPosterUrl = "$imageBase${it.logo2}",
                quality = null
            )
        }
    }

    // === SPORTS ===
    private suspend fun getSports(): List<Sport> {
        val json = app.get("$mainUrl/api/sports").text
        return parseJson(json)
    }

    private fun getSportNames(): List<String> {
        // Static fallback list
        return listOf("Football", "Basketball", "UFC", "Boxing", "Tennis", "Cricket", "WWE", "Hockey", "Rugby")
    }

    // === DATA CLASSES ===
    data class Sport(val id: String, val name: String)
    data class Match(
        val id: Int,
        val team1: String,
        val team2: String,
        val logo1: String,
        val logo2: String,
        val streams: Int
    )
}
