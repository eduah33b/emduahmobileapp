package com.emduah.personal.ui

internal enum class AppPage { HOME, APPS, WALLPAPERS, EDITOR, SETTINGS }

/** Feature screens remember their entry tab; editing is a child of Wallpapers. */
internal data class AppLocation(val page: AppPage = AppPage.HOME, val origin: AppPage = AppPage.HOME) {
    fun open(page: AppPage) = AppLocation(page, if (this.page == AppPage.HOME || this.page == AppPage.APPS) this.page else origin)
    fun back(): AppLocation = when (page) {
        AppPage.EDITOR -> copy(page = AppPage.WALLPAPERS)
        AppPage.WALLPAPERS, AppPage.SETTINGS -> AppLocation(origin, origin)
        AppPage.APPS -> AppLocation()
        AppPage.HOME -> this
    }
    val isRoot get() = page == AppPage.HOME || page == AppPage.APPS
}
