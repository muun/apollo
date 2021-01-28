package io.muun.apollo.presentation.ui.base;

import android.os.Bundle;
import androidx.annotation.Nullable;

import javax.validation.constraints.NotNull;

public interface Presenter<ViewT extends BaseView> extends ParentPresenter {

    void onViewCreated(@Nullable Bundle savedInstanceState);

    void setUp(@NotNull Bundle arguments);

    void afterSetUp();

    void tearDown();

    void saveState(@NotNull Bundle state);

    void restoreState(@Nullable Bundle state);

    void setView(@NotNull ViewT view);

}
