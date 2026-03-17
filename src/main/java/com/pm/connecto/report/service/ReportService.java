package com.pm.connecto.report.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pm.connecto.common.exception.BusinessException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.match.domain.CallSession;
import com.pm.connecto.match.repository.CallSessionRepository;
import com.pm.connecto.report.domain.Report;
import com.pm.connecto.report.repository.ReportRepository;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@Service
public class ReportService {

	private final ReportRepository reportRepository;
	private final UserRepository userRepository;
	private final CallSessionRepository callSessionRepository;

	public ReportService(ReportRepository reportRepository, UserRepository userRepository,
		CallSessionRepository callSessionRepository) {
		this.reportRepository = reportRepository;
		this.userRepository = userRepository;
		this.callSessionRepository = callSessionRepository;
	}

	@Transactional
	public void report(Long reporterId, Long reportedUserId, Long sessionId, String reason) {
		if (reporterId.equals(reportedUserId)) {
			throw new BusinessException(ErrorCode.SELF_REPORT);
		}

		// 신고자가 해당 세션 참여자인지 검증
		CallSession session = callSessionRepository.findByIdAndUserId(sessionId, reporterId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

		// 피신고자가 실제 상대방인지 검증
		Long otherUserId = session.getUser1().getId().equals(reporterId)
			? session.getUser2().getId()
			: session.getUser1().getId();
		if (!reportedUserId.equals(otherUserId)) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED);
		}

		if (reportRepository.existsByReporterIdAndReportedIdAndSessionId(reporterId, reportedUserId, sessionId)) {
			throw new BusinessException(ErrorCode.DUPLICATE_REPORT);
		}

		User reporter = userRepository.findById(reporterId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
		User reported = userRepository.findById(reportedUserId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

		reportRepository.save(Report.builder()
			.reporter(reporter)
			.reported(reported)
			.sessionId(sessionId)
			.reason(reason)
			.build());
	}
}
