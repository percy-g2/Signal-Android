package org.thoughtcrime.securesms.stories.viewer.reply.direct

import android.content.DialogInterface
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.KeyboardEntryDialogFragment
import org.thoughtcrime.securesms.components.emoji.EmojiEventListener
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.keyboard.KeyboardPage
import org.thoughtcrime.securesms.keyboard.KeyboardPagerViewModel
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardPageFragment
import org.thoughtcrime.securesms.keyboard.emoji.search.EmojiSearchFragment
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.viewer.page.StoryViewerPageViewModel
import org.thoughtcrime.securesms.stories.viewer.reply.composer.StoryReactionBar
import org.thoughtcrime.securesms.stories.viewer.reply.composer.StoryReplyComposer
import org.thoughtcrime.securesms.util.FragmentDialogs.displayInDialogAboveAnchor
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * Dialog displayed when the user decides to send a private reply to a story.
 */
class StoryDirectReplyDialogFragment :
  KeyboardEntryDialogFragment(R.layout.stories_reply_to_story_fragment),
  EmojiKeyboardPageFragment.Callback,
  EmojiEventListener,
  EmojiSearchFragment.Callback,
  ReactWithAnyEmojiBottomSheetDialogFragment.Callback {

  private val lifecycleDisposable = LifecycleDisposable()

  private val viewModel: StoryDirectReplyViewModel by viewModels(
    factoryProducer = {
      StoryDirectReplyViewModel.Factory(storyId, recipientId, StoryDirectReplyRepository(requireContext()))
    }
  )

  private val keyboardPagerViewModel: KeyboardPagerViewModel by viewModels(
    ownerProducer = { requireActivity() }
  )

  private val storyViewerPageViewModel: StoryViewerPageViewModel by viewModels(
    ownerProducer = { requireParentFragment() }
  )

  private lateinit var composer: StoryReplyComposer

  private val storyId: Long
    get() = requireArguments().getLong(ARG_STORY_ID)

  private val recipientId: RecipientId?
    get() = requireArguments().getParcelable(ARG_RECIPIENT_ID)

  override val withDim: Boolean = true

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    lifecycleDisposable.bindTo(viewLifecycleOwner)

    composer = view.findViewById(R.id.input)
    composer.callback = object : StoryReplyComposer.Callback {
      override fun onSendActionClicked() {
        lifecycleDisposable += viewModel.sendReply(composer.consumeInput().first)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe {
            Toast.makeText(requireContext(), R.string.StoryDirectReplyDialogFragment__reply_sent, Toast.LENGTH_LONG).show()
            dismissAllowingStateLoss()
          }
      }

      override fun onPickReactionClicked() {
        displayInDialogAboveAnchor(composer.reactionButton, R.layout.stories_reaction_bar_layout) { dialog, view ->
          view.findViewById<StoryReactionBar>(R.id.reaction_bar).apply {
            callback = object : StoryReactionBar.Callback {
              override fun onTouchOutsideOfReactionBar() {
                dialog.dismiss()
              }

              override fun onReactionSelected(emoji: String) {
                dialog.dismiss()
                sendReaction(emoji)
              }

              override fun onOpenReactionPicker() {
                dialog.dismiss()
                ReactWithAnyEmojiBottomSheetDialogFragment.createForStory().show(childFragmentManager, null)
              }
            }
            animateIn()
          }
        }
      }

      override fun onInitializeEmojiDrawer(mediaKeyboard: MediaKeyboard) {
        keyboardPagerViewModel.setOnlyPage(KeyboardPage.EMOJI)
        mediaKeyboard.setFragmentManager(childFragmentManager)
      }

      override fun onHeightChanged(height: Int) = Unit
    }

    viewModel.state.observe(viewLifecycleOwner) { state ->
      if (state.recipient != null) {
        composer.displayPrivacyChrome(state.recipient)
      }

      if (state.storyRecord != null) {
        composer.setQuote(state.storyRecord as MediaMmsMessageRecord)
      }
    }
  }

  override fun onResume() {
    super.onResume()

    ViewUtil.focusAndShowKeyboard(composer)
  }

  override fun onPause() {
    super.onPause()

    ViewUtil.hideKeyboard(requireContext(), composer)
  }

  override fun openEmojiSearch() {
    composer.openEmojiSearch()
  }

  override fun onKeyboardHidden() {
    if (!composer.isRequestingEmojiDrawer) {
      super.onKeyboardHidden()
    }
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    storyViewerPageViewModel.setIsDisplayingDirectReplyDialog(false)
  }

  companion object {
    const val REQUEST_EMOJI = "request.code.emoji"

    private const val ARG_STORY_ID = "arg.story.id"
    private const val ARG_RECIPIENT_ID = "arg.recipient.id"

    fun create(storyId: Long, recipientId: RecipientId? = null): DialogFragment {
      return StoryDirectReplyDialogFragment().apply {
        arguments = Bundle().apply {
          putLong(ARG_STORY_ID, storyId)
          putParcelable(ARG_RECIPIENT_ID, recipientId)
        }
      }
    }
  }

  override fun onEmojiSelected(emoji: String?) {
    composer.onEmojiSelected(emoji)
  }

  override fun closeEmojiSearch() {
    composer.closeEmojiSearch()
  }

  override fun onKeyEvent(keyEvent: KeyEvent?) = Unit

  override fun onReactWithAnyEmojiDialogDismissed() = Unit

  override fun onReactWithAnyEmojiSelected(emoji: String) {
    sendReaction(emoji)
  }

  private fun sendReaction(emoji: String) {
    lifecycleDisposable += viewModel.sendReaction(emoji)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe {
        setFragmentResult(
          REQUEST_EMOJI,
          Bundle().apply {
            putString(REQUEST_EMOJI, emoji)
          }
        )
        Toast.makeText(requireContext(), R.string.StoryDirectReplyDialogFragment__reaction_sent, Toast.LENGTH_LONG).show()
        dismissAllowingStateLoss()
      }
  }
}
