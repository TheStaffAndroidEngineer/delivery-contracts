pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "delivery-contracts"
include(":app", ":cart-api", ":cart-impl", ":feature-saved", ":contract-tests", ":test-lab")
