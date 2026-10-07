CREATE TABLE service_requests (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(80)  NOT NULL,
    email      VARCHAR(254) NOT NULL,
    service    VARCHAR(40)  NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    status     VARCHAR(20)  NOT NULL DEFAULT 'new',

    CONSTRAINT chk_name_length    CHECK (char_length(trim(name)) >= 2),
    CONSTRAINT chk_email_length   CHECK (char_length(email) <= 254),
    CONSTRAINT chk_service_value  CHECK (service IN (
        'gorev-otomasyonu', 'rapor-otomasyonu', 'entegrasyon', 'diger'
    )),
    CONSTRAINT chk_message_length CHECK (char_length(trim(message)) >= 10)
);

-- Supabase public şemasında anon key ile REST API erişimini engeller.
-- Policy tanımlanmadığı için RLS etkinken hiçbir Supabase istemci sorgusu satır döndüremez;
-- sadece sunucu tarafı JDBC bağlantısı (service_role veya doğrudan bağlantı) erişebilir.
ALTER TABLE service_requests ENABLE ROW LEVEL SECURITY;
