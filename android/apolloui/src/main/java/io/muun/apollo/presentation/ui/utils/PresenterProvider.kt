package io.muun.apollo.presentation.ui.utils

import io.muun.apollo.presentation.ui.base.Presenter

object PresenterProvider {

    private val idToPresenter = mutableMapOf<String, Presenter<*>>()

    fun <T: Presenter<*>> register(id: String, presenter: T) {
        check(!idToPresenter.contains(id))
        idToPresenter[id] = presenter
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: Presenter<*>> get(id: String): T? {
        return idToPresenter[id] as? T
    }

    fun unregister(id: String) {
        check(idToPresenter.contains(id))
        idToPresenter.remove(id)
    }
}