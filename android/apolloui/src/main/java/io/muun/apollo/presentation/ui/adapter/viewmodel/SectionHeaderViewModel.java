package io.muun.apollo.presentation.ui.adapter.viewmodel;

import io.muun.apollo.presentation.ui.adapter.holder.ViewHolderFactory;

public class SectionHeaderViewModel implements ItemViewModel {

    public final SectionHeader model;

    public SectionHeaderViewModel(String title) {
        this(title, false);
    }


    public SectionHeaderViewModel(String title, boolean showDivider) {
        this.model = new SectionHeader(title, showDivider);
    }

    @Override
    public int type(ViewHolderFactory typeFactory) {
        return typeFactory.getLayoutRes(model);
    }

    public static class SectionHeader {

        public final String title;
        public final boolean showDivider;

        public SectionHeader(String title, boolean showDivider) {
            this.title = title;
            this.showDivider = showDivider;
        }
    }
}
