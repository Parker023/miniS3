package com.anirudh.storage.minis3.user.repository;

import com.anirudh.storage.minis3.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * @author Anirudh
 * @since 20/02/26
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    @Query(value = "select * from users where email=:email", nativeQuery = true)
    Optional<User> findUserByEmail(String email);

    Optional<User> findByUserPublicId(UUID userPublicId);
}
