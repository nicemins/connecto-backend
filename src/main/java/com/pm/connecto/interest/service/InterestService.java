package com.pm.connecto.interest.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pm.connecto.common.exception.DuplicateResourceException;
import com.pm.connecto.common.exception.MaxLimitExceededException;
import com.pm.connecto.common.exception.ResourceNotFoundException;
import com.pm.connecto.common.response.ErrorCode;
import com.pm.connecto.interest.domain.Interest;
import com.pm.connecto.interest.repository.InterestRepository;
import com.pm.connecto.user.domain.User;
import com.pm.connecto.user.repository.UserRepository;

@Service
public class InterestService {

	private static final int MAX_INTERESTS_PER_USER = 10;

	private final InterestRepository interestRepository;
	private final UserRepository userRepository;

	public InterestService(InterestRepository interestRepository, UserRepository userRepository) {
		this.interestRepository = interestRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public Interest addInterest(Long userId, String tag) {
		User user = userRepository.findActiveById(userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

		if (interestRepository.existsByUserIdAndTag(userId, tag)) {
			throw new DuplicateResourceException(ErrorCode.DUPLICATE_INTEREST);
		}

		int currentCount = interestRepository.countByUserId(userId);
		if (currentCount >= MAX_INTERESTS_PER_USER) {
			throw new MaxLimitExceededException(ErrorCode.MAX_LIMIT_EXCEEDED,
				"최대 " + MAX_INTERESTS_PER_USER + "개까지만 등록할 수 있습니다.");
		}

		Interest interest = Interest.builder()
			.user(user)
			.tag(tag)
			.build();

		return interestRepository.save(interest);
	}

	@Transactional(readOnly = true)
	public List<Interest> getInterests(Long userId) {
		return interestRepository.findByUserId(userId);
	}

	@Transactional
	public void deleteInterest(Long userId, Long interestId) {
		Interest interest = interestRepository.findByIdAndUserId(interestId, userId)
			.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.INTEREST_NOT_FOUND));
		interestRepository.delete(interest);
	}
}
