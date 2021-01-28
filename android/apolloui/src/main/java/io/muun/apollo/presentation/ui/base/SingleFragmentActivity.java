package io.muun.apollo.presentation.ui.base;

import io.muun.apollo.presentation.ui.activity.extension.ErrorFragmentExtension;
import io.muun.apollo.presentation.ui.bundler.StringListBundler;
import io.muun.apollo.presentation.ui.listener.OnBackPressedListener;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.common.utils.Preconditions;

import android.os.Bundle;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import icepick.State;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;

public abstract class SingleFragmentActivity<PresenterT extends Presenter>
        extends BaseActivity<PresenterT> implements SingleFragmentView {

    @Inject
    ErrorFragmentExtension errorFragmentExtension;

    @State(StringListBundler.class)
    List<String> backStackTags = new ArrayList<>();

    private OnBackPressedListener backPressedListener;

    protected BaseFragment getInitialFragment() {
        return null;
    }

    @IdRes
    protected int getFragmentsContainer() {
        return 0; // valid, signals the Activity will manage Fragments without helpers
    }

    public abstract MuunHeader getHeader();

    @Override
    protected void setUpExtensions() {
        super.setUpExtensions();
        addExtension(errorFragmentExtension);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addInitialFragment();
    }

    private void addInitialFragment() {

        if (hasFragments()) {
            return;
        }

        final int fragmentsContainer = getFragmentsContainer();
        final Fragment initialFragment = getInitialFragment();

        if (fragmentsContainer != 0 && initialFragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(fragmentsContainer, initialFragment)
                    .commit();
        }
    }

    private boolean hasFragments() {
        final List<Fragment> fragments = getSupportFragmentManager().getFragments();

        // Some implementations return `null`, others an empty list. We check both:
        return fragments != null && !fragments.isEmpty();
    }

    @Override
    public void replaceFragment(@NotNull Fragment fragment, boolean canGoBackToCurrent) {
        final int fragmentsContainer = getFragmentsContainer();
        Preconditions.checkState(fragmentsContainer > 0);

        final FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction()
                .replace(fragmentsContainer, fragment);

        pushToBackStack(transaction, canGoBackToCurrent);
        transaction.commit();
    }

    @Override
    public void clearFragmentBackStack() {
        final FragmentManager fragMan = getSupportFragmentManager();

        for (int i = 0; i < fragMan.getBackStackEntryCount(); ++i) {
            fragMan.popBackStackImmediate();
        }
    }

    @Override
    public void setLoading(boolean loading) {

    }

    @Override
    public void onBackPressed() {
        if (shouldIgnoreBackAndExit()) {
            return;
        }

        boolean wasHandled = false;

        if (backPressedListener != null) {
            wasHandled = backPressedListener.onBackPressed();
        }

        if (!wasHandled) {
            popBackStack();
        }
    }

    private void pushToBackStack(FragmentTransaction transaction, boolean canGoBackToCurrent) {
        String transactionTag = Boolean.toString(canGoBackToCurrent);

        if (canGoBackToCurrent) {
            transactionTag = transactionTag + backStackTags.size();
            backStackTags.add(transactionTag);
        }

        transaction.addToBackStack(transactionTag);

        // Curious? Let me tell you the story of the Back Stack Hack.

        // The user navigates forward through the Fragments A -> B -> C. We use `replace()` in our
        // transitions. When she wants to go back, we want the B Fragment to be skipped, so the
        // return trip is C -> A.

        // Our first approach would probably be (and indeed was before this gorgeous hack) to avoid
        // placing the B -> C transition in the back-stack. When going back, we should land directly
        // in A -- and we would, after some fashion. Only we'd be looking at BOTH the A and C
        // Fragments visually overlapped on screen, competing to catch input events.

        // Oh, the joys of Android. This happens because the back-stack doesn't contain Fragments,
        // it contains *transitions*. So, if we pushed every transition, it would look like this:

        // - add(A)
        // - replace(A, B)
        // - replace(B, C)
        // (C is visible on screen)

        // But we *didn't* push the last transition, thinking to skip B on our way back, so it looks
        // like this:

        // - add(A)
        // - replace(A, B)
        // (C is visible on screen)

        // So, when popping the stack, the FragmentManager will perform the reverse operation of the
        // last transition, and attempt to replace(B, A).

        // Of course, B doesn't exist anymore, so replace(B, A) just executes add(A), and I suppose
        // that makes sense in some universe. Nobody ever removes C. The result: both A and C end up
        // on screen, comically overlapped. This is actually pretty funny. Here's a screenshot:

        // https://drive.google.com/open?id=19kHSOZyCmjXpOWg0puyPpANuNWaVx8yv (needs Muun account)

        // Now, as to the solution. We push *all* transitions to the back-stack, but we tag
        // them with the the value of `canGoBackToCurrent`, as seen above this comment.

        // See the `popBackStack` method for episode 2 of this saga.
    }

    private void popBackStack() {
        // In `pushToBackStack`, we decided to push all transitions to the back-stack, using
        // the boolean strings "true"/"false" as tags to denote whether we actually wanted to go
        // back to those stack entries or not.

        // Here, instead of just popping the top stack item (as the Activity class does), we'll
        // keep popping until we remove the first transition tagged with "true":
        // UPDATE: since popBackStackImmediate() called with POP_BACK_STACK_INCLUSIVE pops EVERY
        // fragmentTransaction with a name/tag matching the specfied transactionTag, we make a
        // small modification introduce unique transactionTags for each transaction and keep
        // track of them. Otherwise, in the case of fragment transactions A -> B -> C (all with
        // canGoBackCurrent = true), pressing back in fragment C would remove C but also B.
        final String transactionTag;
        if (! backStackTags.isEmpty()) {
            transactionTag = backStackTags.get(backStackTags.size() - 1);
        } else {
            transactionTag = Boolean.toString(true);
        }

        final boolean hadPreviousFragment = getSupportFragmentManager().popBackStackImmediate(
                transactionTag,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
        );

        if (hadPreviousFragment) {
            backStackTags.remove(backStackTags.size() - 1);
        } else {
            finishActivity();
        }
    }

    /**
     * Register a handler for the BACK action.
     */
    public void setOnBackPressedListener(OnBackPressedListener listener) {
        this.backPressedListener = listener;
    }

    public void showError(@StringRes int titleRes, @StringRes int descriptionRes, String... args) {
        errorFragmentExtension.showError(titleRes, descriptionRes, args);
    }
}
