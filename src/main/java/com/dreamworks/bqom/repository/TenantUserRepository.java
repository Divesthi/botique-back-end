package com.dreamworks.bqom.repository;

import com.dreamworks.bqom.repository.entity.TenantUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantUserRepository extends JpaRepository<TenantUser, Long> {

    Optional<TenantUser> findBySupabaseUid(String supabaseUid);

    Optional<TenantUser> findByEmail(String email);

    List<TenantUser> findByTenantCode(String tenantCode);
}
