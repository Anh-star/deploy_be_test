package com.cmcu.itstudy.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tbl_users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", columnDefinition = "uniqueidentifier")
    private UUID id;

    @Column(name = "email", nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "full_name", length = 255, columnDefinition = "nvarchar(255)")
    private String fullName;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "bio", length = 2000, columnDefinition = "nvarchar(2000)")
    private String bio;

    @Column(name = "avatar", length = 2048)
    private String avatarUrl;

    @Column(name = "provider", length = 32)
    private String provider;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<UserRole> userRoles = new HashSet<>();

    public Set<Role> getRoles() {
        return userRoles.stream().map(UserRole::getRole).collect(Collectors.toSet());
    }

    public void setRoles(Set<Role> roles) {
        this.userRoles.clear();
        roles.forEach(role -> this.userRoles.add(UserRole.builder().user(this).role(role).build()));
    }
    @JsonIgnore
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<RefreshToken> refreshTokens = new HashSet<>();

    @JsonIgnore
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<PasswordResetToken> passwordResetTokens = new HashSet<>();
}
