-- Hibernate maps @Column(length = 3) to VARCHAR, PostgreSQL CHAR(3) is bpchar.
ALTER TABLE wallets
    ALTER COLUMN currency TYPE VARCHAR(3);
