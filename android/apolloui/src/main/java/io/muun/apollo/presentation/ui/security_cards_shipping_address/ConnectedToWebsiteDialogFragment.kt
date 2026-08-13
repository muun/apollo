package io.muun.apollo.presentation.ui.security_cards_shipping_address

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.transition.TransitionManager
import io.muun.apollo.databinding.FragmentConnectedToWebsiteDialogBinding

class ConnectedToWebsiteDialogFragment : Fragment() {

    companion object {
        private const val TAG = "ConnectedToWebsiteDialogFragment"
        private const val ARG_WEBSITE_URL = "ARG_WEBSITE_URL"
        private const val ARG_ANIMATION_START_VIEW_ID = "ARG_ANIMATION_START_VIEW_ID"
        private const val ANIM_EXPAND = 220L
        private const val ANIM_BACKDROP_FADE = 220L
        private const val ANIM_CONTENT_FADE_IN = 150L
        private const val ANIM_CONTENT_FADE_OUT = 120L

        private const val BACKDROP_OPACITY = 0.5f

        fun show(
            fragmentManager: FragmentManager,
            websiteUrl: String,
            animationStartViewId: Int,
        ) {
            val fragment = ConnectedToWebsiteDialogFragment().apply {
                arguments = bundleOf(
                    ARG_WEBSITE_URL to websiteUrl,
                    ARG_ANIMATION_START_VIEW_ID to animationStartViewId,
                )
            }

            fragmentManager.beginTransaction()
                .add(android.R.id.content, fragment, TAG)
                .addToBackStack(null)
                .commit()
        }

        fun isShown(fragmentManager: FragmentManager): Boolean {
            return fragmentManager.findFragmentByTag(TAG) != null
        }
    }

    private val websiteUrlArgument: String
        get() = requireNotNull(requireArguments().getString(ARG_WEBSITE_URL))

    private val animationStartViewIdArgument: Int
        get() = requireArguments().getInt(ARG_ANIMATION_START_VIEW_ID)

    private val heroAnimation = HeroAnimation()

    private val animationStartView: View?
        get() = activity?.findViewById(animationStartViewIdArgument)

    private val animationSceneRoot: ViewGroup?
        get() = activity?.findViewById(android.R.id.content)

    private var _binding: FragmentConnectedToWebsiteDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentConnectedToWebsiteDialogBinding.inflate(
            inflater, container, false,
        )

        animationStartView?.visibility = View.INVISIBLE
        if (savedInstanceState == null) {
            binding.layoutCard.visibility = View.INVISIBLE
            binding.viewBackdrop.alpha = 0f
        }

        binding.viewContent.setWebsiteUrl(websiteUrlArgument)
        binding.viewContent.setOnCloseClick { dismiss() }
        binding.viewBackdrop.setOnClickListener { dismiss() }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().onBackPressedDispatcher
            .addCallback(viewLifecycleOwner) { dismiss() }

        if (savedInstanceState == null) {
            expand()
        }
    }

    override fun onDestroyView() {
        animationSceneRoot?.let { TransitionManager.endTransitions(it) }
        _binding = null
        super.onDestroyView()
    }

    private fun dismiss() {
        if (heroAnimation.isPlaying) return
        collapse().onEndAnimation { popSafely() }
    }

    private fun expand(): AnimationHandle {
        if (heroAnimation.isPlaying) return HeroAnimationScope.noop()
        val startView = animationStartView ?: return HeroAnimationScope.noop()
        val sceneRoot = animationSceneRoot ?: return HeroAnimationScope.noop()

        binding.viewContent.alpha = 0f

        return heroAnimation.run(sceneRoot) {
            inParallel {
                inSequence {
                    morph(from = startView, to = binding.layoutCard, duration = ANIM_EXPAND)
                    fade(binding.viewContent, to = 1f, duration = ANIM_CONTENT_FADE_IN)
                }
                fade(binding.viewBackdrop, to = BACKDROP_OPACITY, duration = ANIM_BACKDROP_FADE)
            }
        }
    }

    private fun collapse(): AnimationHandle {
        if (heroAnimation.isPlaying) return HeroAnimationScope.noop()
        val startView = animationStartView ?: return HeroAnimationScope.noop()
        val sceneRoot = animationSceneRoot ?: return HeroAnimationScope.noop()

        return heroAnimation.run(sceneRoot) {
            inSequence {
                fade(binding.viewContent, to = 0f, duration = ANIM_CONTENT_FADE_OUT)
                inParallel {
                    morph(from = binding.layoutCard, to = startView, duration = ANIM_EXPAND)
                        .onEndAnimation { startView.visibility = View.VISIBLE }
                    fade(binding.viewBackdrop, to = 0f, duration = ANIM_BACKDROP_FADE)
                }
            }
        }
    }

    private fun popSafely() {
        if (isAdded && !parentFragmentManager.isStateSaved) {
            parentFragmentManager.popBackStack()
        }
    }
}
