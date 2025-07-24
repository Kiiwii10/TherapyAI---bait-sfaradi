package com.example.therapyai.util;


import androidx.lifecycle.Observer;

/**
 * Used as a wrapper for data that is exposed via a LiveData that represents an event.
 * Helps prevent events (like navigation or SnackBar messages) from firing multiple times
 * on configuration change.
 */
public class Event<T> {

    private T content;
    private boolean hasBeenHandled = false;

    public Event(T content) {
        if (content == null) {
            throw new IllegalArgumentException("Event content cannot be null");
        }
        this.content = content;
    }

    /**
     * Returns the content and prevents its use again.
     */
    public T getContentIfNotHandled() {
        if (hasBeenHandled) {
            return null;
        } else {
            hasBeenHandled = true;
            return content;
        }
    }

    /**
     * Returns the content, even if it's already been handled.
     */
    public T peekContent() {
        return content;
    }

    /**
     * Returns whether the content has already been handled.
     */
    public boolean hasBeenHandled() {
        return hasBeenHandled;
    }


    /**
     * An {@link Observer} for {@link Event}s, simplifying the pattern of checking if the {@link Event}'s content has
     * already been handled.
     *
     * @param <T> The type of the content.
     */
    public static class EventObserver<T> implements Observer<Event<T>> {
        private final OnEventUnhandledContent<T> onEventUnhandledContent;

        public EventObserver(OnEventUnhandledContent<T> onEventUnhandledContent) {
            this.onEventUnhandledContent = onEventUnhandledContent;
        }

        @Override
        public void onChanged(Event<T> event) {
            if (event != null) {
                T content = event.getContentIfNotHandled();
                if (content != null) {
                    onEventUnhandledContent.onEventUnhandledContent(content);
                }
            }
        }
    }

    /**
     * Functional interface for handling unhandled event content.
     * @param <T>
     */
    @FunctionalInterface
    public interface OnEventUnhandledContent<T> {
        void onEventUnhandledContent(T value);
    }
}
