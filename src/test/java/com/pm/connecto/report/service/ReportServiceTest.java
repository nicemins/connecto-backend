package com.pm.connecto.report.service;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pm.connecto.common.exception.BusinessException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.report.domain.Report;
import com.pm.connecto.report.repository.ReportRepository;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService 단위 테스트")
class ReportServiceTest {

	@Mock
	private ReportRepository reportRepository;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private ReportService reportService;

	private static final Long REPORTER_ID = 1L;
	private static final Long REPORTED_ID = 2L;
	private static final Long SESSION_ID = 10L;
	private static final String REASON = "부적절한 언행";

	private User createUser(String email) {
		return new User(email, "encodedPassword");
	}

	@Nested
	@DisplayName("신고 (report)")
	class ReportTest {

		@Test
		@DisplayName("성공: 정상적인 신고를 처리한다")
		void 신고_성공() {
			// given
			User reporter = createUser("reporter@example.com");
			User reported = createUser("reported@example.com");
			given(reportRepository.existsByReporterIdAndReportedIdAndSessionId(REPORTER_ID, REPORTED_ID, SESSION_ID)).willReturn(false);
			given(userRepository.findById(REPORTER_ID)).willReturn(Optional.of(reporter));
			given(userRepository.findById(REPORTED_ID)).willReturn(Optional.of(reported));
			given(reportRepository.save(any(Report.class))).willAnswer(inv -> inv.getArgument(0));

			// when & then
			assertThatCode(() -> reportService.report(REPORTER_ID, REPORTED_ID, SESSION_ID, REASON))
				.doesNotThrowAnyException();

			verify(reportRepository).save(any(Report.class));
		}

		@Test
		@DisplayName("실패: 자기 자신을 신고하면 BusinessException(SELF_REPORT) 발생")
		void 자기_자신_신고_예외() {
			// when & then
			assertThatThrownBy(() -> reportService.report(REPORTER_ID, REPORTER_ID, SESSION_ID, REASON))
				.isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.SELF_REPORT);

			verify(reportRepository, never()).save(any());
		}

		@Test
		@DisplayName("실패: 동일 세션에 이미 신고한 경우 BusinessException(DUPLICATE_REPORT) 발생")
		void 중복_신고_예외() {
			// given
			given(reportRepository.existsByReporterIdAndReportedIdAndSessionId(REPORTER_ID, REPORTED_ID, SESSION_ID)).willReturn(true);

			// when & then
			assertThatThrownBy(() -> reportService.report(REPORTER_ID, REPORTED_ID, SESSION_ID, REASON))
				.isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_REPORT);

			verify(reportRepository, never()).save(any());
		}

		@Test
		@DisplayName("실패: 신고자가 존재하지 않으면 ResourceNotFoundException 발생")
		void 신고자_없음_예외() {
			// given
			given(reportRepository.existsByReporterIdAndReportedIdAndSessionId(REPORTER_ID, REPORTED_ID, SESSION_ID)).willReturn(false);
			given(userRepository.findById(REPORTER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> reportService.report(REPORTER_ID, REPORTED_ID, SESSION_ID, REASON))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

			verify(reportRepository, never()).save(any());
		}

		@Test
		@DisplayName("실패: 피신고자가 존재하지 않으면 ResourceNotFoundException 발생")
		void 피신고자_없음_예외() {
			// given
			User reporter = createUser("reporter@example.com");
			given(reportRepository.existsByReporterIdAndReportedIdAndSessionId(REPORTER_ID, REPORTED_ID, SESSION_ID)).willReturn(false);
			given(userRepository.findById(REPORTER_ID)).willReturn(Optional.of(reporter));
			given(userRepository.findById(REPORTED_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> reportService.report(REPORTER_ID, REPORTED_ID, SESSION_ID, REASON))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

			verify(reportRepository, never()).save(any());
		}
	}
}
