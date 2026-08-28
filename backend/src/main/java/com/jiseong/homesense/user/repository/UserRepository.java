package com.jiseong.homesense.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jiseong.homesense.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 이메일 대소문자 구분 없이 조회한다. 저장 시 소문자로 정규화되므로
     * 조회 시에도 {@link User#normalizeEmail}로 동일하게 정규화해야 매칭된다.
     */
    default Optional<User> findByEmail(String email) {
        return findByNormalizedEmail(User.normalizeEmail(email));
    }

    default boolean existsByEmail(String email) {
        return existsByNormalizedEmail(User.normalizeEmail(email));
    }

    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByNormalizedEmail(@Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.email = :email")
    boolean existsByNormalizedEmail(@Param("email") String email);
}
