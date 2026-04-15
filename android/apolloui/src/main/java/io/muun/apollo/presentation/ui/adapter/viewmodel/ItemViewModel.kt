package io.muun.apollo.presentation.ui.adapter.viewmodel

import io.muun.apollo.presentation.ui.adapter.holder.ViewHolderFactory

interface ItemViewModel {

    fun type(typeFactory: ViewHolderFactory): Int
}
