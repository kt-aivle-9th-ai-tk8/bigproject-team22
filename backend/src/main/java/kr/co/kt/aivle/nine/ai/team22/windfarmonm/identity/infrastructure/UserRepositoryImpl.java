package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
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
    public boolean existsById(Long id) {
        return userJpaRepository.existsById(id);
    }

    @Override
    public Optional<User> findByEmployeeId(String employeeId) {
        return userJpaRepository.findByEmployeeId(employeeId);
    }

    @Override
    public boolean existsByEmployeeId(String employeeId) {
        return userJpaRepository.existsByEmployeeId(employeeId);
    }

    @Override
    public List<User> findAll() {
        return userJpaRepository.findAll();
    }

    @Override
    public List<Long> findUserIdsByRole(Role role) {
        return userJpaRepository.findUserIdsByRole(role);
    }

    @Override
    public void delete(User user) {
        userJpaRepository.delete(user);
    }

    @Override
    public void flush() {
        userJpaRepository.flush();
    }
}
