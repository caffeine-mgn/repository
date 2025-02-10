package pw.binom.repo.repositories.maven

import pw.binom.date.DateTime

data class MetaData(
    val groupId: MavenGroup,
    val artifactId: String,
    val version: MavenVersion?,
    val latest: MavenVersion?,
    val release: MavenVersion?,
    val versions: List<MavenVersion>,
    val lastUpdate: DateTime,
)