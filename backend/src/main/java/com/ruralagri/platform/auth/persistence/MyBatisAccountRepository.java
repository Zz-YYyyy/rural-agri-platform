package com.ruralagri.platform.auth.persistence;

import java.util.Optional;

import com.ruralagri.platform.auth.AccountRepository;
import com.ruralagri.platform.auth.StoredAccount;

public class MyBatisAccountRepository implements AccountRepository {
    private final AccountMapper mapper;

    public MyBatisAccountRepository(AccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<StoredAccount> findByUsername(String username) {
        return Optional.ofNullable(mapper.findByUsername(username));
    }

    @Override
    public Optional<StoredAccount> findById(long id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public long insertOrdinaryAccount(String username, String passwordHash, String nickname) {
        var account = new NewAccount(username, passwordHash, nickname);
        mapper.insert(account);
        return account.getId();
    }

    @Override
    public void updateLastLogin(long id) {
        mapper.updateLastLogin(id);
    }
}
