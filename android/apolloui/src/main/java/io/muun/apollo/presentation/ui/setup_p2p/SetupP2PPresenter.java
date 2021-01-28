package io.muun.apollo.presentation.ui.setup_p2p;

import io.muun.apollo.domain.action.ContactActions;
import io.muun.apollo.domain.action.UserActions;
import io.muun.apollo.domain.action.base.ActionState;
import io.muun.apollo.domain.model.P2PSetupStep;
import io.muun.apollo.domain.model.User;
import io.muun.apollo.domain.model.UserPhoneNumber;
import io.muun.apollo.domain.model.UserProfile;
import io.muun.apollo.domain.selector.UserSelector;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.BaseFragment;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.SingleFragmentView;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.apollo.presentation.ui.fragments.phone_number.PhoneNumberFragment;
import io.muun.apollo.presentation.ui.fragments.phone_number.PhoneNumberParentPresenter;
import io.muun.apollo.presentation.ui.fragments.profile.ProfileFragment;
import io.muun.apollo.presentation.ui.fragments.profile.ProfileParentPresenter;
import io.muun.apollo.presentation.ui.fragments.sync_contacts.SyncContactsFragment;
import io.muun.apollo.presentation.ui.fragments.sync_contacts.SyncContactsParentPresenter;
import io.muun.apollo.presentation.ui.fragments.verification_code.VerificationCodeFragment;
import io.muun.apollo.presentation.ui.fragments.verification_code.VerificationCodeParentPresenter;
import io.muun.common.Optional;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.model.PhoneNumber;
import io.muun.common.model.VerificationType;

import android.Manifest;
import android.net.Uri;
import android.os.Bundle;
import icepick.State;
import rx.Observable;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

@PerActivity
public class SetupP2PPresenter extends BasePresenter<SingleFragmentView> implements
        PhoneNumberParentPresenter,
        VerificationCodeParentPresenter,
        ProfileParentPresenter,
        SyncContactsParentPresenter {

    private final UserActions userActions;
    private final ContactActions contactActions;

    private final UserSelector userSel;

    @State
    P2PSetupStep currentStep;

    /**
     * Creates a presenter.
     */
    @Inject
    public SetupP2PPresenter(UserActions userActions,
                             ContactActions contactActions,
                             UserSelector userSel) {
        this.userActions = userActions;
        this.contactActions = contactActions;
        this.userSel = userSel;
    }

    @Override
    public void setUp(@NotNull Bundle arguments) {
        super.setUp(arguments);

        setUpCreatePhoneAction();
        setUpConfirmPhoneAction();
        setUpUpdateProfileAction();
    }

    private void setUpCreatePhoneAction() {
        final Observable<?> observable = userActions.createPhoneAction
                .getState()
                .compose(handleStates(view::setLoading, this::handleError))
                .doOnNext(ignored -> navigateToStep(P2PSetupStep.CONFIRM_PHONE));

        subscribeTo(observable);
    }

    private void setUpConfirmPhoneAction() {
        final Observable<?> observable = userActions.confirmPhoneAction
                .getState()
                .compose(handleStates(view::setLoading, this::handleError))
                .doOnNext(ignored -> navigateToStep(P2PSetupStep.PROFILE));

        subscribeTo(observable);
    }

    private void setUpUpdateProfileAction() {
        final Observable<?> observable = userActions.createProfileAction
                .getState()
                .compose(handleStates(view::setLoading, this::handleError))
                .doOnNext(ignored -> navigateToStep(P2PSetupStep.SYNC_CONTACTS));

        subscribeTo(observable);
    }

    @Override
    public void submitPhoneNumber(PhoneNumber phoneNumber) {
        checkStep(P2PSetupStep.PHONE);
        userActions.createPhoneAction.run(phoneNumber);
    }

    @Override
    public Observable<ActionState<UserPhoneNumber>> watchSubmitPhoneNumber() {
        return userActions.createPhoneAction.getState();
    }

    @Override
    public void submitVerificationCode(String verificationCode) {
        checkStep(P2PSetupStep.CONFIRM_PHONE);
        userActions.confirmPhoneAction.run(verificationCode);
    }

    @Override
    public Observable<ActionState<UserPhoneNumber>> watchSubmitVerificationCode() {
        return userActions.confirmPhoneAction.getState();
    }

    @Override
    public void changePhoneNumber() {
        checkStep(P2PSetupStep.CONFIRM_PHONE);

        // Reset phone number, otherwise the step will fast forward to confirm phone
        userActions.resetPhoneNumber();

        navigateToStep(P2PSetupStep.PHONE);
    }

    @Override
    public void resendVerificationCode(VerificationType verificationType) {
        checkStep(P2PSetupStep.CONFIRM_PHONE);
        userActions.resendVerificationCodeAction.run(verificationType);
    }

    @Override
    public void submitProfile(String firstName, String lastName, Optional<Uri> pictureUri) {
        checkStep(P2PSetupStep.PROFILE);

        final UserProfile userProfile = new UserProfile(firstName, lastName);

        pictureUri.ifPresent(userActions::setPendingProfilePicture);

        userActions.createProfileAction.run(userProfile);
    }

    @Override
    public Observable<ActionState<UserProfile>> watchSubmitProfile() {
        return userActions.createProfileAction.getState();
    }

    /**
     * Complete the p2p payments setup process and navigate to home screen.
     */
    @Override
    public void reportContactPermissionGranted() {
        // User can be in one of these two steps. Depends on whether it is the first time it is
        // syncing contacts or if it has previously done it and we have stored the
        // hasP2PEnabled flag in Houston.
        checkStep(P2PSetupStep.SYNC_CONTACTS, P2PSetupStep.FINISHED);

        contactActions.initialSyncPhoneContactsAction.run();

        navigator.navigateToHome(getContext());
        analytics.report(new AnalyticsEvent.E_P2P_SETUP_SUCCESSFUL());
        view.finishActivity();
    }

    @Override
    public void reportContactsPermissionNeverAskAgain() {
        checkStep(P2PSetupStep.SYNC_CONTACTS);

        userActions.reportContactsPermissionNeverAskAgain();

        navigator.navigateToHome(getContext());
        view.finishActivity();
    }

    public BaseFragment getInitialStep() {
        currentStep = getInitialP2PSetupStep(); // TODO this shouldn't be a side-effect
        return createStepFragment(currentStep);
    }

    private void navigateToStep(P2PSetupStep step) {
        currentStep = step;
        view.replaceFragment(createStepFragment(step), false);
    }

    private void checkStep(P2PSetupStep...allowed) {

        for (P2PSetupStep p2PSetupStep : allowed) {
            if (currentStep == p2PSetupStep) {
                return;
            }
        }

        throw new IllegalStateException(currentStep + " step cannot do this");
    }

    private P2PSetupStep getInitialP2PSetupStep() {
        final User user = userSel.get();

        if (!user.phoneNumber.isPresent()) {
            return P2PSetupStep.PHONE;

        } else if (!user.phoneNumber.get().isVerified()) {
            return P2PSetupStep.CONFIRM_PHONE;

        } else if (!user.profile.isPresent()) {
            return P2PSetupStep.PROFILE;

        } else if (!hasP2PEnabled()) {
            return P2PSetupStep.SYNC_CONTACTS;

        } else {

            return P2PSetupStep.FINISHED;
        }
    }

    private boolean hasP2PEnabled() {
        return userSel.get().hasP2PEnabled
                && hasPermission(Manifest.permission.READ_CONTACTS);
    }

    private BaseFragment createStepFragment(P2PSetupStep step) {
        switch (step) {

            case PHONE:
                return new PhoneNumberFragment();

            case CONFIRM_PHONE:
                return new VerificationCodeFragment();

            case PROFILE:
                return new ProfileFragment();

            case SYNC_CONTACTS:
                return new SyncContactsFragment();

            case FINISHED:
                throw new IllegalStateException("Cannot navigate to FINISHED P2P Setup State");

            default:
                throw new MissingCaseError(step, "Peer to Peer Payments setup");
        }
    }
}
