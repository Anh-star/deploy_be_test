package com.cmcu.itstudy.handle;

/**
 * Thrown when the private storage object backing a paid preview is too
 * large to be safely streamed through the preview endpoint.
 *
 * <p>The StudyIT upload pipeline already caps raw object size at 25&nbsp;MB.
 * This exception signals that an object on the private Supabase bucket
 * exceeds that limit, so the preview endpoint must abort instead of
 * attempting to load the bytes into memory.
 *
 * <p>The accompanying {@link com.cmcu.itstudy.handle.GlobalExceptionHandler}
 * maps the exception to HTTP&nbsp;413 with a generic message; no bucket or
 * path information is ever echoed back to the caller.
 */
public class PreviewFileTooLargeException extends RuntimeException {

    public PreviewFileTooLargeException(String message) {
        super(message);
    }
}