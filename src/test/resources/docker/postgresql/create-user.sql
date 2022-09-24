-- CREATE USER
CREATE ROLE user_hostssl LOGIN PASSWORD 'password';
GRANT ALL PRIVILEGES ON DATABASE postgres TO user_hostssl;

CREATE ROLE user_hostnossl LOGIN PASSWORD 'password';
GRANT ALL PRIVILEGES ON DATABASE postgres TO user_hostnossl;
