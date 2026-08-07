package com.cmcu.itstudy.service.contract;

import com.cmcu.itstudy.dto.office.ProcessExecutionResult;
import com.cmcu.itstudy.dto.office.ProcessRunOptions;

import java.util.List;

/**
 * Generic, cross-platform process execution contract used by
 * {@code LibreOfficeDocumentConverter}.
 *
 * <p>The implementation owns ONLY the {@link ProcessBuilder},
 * {@link Process}, the {@link ProcessHandle} tree, the stream
 * consumers and the bounded diagnostic buffers. It does NOT own the
 * LibreOffice semaphore, the conversion temporary directories, the
 * document bytes, the PDF validation or any Supabase call.</p>
 *
 * <p>Arguments are passed as a {@code List<String>}; the implementation
 * must NOT invoke any shell interpreter ({@code sh}, {@code bash},
 * {@code cmd}, {@code powershell}) and must NOT rely on any
 * OS-level process management command ({@code pkill}, {@code pgrep},
 * {@code taskkill}).</p>
 */
public interface ProcessRunner {

    /**
     * Run the supplied argv with the supplied options.
     *
     * @param argv    fully-qualified arguments; the first entry is the
     *                executable name resolved by the OS via {@code PATH}
     * @param options timeouts, capture caps, environment overrides
     * @return a {@link ProcessExecutionResult}; never {@code null}
     */
    ProcessExecutionResult run(List<String> argv, ProcessRunOptions options);
}
