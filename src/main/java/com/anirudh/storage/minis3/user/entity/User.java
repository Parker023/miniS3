package com.anirudh.storage.minis3.user.entity;


import com.anirudh.storage.minis3.entity.BaseEntity;
import com.anirudh.storage.minis3.util.CommonUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * @author Anirudh
 * @since 20/02/26
 */

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {

    public User() {
        this.userPublicId = CommonUtils.generateRandomUUID();
    }

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private UUID userPublicId;

}
