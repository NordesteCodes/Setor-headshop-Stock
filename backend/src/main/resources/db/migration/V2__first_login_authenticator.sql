-- Existing enrolled accounts keep their authenticator. NULL means not enrolled yet.
ALTER TABLE app_user ALTER COLUMN totp_cipher DROP NOT NULL;
ALTER TABLE app_user ALTER COLUMN username TYPE varchar(254);
ALTER TABLE auth_attempt ALTER COLUMN bucket TYPE varchar(300);
CREATE UNIQUE INDEX app_user_login_case_insensitive ON app_user(lower(username));
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Database-only provisioning; no public registration endpoint, no password stored in plain text.
CREATE FUNCTION provision_internal_user(email text, chosen_password text) RETURNS uuid
LANGUAGE plpgsql AS $$
DECLARE new_id uuid := gen_random_uuid();
BEGIN
    IF email IS NULL OR length(trim(email)) > 254 OR trim(email) !~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$' THEN
        RAISE EXCEPTION 'Email invalido';
    END IF;
    IF chosen_password IS NULL OR length(chosen_password) < 14 OR octet_length(chosen_password) > 72 THEN
        RAISE EXCEPTION 'Senha deve ter pelo menos 14 caracteres e no maximo 72 bytes';
    END IF;
    INSERT INTO app_user(id,username,password_hash,totp_cipher)
    VALUES(new_id,lower(trim(email)),crypt(chosen_password,gen_salt('bf',12)),NULL);
    INSERT INTO audit_entry(id,actor,action,resource_id)
    VALUES(gen_random_uuid(),new_id,'auth.provision.database',new_id);
    RETURN new_id;
END;
$$;
REVOKE ALL ON FUNCTION provision_internal_user(text,text) FROM PUBLIC;
