package com.ruralagri.platform.auth.persistence;

import com.ruralagri.platform.auth.StoredAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AccountMapper {
    String COLUMNS = """
            SELECT u.id, u.username, u.password_hash AS passwordHash, u.nickname,
                   u.avatar_key AS avatarKey, u.status, u.is_admin AS admin, u.created_at AS createdAt,
                   f.id AS farmerId, f.status AS farmerStatus
            FROM sys_user u LEFT JOIN farmer_profile f ON f.user_id = u.id
            """;

    @Select(COLUMNS + " WHERE u.username = #{username}")
    StoredAccount findByUsername(@Param("username") String username);

    @Select(COLUMNS + " WHERE u.id = #{id}")
    StoredAccount findById(@Param("id") long id);

    @Insert("""
            INSERT INTO sys_user (username, password_hash, nickname, status, is_admin, created_at, updated_at)
            VALUES (#{username}, #{passwordHash}, #{nickname}, 'ACTIVE', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NewAccount account);

    @Update("UPDATE sys_user SET last_login_at = UTC_TIMESTAMP(3), updated_at = UTC_TIMESTAMP(3) WHERE id = #{id}")
    int updateLastLogin(@Param("id") long id);
}
