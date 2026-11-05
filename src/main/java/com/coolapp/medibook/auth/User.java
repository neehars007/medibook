package com.coolapp.medibook.auth;

import com.coolapp.medibook.common.BaseEntity;
import com.coolapp.medibook.common.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {
    @Column(nullable = false)
    private String email;
    @Column(name = "password_hash")
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    private String phone;
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "totp_secret")
    private String totpSecret;
    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;
    @Column(nullable = false)
    private boolean active = true;
}
