package app.flicky.data.trust

interface RepoSignatureVerifier {
    fun isTrustedRepoUrl(url: String): Boolean
}

class HttpsOnlyVerifier : RepoSignatureVerifier {
    override fun isTrustedRepoUrl(url: String): Boolean = url.startsWith("https://")
}