package kr.co.kt.aivle.nine.ai.team22.windfarmonm.infra.user;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.entity.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link UserRepository} 포트의 JPA 어댑터.
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findById(id);
    }

    @Override
    public Optional<User> findByEmployeeId(String employeeId) {
        return userJpaRepository.findByEmployeeId(employeeId);
    }

    @Override
    public boolean existsByEmployeeId(String employeeId) {
        return userJpaRepository.existsByEmployeeId(employeeId);
    }
}
