package com.devboard.repository;

import com.devboard.entity.PasswordResetToken;
import com.devboard.entity.User;
import com.devboard.entity.enums.PasswordResetTokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    List<PasswordResetToken> findByUserAndStatus(User user, PasswordResetTokenStatus status);
}
