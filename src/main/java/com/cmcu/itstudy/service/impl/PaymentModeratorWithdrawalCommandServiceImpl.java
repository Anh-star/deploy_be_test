package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.dto.paymentmoderator.withdrawal.PaymentModeratorWithdrawalActionResponseDto;
import com.cmcu.itstudy.entity.WithdrawalRequest;
import com.cmcu.itstudy.enums.NotificationType;
import com.cmcu.itstudy.enums.WithdrawalStatus;
import com.cmcu.itstudy.handle.WithdrawalStateConflictException;
import com.cmcu.itstudy.repository.WithdrawalRequestRepository;
import com.cmcu.itstudy.service.contract.NotificationService;
import com.cmcu.itstudy.service.contract.PaymentModeratorWithdrawalCommandService;
import com.cmcu.itstudy.service.contract.SellerBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentModeratorWithdrawalCommandServiceImpl
        implements PaymentModeratorWithdrawalCommandService {

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final SellerBalanceService sellerBalanceService;
    private final NotificationService notificationService;

    @Override
    public PaymentModeratorWithdrawalActionResponseDto approveWithdrawal(
            UUID withdrawalId,
            UUID moderatorId,
            String adminNote
    ) {
        if (withdrawalId == null) {
            throw new IllegalArgumentException("Withdrawal ID is required");
        }
        if (moderatorId == null) {
            throw new IllegalArgumentException("Moderator ID is required");
        }

        WithdrawalRequest withdrawal = withdrawalRequestRepository
                .findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Withdrawal request not found"
                ));

        if (withdrawal.getStatus() != WithdrawalStatus.PENDING) {
            throw new WithdrawalStateConflictException(
                    "Withdrawal request has already been processed"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        withdrawal.setStatus(WithdrawalStatus.APPROVED);
        withdrawal.setApprovedByAdminId(moderatorId);
        withdrawal.setApprovedAt(now);
        withdrawal.setAdminNote(normalizeOptionalNote(adminNote));

        WithdrawalRequest saved = withdrawalRequestRepository
                .saveAndFlush(withdrawal);

        try {
            String reqCode = saved.getRequestCode() != null ? saved.getRequestCode() : "";
            String note = saved.getAdminNote() != null ? saved.getAdminNote().trim() : "";
            String msg = "Yêu cầu rút tiền #" + reqCode + " của bạn đã được phê duyệt." + (!note.isBlank() ? " Ghi chú: " + note : "");
            notificationService.createAndPush(
                    saved.getSellerId(),
                    moderatorId,
                    NotificationType.WITHDRAWAL_APPROVED,
                    saved.getId().toString(),
                    "WITHDRAWAL",
                    msg
            );
        } catch (Exception e) {
            // Ignore notification failure
        }

        return toActionResponse(saved);
    }

    @Override
    public PaymentModeratorWithdrawalActionResponseDto rejectWithdrawal(
            UUID withdrawalId,
            UUID moderatorId,
            String adminNote
    ) {
        if (withdrawalId == null) {
            throw new IllegalArgumentException("Withdrawal ID is required");
        }
        if (moderatorId == null) {
            throw new IllegalArgumentException("Moderator ID is required");
        }
        if (adminNote == null || adminNote.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }

        WithdrawalRequest withdrawal = withdrawalRequestRepository
                .findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Withdrawal request not found"
                ));

        WithdrawalStatus currentStatus = withdrawal.getStatus();
        if (currentStatus != WithdrawalStatus.PENDING
                && currentStatus != WithdrawalStatus.APPROVED) {
            throw new WithdrawalStateConflictException(
                    "Withdrawal request has already been processed"
            );
        }

        sellerBalanceService.releaseLockedToAvailable(
                withdrawal.getSellerId(),
                withdrawal.getAmount()
        );

        LocalDateTime now = LocalDateTime.now();
        withdrawal.setStatus(WithdrawalStatus.REJECTED);
        withdrawal.setRejectedByAdminId(moderatorId);
        withdrawal.setRejectedAt(now);
        withdrawal.setAdminNote(adminNote.trim());

        WithdrawalRequest saved = withdrawalRequestRepository
                .saveAndFlush(withdrawal);

        try {
            String reqCode = saved.getRequestCode() != null ? saved.getRequestCode() : "";
            String reason = saved.getAdminNote() != null ? saved.getAdminNote().trim() : "";
            String msg = "Yêu cầu rút tiền #" + reqCode + " của bạn đã bị từ chối." + (!reason.isBlank() ? " Lý do: " + reason : "");
            notificationService.createAndPush(
                    saved.getSellerId(),
                    moderatorId,
                    NotificationType.WITHDRAWAL_REJECTED,
                    saved.getId().toString(),
                    "WITHDRAWAL",
                    msg
            );
        } catch (Exception e) {
            // Ignore notification failure
        }

        return toActionResponse(saved);
    }

    @Override
    public PaymentModeratorWithdrawalActionResponseDto markPaid(
            UUID withdrawalId,
            UUID moderatorId,
            String adminNote
    ) {
        if (withdrawalId == null) {
            throw new IllegalArgumentException("Withdrawal ID is required");
        }
        if (moderatorId == null) {
            throw new IllegalArgumentException("Moderator ID is required");
        }
        if (adminNote == null || adminNote.isBlank()) {
            throw new IllegalArgumentException(
                    "Payment confirmation note is required"
            );
        }

        WithdrawalRequest withdrawal = withdrawalRequestRepository
                .findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Withdrawal request not found"
                ));

        if (withdrawal.getStatus() != WithdrawalStatus.APPROVED) {
            throw new WithdrawalStateConflictException(
                    "Withdrawal request has already been processed"
            );
        }

        sellerBalanceService.moveLockedToWithdrawn(
                withdrawal.getSellerId(),
                withdrawal.getAmount()
        );

        LocalDateTime now = LocalDateTime.now();
        withdrawal.setStatus(WithdrawalStatus.PAID);
        withdrawal.setPaidByAdminId(moderatorId);
        withdrawal.setPaidAt(now);
        withdrawal.setAdminNote(adminNote.trim());

        WithdrawalRequest saved = withdrawalRequestRepository
                .saveAndFlush(withdrawal);

        try {
            String reqCode = saved.getRequestCode() != null ? saved.getRequestCode() : "";
            String note = saved.getAdminNote() != null ? saved.getAdminNote().trim() : "";
            String msg = "Yêu cầu rút tiền #" + reqCode + " của bạn đã được chuyển khoản thành công." + (!note.isBlank() ? " Ghi chú: " + note : "");
            notificationService.createAndPush(
                    saved.getSellerId(),
                    moderatorId,
                    NotificationType.WITHDRAWAL_APPROVED,
                    saved.getId().toString(),
                    "WITHDRAWAL",
                    msg
            );
        } catch (Exception e) {
            // Ignore notification failure
        }

        return toActionResponse(saved);
    }

    @Override
    public PaymentModeratorWithdrawalActionResponseDto approveAndMarkPaid(
            UUID withdrawalId,
            UUID moderatorId,
            String adminNote
    ) {
        if (withdrawalId == null) {
            throw new IllegalArgumentException("Withdrawal ID is required");
        }
        if (moderatorId == null) {
            throw new IllegalArgumentException("Moderator ID is required");
        }
        if (adminNote == null || adminNote.isBlank()) {
            throw new IllegalArgumentException(
                    "Payment confirmation note is required"
            );
        }

        WithdrawalRequest withdrawal = withdrawalRequestRepository
                .findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Withdrawal request not found"
                ));

        if (withdrawal.getStatus() != WithdrawalStatus.PENDING) {
            throw new WithdrawalStateConflictException(
                    "Withdrawal request has already been processed"
            );
        }

        // Single atomic step: balance move + status flip share the same
        // transaction. PESSIMISTIC_WRITE on the row plus @Transactional on
        // this method ensure rollback on any failure (no status PAID while
        // money is still locked).
        sellerBalanceService.moveLockedToWithdrawn(
                withdrawal.getSellerId(),
                withdrawal.getAmount()
        );

        LocalDateTime now = LocalDateTime.now();
        withdrawal.setStatus(WithdrawalStatus.PAID);
        withdrawal.setApprovedByAdminId(moderatorId);
        withdrawal.setApprovedAt(now);
        withdrawal.setPaidByAdminId(moderatorId);
        withdrawal.setPaidAt(now);
        withdrawal.setAdminNote(adminNote.trim());

        WithdrawalRequest saved = withdrawalRequestRepository
                .saveAndFlush(withdrawal);

        try {
            String reqCode = saved.getRequestCode() != null ? saved.getRequestCode() : "";
            String note = saved.getAdminNote() != null ? saved.getAdminNote().trim() : "";
            String msg = "Yêu cầu rút tiền #" + reqCode + " của bạn đã được duyệt và chuyển khoản thành công." + (!note.isBlank() ? " Ghi chú: " + note : "");
            notificationService.createAndPush(
                    saved.getSellerId(),
                    moderatorId,
                    NotificationType.WITHDRAWAL_APPROVED,
                    saved.getId().toString(),
                    "WITHDRAWAL",
                    msg
            );
        } catch (Exception e) {
            // Ignore notification failure
        }

        return toActionResponse(saved);
    }

    private static String normalizeOptionalNote(String adminNote) {
        if (adminNote == null || adminNote.isBlank()) {
            return null;
        }
        return adminNote.trim();
    }

    private static PaymentModeratorWithdrawalActionResponseDto toActionResponse(
            WithdrawalRequest withdrawal
    ) {
        return PaymentModeratorWithdrawalActionResponseDto.builder()
                .id(withdrawal.getId())
                .requestCode(withdrawal.getRequestCode())
                .sellerId(withdrawal.getSellerId())
                .amount(withdrawal.getAmount())
                .status(withdrawal.getStatus())
                .adminNote(withdrawal.getAdminNote())
                .approvedByAdminId(withdrawal.getApprovedByAdminId())
                .rejectedByAdminId(withdrawal.getRejectedByAdminId())
                .paidByAdminId(withdrawal.getPaidByAdminId())
                .approvedAt(withdrawal.getApprovedAt())
                .rejectedAt(withdrawal.getRejectedAt())
                .paidAt(withdrawal.getPaidAt())
                .createdAt(withdrawal.getCreatedAt())
                .updatedAt(withdrawal.getUpdatedAt())
                .build();
    }
}